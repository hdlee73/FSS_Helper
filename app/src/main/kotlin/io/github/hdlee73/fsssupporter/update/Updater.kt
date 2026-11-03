package io.github.hdlee73.fsssupporter.update

import io.github.hdlee73.fsssupporter.BuildInfo
import io.github.hdlee73.fsssupporter.data.AppDirs
import io.github.hdlee73.fsssupporter.data.AppJson
import io.github.hdlee73.fsssupporter.ui.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.util.Base64

@Serializable
data class UpdateAsset(val name: String, val sha256: String, val size: Long = 0)

/** 릴리스마다 함께 올리는 latest.json. 개인 서명키로 서명한 latest.json.sig가 짝으로 있어야 믿는다. */
@Serializable
data class UpdateManifest(
    val version: String,
    val published: String = "",
    val notes: String = "",
    /** "x64", "arm64" */
    val assets: Map<String, UpdateAsset> = emptyMap(),
)

class UpdateException(message: String) : Exception(message)

/**
 * 자동 업데이트.
 * - 인터넷 PC: GitHub 최신 릴리스의 latest.json과 서명을 받아, 내장 공개키로 서명을 확인한 뒤에만 설치 파일을 받는다.
 * - 내부망 PC: GitHub에 닿지 않으므로, 같은 세 파일(설치 파일·latest.json·latest.json.sig)을 옮겨 와 '파일로 설치'한다.
 * 서명은 저장소 주인의 GitHub Secret에만 있는 키로 만들어지므로, 다른 사람이 만든 설치 파일은 업데이트로 받아들이지 않는다.
 */
class Updater {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    val arch: String get() = if (Os.isArm) "arm64" else "x64"

