package io.github.hdlee73.fsssupporter.tasks

import io.github.hdlee73.fsssupporter.data.AppDirs
import io.github.hdlee73.fsssupporter.data.JsonFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate
import java.util.UUID

@Serializable
enum class TaskKind(val label: String) { INSPECTION("검사"), REPORT("리포트"), DOCUMENT("의견서·조치안"), MEETING("회의·보고"), ETC("기타") }

@Serializable
enum class TaskStatus(val label: String) { PLANNED("예정"), IN_PROGRESS("진행 중"), REVIEW("검토 중"), DONE("완료"), ON_HOLD("보류") }

@Serializable
data class CheckItem(val id: String = newId(), val text: String, val done: Boolean = false)

/** 작업 한 건: 검사, 리포트 작성, 의견서 등. 날짜는 yyyy-MM-dd */
@Serializable
data class WorkTask(
    val id: String = newId(),
    val title: String,
    val kind: TaskKind = TaskKind.INSPECTION,
    val status: TaskStatus = TaskStatus.PLANNED,
    /** 대상 기관(검사 대상 회사 등) */
    val target: String = "",
    val start: String = "",
    val due: String = "",
    val memo: String = "",
    val checklist: List<CheckItem> = emptyList(),
    /** 관련 법령 검색어(예: "자본시장법 제54조") */
    val laws: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val progress: Float get() = if (checklist.isEmpty()) (if (status == TaskStatus.DONE) 1f else 0f) else checklist.count { it.done }.toFloat() / checklist.size
    val isOpen: Boolean get() = status != TaskStatus.DONE

    fun dueDate(): LocalDate? = parseDate(due)
    fun startDate(): LocalDate? = parseDate(start)
}

/** 일정 한 건(회의, 보고, 현장검사 일정 등). [taskId]로 작업과 묶을 수 있다. */
@Serializable
data class Schedule(
    val id: String = newId(),
    val title: String,
    val date: String,
    val time: String = "",
    val place: String = "",
    val taskId: String? = null,
    val memo: String = "",
) {
    fun localDate(): LocalDate? = parseDate(date)
}

@Serializable
data class TaskData(val tasks: List<WorkTask> = emptyList(), val schedules: List<Schedule> = emptyList())

fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

fun parseDate(s: String): LocalDate? = runCatching { if (s.isBlank()) null else LocalDate.parse(s.trim().replace('.', '-').replace('/', '-')) }.getOrNull()

/** 날짜 입력을 yyyy-MM-dd 로 맞춘다("20261103", "2026.11.3" 등 허용). 알아볼 수 없으면 null, 빈 값은 "" */
fun normalizeDate(text: String): String? {
    val t = text.trim()
    if (t.isEmpty()) return ""
    val digits = t.filter(Char::isDigit)
    if (digits.length == 8 && t.all { it.isDigit() }) return runCatching { LocalDate.of(digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6).toInt()).toString() }.getOrNull()
    val parts = t.split('-', '.', '/').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size == 3) return runCatching { LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()).toString() }.getOrNull()
    return null
}

class TaskStore(file: File = File(AppDirs.root, "tasks.json")) {
    private val store = JsonFile(file, TaskData.serializer()) { TaskData() }
    private val _data = MutableStateFlow(store.read())
    val data: StateFlow<TaskData> = _data.asStateFlow()

    private fun save(block: (TaskData) -> TaskData) {
        _data.update(block)
        runCatching { store.write(_data.value) }
    }

    fun upsert(task: WorkTask) = save { d ->
        val t = task.copy(updatedAt = System.currentTimeMillis())
        if (d.tasks.any { it.id == t.id }) d.copy(tasks = d.tasks.map { if (it.id == t.id) t else it }) else d.copy(tasks = d.tasks + t)
    }

    fun delete(id: String) = save { d -> d.copy(tasks = d.tasks.filter { it.id != id }, schedules = d.schedules.map { if (it.taskId == id) it.copy(taskId = null) else it }) }

    fun upsertSchedule(s: Schedule) = save { d ->
        if (d.schedules.any { it.id == s.id }) d.copy(schedules = d.schedules.map { if (it.id == s.id) s else it }) else d.copy(schedules = d.schedules + s)
    }

    fun deleteSchedule(id: String) = save { d -> d.copy(schedules = d.schedules.filter { it.id != id }) }

    /** 진행 중인 작업을 마감일 순서로. 마감일 없는 것은 뒤로 */
    fun openTasks(): List<WorkTask> = _data.value.tasks.filter { it.isOpen }.sortedWith(compareBy({ it.dueDate() ?: LocalDate.MAX }, { it.title }))

    /** [from]부터 [days]일 안의 일정과 마감 */
    fun agenda(from: LocalDate, days: Long): List<AgendaItem> {
        val to = from.plusDays(days)
        val d = _data.value
        val s = d.schedules.mapNotNull { sc -> sc.localDate()?.takeIf { !it.isBefore(from) && it.isBefore(to) }?.let { AgendaItem(it, sc.time, sc.title, AgendaKind.SCHEDULE, sc.id, sc.taskId) } }
        val t = d.tasks.filter { it.isOpen }.flatMap { task ->
            listOfNotNull(
                task.dueDate()?.takeIf { !it.isBefore(from) && it.isBefore(to) }?.let { AgendaItem(it, "", "${task.title} 마감", AgendaKind.DUE, task.id, task.id) },
                task.startDate()?.takeIf { !it.isBefore(from) && it.isBefore(to) }?.let { AgendaItem(it, "", "${task.title} 시작", AgendaKind.START, task.id, task.id) },
            )
        }
        return (s + t).sortedWith(compareBy({ it.date }, { it.time }))
    }

    /** 마감이 지난 진행 중 작업 */
    fun overdue(today: LocalDate): List<WorkTask> = openTasks().filter { it.dueDate()?.isBefore(today) == true }
}

enum class AgendaKind { SCHEDULE, DUE, START }

data class AgendaItem(val date: LocalDate, val time: String, val title: String, val kind: AgendaKind, val refId: String, val taskId: String?)
