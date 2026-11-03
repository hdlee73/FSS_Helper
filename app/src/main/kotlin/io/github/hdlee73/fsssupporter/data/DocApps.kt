package io.github.hdlee73.fsssupporter.data

import kotlinx.serialization.Serializable
import java.awt.Desktop
import java.io.File
import java.util.concurrent.TimeUnit

/** 한글 문서(HWP·HWPX)를 열 프로그램 */
@Serializable
enum class HwpApp(val label: String, val desc: String) {
    AUTO("자동", "한글이 있으면 한글, 없으면 이음오피스, 둘 다 없으면 Windows 기본 앱"),
    HANCOM("한컴오피스 한글", "한글(Hwp.exe)로 엽니다"),
    IEUM("이음오피스", "이음오피스(IeumOffice)로 엽니다"),
}

/**
 * 이 PC의 한글·이음오피스를 찾아 HWP·HWPX를 연다.
 * 이음오피스는 설치 폴더를 고를 수 있어 기본 위치·제거 정보(레지스트리)·직접 지정한 경로 순서로 찾는다.
 */
object DocApps {
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    @Volatile private var ieumCache: File? = null
    @Volatile private var hancomCache: File? = null
    @Volatile private var scanned = false

    fun isHwp(f: File) = f.extension.lowercase() in setOf("hwp", "hwpx")

    /** 다시 찾기(설정 화면의 '다시 찾기') */
    fun rescan() { scanned = false; ieumCache = null; hancomCache = null }

    private fun scan() {
        if (scanned) return
        scanned = true
        if (!windows) return
        ieumCache = findIeum()
        hancomCache = findHancom()
    }

    fun ieum(custom: String = ""): File? = custom.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile } ?: run { scan(); ieumCache }
    fun hancom(): File? { scan(); return hancomCache }

    private fun env(name: String) = System.getenv(name).orEmpty()

    private fun findIeum(): File? {
        val roots = listOf(env("LOCALAPPDATA") + "\\Programs", env("ProgramFiles"), env("ProgramW6432"), env("ProgramFiles(x86)")).filter { it.isNotBlank() }
        roots.flatMap { listOf(File(it, "IeumOffice\\IeumOffice.exe"), File(it, "ieumoffice\\IeumOffice.exe")) }.firstOrNull { it.isFile }?.let { return it }
        // 제거 정보에 남은 설치 위치(DisplayIcon·InstallLocation)
        for (hive in listOf("HKCU", "HKLM")) {
            val out = reg("query", "$hive\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall", "/s", "/f", "IeumOffice") ?: continue
            for (line in out.lines()) {
                val v = Regex("""REG_(?:EXPAND_)?SZ\s+(.+)$""").find(line)?.groupValues?.get(1)?.trim()?.trim('"') ?: continue
                val path = v.substringBefore(",").trim('"')
                val f = if (path.lowercase().endsWith(".exe")) File(path) else File(path, "IeumOffice.exe")
                if (f.isFile && f.name.lowercase().startsWith("ieumoffice")) return f
            }
        }
        return null
    }

    private fun findHancom(): File? {
        for (hive in listOf("HKLM", "HKCU")) {
            val out = reg("query", "$hive\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\Hwp.exe", "/ve") ?: continue
            Regex("""REG_(?:EXPAND_)?SZ\s+(.+)$""", RegexOption.MULTILINE).find(out)?.groupValues?.get(1)?.trim()?.trim('"')
                ?.let(::File)?.takeIf { it.isFile }?.let { return it }
        }
        listOf(env("ProgramFiles(x86)"), env("ProgramFiles")).filter { it.isNotBlank() }.forEach { pf ->
            File(pf, "Hnc").listFiles()?.sortedByDescending { it.name }?.forEach { office ->
                office.listFiles()?.sortedByDescending { it.name }?.forEach { h ->
                    File(h, "Bin\\Hwp.exe").takeIf { it.isFile }?.let { return it }
                }
            }
        }
        return null
    }

    /** reg.exe는 콘솔 코드 페이지(한국어 Windows는 CP949)로 출력한다. */
    private val nativeCharset = runCatching { java.nio.charset.Charset.forName(System.getProperty("native.encoding") ?: "MS949") }.getOrDefault(Charsets.UTF_8)

    private fun reg(vararg args: String): String? = runCatching {
        val p = ProcessBuilder(listOf("reg.exe") + args).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader(nativeCharset).readText()
        if (!p.waitFor(5, TimeUnit.SECONDS)) { p.destroyForcibly(); null } else if (p.exitValue() == 0) text else null
    }.getOrNull()

    /**
     * 파일을 연다. 한글 문서면 고른 프로그램으로, 아니면 Windows 기본 앱으로.
     * 고른 프로그램이 없으면 다른 것으로 열고 그 사실을 돌려준다(알릴 것이 없으면 null).
     */
    fun open(file: File, app: HwpApp, ieumPath: String = ""): String? {
        if (!isHwp(file) || !windows) { desktop(file); return null }
        val ieum = ieum(ieumPath)
        val hancom = hancom()
        val (exe, note) = when (app) {
            HwpApp.AUTO -> (hancom ?: ieum) to null
            HwpApp.HANCOM -> (hancom ?: ieum) to if (hancom == null) (if (ieum != null) "한글을 찾지 못해 이음오피스로 열었습니다." else "한글을 찾지 못해 Windows 기본 앱으로 열었습니다.") else null
            HwpApp.IEUM -> (ieum ?: hancom) to if (ieum == null) "이음오피스를 찾지 못했습니다. 설정에서 IeumOffice.exe 위치를 지정해 주세요." else null
        }
        if (exe == null) { desktop(file); return note }
        return runCatching { ProcessBuilder(exe.absolutePath, file.absolutePath).start(); note }
            .getOrElse { desktop(file); note ?: "${exe.name}을(를) 실행하지 못해 기본 앱으로 열었습니다." }
    }

    private fun desktop(file: File) { runCatching { Desktop.getDesktop().open(file) } }
}
