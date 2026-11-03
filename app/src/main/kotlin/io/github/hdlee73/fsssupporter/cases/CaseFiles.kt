package io.github.hdlee73.fsssupporter.cases

import io.github.hdlee73.fsssupporter.data.AppDirs
import io.github.hdlee73.fsssupporter.tasks.WorkTask
import java.io.File

/** 검사건마다 문서를 모아 두는 폴더: 문서\FSS_Helper\검사건\<검사건 이름>_<id> */
object CaseFiles {
    fun safe(s: String) = s.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(60).ifBlank { "검사건" }

    val root: File get() = File(AppDirs.exports, "검사건")

    /** 이름이 바뀌어도 같은 폴더를 찾도록 id로 끝나는 폴더를 먼저 찾는다. */
    fun dir(task: WorkTask, create: Boolean = true): File {
        val existing = root.listFiles { f -> f.isDirectory && f.name.endsWith("_" + task.id) }?.firstOrNull()
        val d = existing ?: File(root, safe(task.title) + "_" + task.id)
        if (create) d.mkdirs()
        return d
    }

    fun list(task: WorkTask): List<File> =
        dir(task, create = false).takeIf { it.isDirectory }?.listFiles { f -> f.isFile && !f.name.startsWith("~$") && !f.name.startsWith(".") }
            ?.sortedByDescending { it.lastModified() }.orEmpty()

    /** 같은 이름이 있으면 " (2)"를 붙인 새 이름 */
    fun unique(dir: File, name: String): File {
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var f = File(dir, name)
        var n = 2
        while (f.exists()) { f = File(dir, "$base ($n)" + if (ext.isNotEmpty()) ".$ext" else ""); n++ }
        return f
    }
}
