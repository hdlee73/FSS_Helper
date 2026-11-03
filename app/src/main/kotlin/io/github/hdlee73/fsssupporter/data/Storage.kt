package io.github.hdlee73.fsssupporter.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 앱 데이터 폴더. Windows는 %APPDATA%\FSS_Helper, 그 밖에는 ~/.fss_helper. 예전 이름(FSS_Supporter) 폴더가 있으면 옮겨 쓴다. */
object AppDirs {
    val root: File by lazy {
        val appData = System.getenv("APPDATA")
        val (dir, old) = if (!appData.isNullOrBlank()) File(appData, "FSS_Helper") to File(appData, "FSS_Supporter")
        else File(System.getProperty("user.home"), ".fss_helper") to File(System.getProperty("user.home"), ".fss_supporter")
        if (!dir.exists() && old.isDirectory) runCatching { if (!old.renameTo(dir)) old.copyRecursively(dir) }
        dir.apply { mkdirs() }
    }

    fun sub(name: String): File = File(root, name).apply { mkdirs() }
    val cache: File get() = sub("cache")
    val exports: File by lazy {
        val docs = File(System.getProperty("user.home"), "Documents")
        val base = if (docs.isDirectory) docs else File(System.getProperty("user.home"))
        val dir = File(base, "FSS_Helper")
        val old = File(base, "FSS_Supporter")
        if (!dir.exists() && old.isDirectory) runCatching { old.renameTo(dir) }
        dir.apply { mkdirs() }
    }
}

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
    explicitNulls = false
}

/** JSON 파일 하나에 값을 저장한다. 쓰기는 임시 파일에 쓴 뒤 바꿔 끼워서 중간에 꺼져도 파일이 깨지지 않는다. */
class JsonFile<T>(private val file: File, private val serializer: KSerializer<T>, private val default: () -> T) {
    fun read(): T = runCatching {
        if (!file.exists()) default() else AppJson.decodeFromString(serializer, file.readText(Charsets.UTF_8))
    }.getOrElse {
        // 깨진 파일은 옆에 남겨 두고 기본값으로 시작한다.
        runCatching { file.copyTo(File(file.parentFile, file.name + ".broken"), overwrite = true) }
        default()
    }

    @Synchronized
    fun write(value: T) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(AppJson.encodeToString(serializer, value), Charsets.UTF_8)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
