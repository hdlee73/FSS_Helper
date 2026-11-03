package io.github.hdlee73.fsssupporter.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 이 PC에 설치된 한글(한컴오피스)을 자동화(HWPFrame.HwpObject)로 움직여, 원본 서식 파일에서 한 서식 부분만 남긴 사본을 만든다.
 * 한글이 직접 지우고 저장하므로 글꼴·표·쪽 모양이 원본 그대로 남는다.
 * 처음 쓸 때 한글이 '파일 접근 허용' 창을 띄우면 '모두 허용'을 누르면 된다.
 */
object Hancom {
    /** 찾을 글자와 그 글자가 문서에서 몇 번째로 나오는지(1부터) */
    @Serializable
    data class Mark(val text: String, val nth: Int = 1)

    sealed interface Result {
        data class Ok(val file: File) : Result
        data class Failed(val message: String, val noHancom: Boolean = false) : Result
    }

    val available: Boolean get() = System.getProperty("os.name").orEmpty().startsWith("Windows")

    @Serializable
    private data class Job(val src: String, val out: String, val format: String, val start: String, val startNth: Int, val end: String = "", val endNth: Int = 0)

    /** [src]를 [out]으로 복사한 뒤 [start] 문단부터 [end] 문단 앞까지만 남긴다. [end]가 없으면 문서 끝까지. */
    suspend fun extract(src: File, out: File, start: Mark, end: Mark?): Result = withContext(Dispatchers.IO) {
        if (!available) return@withContext Result.Failed("Windows에서만 쓸 수 있습니다.", noHancom = true)
        out.parentFile?.mkdirs()
        val dir = AppDirs.sub("tmp")
        val script = File(dir, "hwp-extract.ps1")
        // PowerShell 5는 BOM이 있어야 UTF-8 스크립트의 한글을 바르게 읽는다.
        script.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + SCRIPT.toByteArray(Charsets.UTF_8))
        val job = File(dir, "hwp-job.json")
        val format = if (src.name.lowercase().endsWith(".hwpx")) "HWPX" else "HWP"
        job.writeText(AppJson.encodeToString(Job.serializer(), Job(src.absolutePath, out.absolutePath, format, start.text, start.nth, end?.text.orEmpty(), end?.nth ?: 0)), Charsets.UTF_8)
        val p = runCatching {
            ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath, "-job", job.absolutePath)
                .redirectErrorStream(true).start()
        }.getOrElse { return@withContext Result.Failed("PowerShell을 실행하지 못했습니다: ${it.message}") }
        val text = StringBuilder()
        val reader = Thread { runCatching { p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { text.appendLine(it) } } }.apply { isDaemon = true; start() }
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return@withContext Result.Failed("한글이 2분 안에 끝내지 못했습니다. 한글에 '파일 접근 허용' 창이 떠 있으면 허용한 뒤 다시 눌러 주세요.")
        }
        reader.join(2000)
        val log = text.toString()
        when {
            p.exitValue() == 0 && "RESULT:OK" in log && out.isFile -> Result.Ok(out)
            "RESULT:NOHWP" in log -> Result.Failed("이 PC에서 한글 자동화를 찾지 못했습니다(한컴오피스 한글 필요).", noHancom = true)
            "RESULT:NOTFOUND" in log -> Result.Failed("원본에서 '${start.text}'를 찾지 못했습니다.")
            else -> Result.Failed("한글 작업이 실패했습니다: " + log.lines().lastOrNull { it.isNotBlank() }.orEmpty().take(200))
        }
    }

    private val SCRIPT = """
param([string]${'$'}job)
${'$'}ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
${'$'}p = Get-Content -LiteralPath ${'$'}job -Raw -Encoding UTF8 | ConvertFrom-Json
try { ${'$'}hwp = New-Object -ComObject HWPFrame.HwpObject } catch { Write-Output 'RESULT:NOHWP'; exit 3 }
function Find-Nth([string]${'$'}text, [int]${'$'}n) {
  [void]${'$'}hwp.Run('MoveDocBegin')
  for (${'$'}k = 0; ${'$'}k -lt ${'$'}n; ${'$'}k++) {
    ${'$'}set = ${'$'}hwp.HParameterSet.HFindReplace
    [void]${'$'}hwp.HAction.GetDefault('RepeatFind', ${'$'}set.HSet)
    ${'$'}set.FindString = ${'$'}text
    ${'$'}set.IgnoreMessage = 1
    ${'$'}set.Direction = ${'$'}hwp.FindDir('Forward')
    ${'$'}set.FindType = 1
    ${'$'}set.FindRegExp = 0
    ${'$'}set.WholeWordOnly = 0
    if (-not ${'$'}hwp.HAction.Execute('RepeatFind', ${'$'}set.HSet)) { return ${'$'}null }
  }
  [void]${'$'}hwp.Run('Cancel')
  ${'$'}g = 0
  while (${'$'}hwp.GetPosBySet().Item('List') -ne 0 -and ${'$'}g -lt 8) { [void]${'$'}hwp.Run('MoveParentList'); ${'$'}g++ }
  [void]${'$'}hwp.Run('MoveParaBegin')
  return ${'$'}hwp.GetPosBySet()
}
try {
  try { [void]${'$'}hwp.RegisterModule('FilePathCheckDLL', 'FilePathCheckerModule') } catch {}
  Copy-Item -LiteralPath ${'$'}p.src -Destination ${'$'}p.out -Force
  Set-ItemProperty -LiteralPath ${'$'}p.out -Name IsReadOnly -Value ${'$'}false
  if (-not ${'$'}hwp.Open(${'$'}p.out, ${'$'}p.format, 'forceopen:true;versionwarning:false')) { Write-Output 'RESULT:OPENFAIL'; exit 5 }
  ${'$'}s = Find-Nth ${'$'}p.start ${'$'}p.startNth
  if (${'$'}null -eq ${'$'}s) { Write-Output 'RESULT:NOTFOUND'; exit 4 }
  if (${'$'}p.end -ne '') {
    ${'$'}e = Find-Nth ${'$'}p.end ${'$'}p.endNth
    if (${'$'}null -ne ${'$'}e) {
      [void]${'$'}hwp.SetPosBySet(${'$'}e)
      [void]${'$'}hwp.Run('MoveSelDocEnd')
      [void]${'$'}hwp.Run('Delete')
    }
  }
  ${'$'}s = Find-Nth ${'$'}p.start ${'$'}p.startNth
  if (${'$'}null -ne ${'$'}s) {
    [void]${'$'}hwp.SetPosBySet(${'$'}s)
    [void]${'$'}hwp.Run('MoveSelDocBegin')
    [void]${'$'}hwp.Run('Delete')
  }
  [void]${'$'}hwp.SaveAs(${'$'}p.out, ${'$'}p.format, '')
  Write-Output 'RESULT:OK'
} catch {
  Write-Output ('RESULT:ERROR ' + ${'$'}_.Exception.Message)
  exit 6
} finally {
  try { [void]${'$'}hwp.Clear(1) } catch {}
  try { [void]${'$'}hwp.Quit() } catch {}
}
""".trimStart()
}