    private fun get(url: String, timeout: Long = 20): HttpResponse<ByteArray> {
        val req = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(timeout))
            .header("User-Agent", "FSS_Helper/${BuildInfo.VERSION}")
            .header("Accept", "application/vnd.github+json, application/octet-stream, */*")
            .build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofByteArray())
        if (res.statusCode() == 404) throw UpdateException("아직 공개된 릴리스가 없습니다.")
        if (res.statusCode() !in 200..299) throw UpdateException("업데이트 서버 응답 오류(${res.statusCode()})")
        return res
    }

    /** 최신 릴리스를 확인한다. 새 버전이 없으면 null. */
    suspend fun check(): Pair<UpdateManifest, Map<String, String>>? = withContext(Dispatchers.IO) {
        val release = try {
            AppJson.parseToJsonElement(get("https://api.github.com/repos/${BuildInfo.REPO}/releases/latest").body().toString(Charsets.UTF_8)).jsonObject
        } catch (e: UpdateException) {
            throw e
        } catch (e: Exception) {
            throw UpdateException("업데이트 서버에 연결하지 못했습니다. 내부망이면 '파일로 설치'를 쓰세요.")
        }
        val urls = release["assets"]?.jsonArray.orEmpty().associate {
            it.jsonObject["name"]!!.jsonPrimitive.content to it.jsonObject["browser_download_url"]!!.jsonPrimitive.content
        }
        val manifestUrl = urls["latest.json"] ?: throw UpdateException("릴리스에 latest.json이 없습니다.")
        val sigUrl = urls["latest.json.sig"] ?: throw UpdateException("릴리스에 서명 파일이 없습니다.")
        val bytes = get(manifestUrl).body()
        val sig = get(sigUrl).body()
        val m = verifyManifest(bytes, sig)
        if (compareVersions(m.version, BuildInfo.VERSION) <= 0) null else m to urls
    }

    /** 설치 파일을 받아 해시를 확인한다. */
    suspend fun download(m: UpdateManifest, urls: Map<String, String>, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val asset = m.assets[arch] ?: throw UpdateException("이 PC($arch)용 설치 파일이 릴리스에 없습니다.")
        val url = urls[asset.name] ?: throw UpdateException("설치 파일 ${asset.name}을(를) 찾지 못했습니다.")
        val dir = File(AppDirs.cache, "update").apply { mkdirs() }
        val out = File(dir, asset.name)
        val req = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMinutes(10)).header("User-Agent", "FSS_Helper/${BuildInfo.VERSION}").build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofInputStream())
        if (res.statusCode() !in 200..299) throw UpdateException("설치 파일을 받지 못했습니다(${res.statusCode()}).")
        val total = res.headers().firstValueAsLong("Content-Length").orElse(asset.size).coerceAtLeast(1)
        res.body().use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        checkHash(out, asset)
        out
    }

    /**
     * 내부망용: 사용자가 고른 latest.json 옆에 있는 서명과 설치 파일을 확인한다.
     * 확인된 (매니페스트, 설치 파일)을 돌려준다.
     */
    suspend fun verifyLocal(manifestFile: File): Pair<UpdateManifest, File> = withContext(Dispatchers.IO) {
        val sigFile = File(manifestFile.parentFile, manifestFile.name + ".sig")
        if (!sigFile.exists()) throw UpdateException("같은 폴더에 ${sigFile.name} 파일이 필요합니다.")
        val m = verifyManifest(manifestFile.readBytes(), sigFile.readBytes())
        val asset = m.assets[arch] ?: throw UpdateException("이 PC($arch)용 설치 파일 정보가 없습니다.")
        val setup = File(manifestFile.parentFile, asset.name)
        if (!setup.exists()) throw UpdateException("같은 폴더에 ${asset.name} 파일이 필요합니다.")
        checkHash(setup, asset)
        m to setup
    }

    /** 확인된 설치 파일을 조용히 실행한다. 설치가 끝나면 설치 파일이 앱을 다시 켠다. 호출한 쪽은 곧바로 앱을 끝내야 한다. */
    fun launchInstaller(setup: File) {
        ProcessBuilder(setup.absolutePath, "/SILENT", "/NORESTART", "/SUPPRESSMSGBOXES", "/CLOSEAPPLICATIONS")
            .directory(setup.parentFile)
            .start()
    }

    private fun checkHash(file: File, asset: UpdateAsset) {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        if (!hex.equals(asset.sha256, ignoreCase = true)) {
            runCatching { if (file.parentFile == File(AppDirs.cache, "update")) file.delete() }
            throw UpdateException("설치 파일이 서명된 정보와 다릅니다(해시 불일치). 설치하지 않습니다.")
        }
    }

    companion object {
        fun verifyManifest(bytes: ByteArray, signature: ByteArray): UpdateManifest {
            val sig = decodeSignature(signature)
            if (!verify(bytes, sig)) throw UpdateException("서명이 맞지 않습니다. 공식 배포본이 아니어서 설치하지 않습니다.")
            return try {
                AppJson.decodeFromString(UpdateManifest.serializer(), bytes.toString(Charsets.UTF_8))
            } catch (e: Exception) {
                throw UpdateException("업데이트 정보를 읽지 못했습니다.")
            }
        }

        /** 서명 파일은 64바이트 원본이거나 base64 글자일 수 있다. */
        fun decodeSignature(raw: ByteArray): ByteArray {
            if (raw.size == 64) return raw
            return runCatching { Base64.getDecoder().decode(raw.toString(Charsets.US_ASCII).trim()) }.getOrElse { raw }
        }

        fun verify(data: ByteArray, sig: ByteArray, publicKeyB64: String = UpdateKey.PUBLIC_KEY): Boolean = runCatching {
            val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64)))
            Signature.getInstance("Ed25519").run {
                initVerify(key)
                update(data)
                verify(sig)
            }
        }.getOrDefault(false)

        /** "1.2.10" > "1.2.9". 앞의 v와 '-' 뒤 꼬리는 무시한다. */
        fun compareVersions(a: String, b: String): Int {
            fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val x = parts(a); val y = parts(b)
            for (i in 0 until maxOf(x.size, y.size)) {
                val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }
    }
}
