package io.github.hdlee73.fsssupporter.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/** 메뉴 묶음: 홈, 자료(매뉴얼·서식), 검색, 제재 기준, 할 일, 아래쪽(설정·앱 정보) */
enum class NavGroup(val title: String) { HOME(""), DOCS("검사 자료"), SEARCH("검색"), CRITERIA("제재 기준"), WORK("내 일"), LOWER("") }

/** 상단 탭의 목적지. [short]는 창이 좁을 때 탭에 쓰는 짧은 이름 */
enum class Dest(val label: String, val icon: ImageVector, val group: NavGroup, val hint: String = "", val short: String = label) {
    HOME("홈", Icons.Outlined.Home, NavGroup.HOME),
    MANUAL("검사품질관리 매뉴얼", Icons.Outlined.MenuBook, NavGroup.DOCS, "원본 모양(PDF)으로 보기", "품질관리 매뉴얼"),
    FORMS("검사관련 서식", Icons.Outlined.Description, NavGroup.DOCS, "원본 보기 · 한글/이음오피스로 작성", "서식"),
    LAW("법률 검색", Icons.Outlined.ManageSearch, NavGroup.SEARCH, "항·호·목별 위임 법규"),
    DART("기업 개황", Icons.Outlined.Business, NavGroup.SEARCH, "개황·주주·재무·공시"),
    LEVEL("양정기준", Icons.Outlined.Gavel, NavGroup.CRITERIA, "임원·직원·기관 제재 수준"),
    PENALTY("과징금 산정기준", Icons.Outlined.AccountBalance, NavGroup.CRITERIA, "기본과징금 · 가중·감경"),
    FINE("과태료 산정기준", Icons.Outlined.Payments, NavGroup.CRITERIA, "예정금액 · 가중·감경"),
    TODO("할 일", Icons.Outlined.Checklist, NavGroup.WORK, "To Do 목록"),
    SETTINGS("설정", Icons.Outlined.Settings, NavGroup.LOWER),
    ABOUT("앱 정보", Icons.Outlined.Info, NavGroup.LOWER),
    ;

    val lower: Boolean get() = group == NavGroup.LOWER

    companion object {
        /** 홈 화면·상단 탭에 보여 주는 도구 */
        val tools: List<Dest> get() = entries.filter { it.group != NavGroup.HOME && !it.lower }
    }
}

/**
 * 화면 이동과 뒤로가기. 뒤로가기(Alt+←, 마우스 뒤로 버튼)는 먼저 지금 화면 안의 단계(예: 검색 결과 → 이전 결과)를
 * 되돌리고, 더 없으면 직전 메뉴로 돌아간다. 앱을 닫지는 않는다.
 */
class Navigator {
    var current by mutableStateOf(Dest.HOME)
        private set
    private val stack = ArrayDeque<Dest>()
    private val inner = HashMap<Dest, () -> Boolean>()

    /** 지금 화면에서 뒤로 갈 곳이 있는지(화면 안 단계 또는 직전 메뉴) */
    var canGoBack by mutableStateOf(false)
        private set

    fun go(dest: Dest) {
        if (dest == current) return
        stack.addLast(current)
        if (stack.size > 50) stack.removeFirst()
        current = dest
        refresh()
    }

    fun back(): Boolean {
        if (inner[current]?.invoke() == true) {
            refresh()
            return true
        }
        val prev = stack.removeLastOrNull() ?: return false
        current = prev
        refresh()
        return true
    }

    /** 화면이 자기 안의 뒤로가기를 등록한다. handler가 true를 돌려주면 화면 안에서 처리한 것이다. */
    fun registerInnerBack(dest: Dest, handler: () -> Boolean) {
        inner[dest] = handler
    }

    fun unregisterInnerBack(dest: Dest) {
        inner.remove(dest)
    }

    fun refresh() {
        canGoBack = stack.isNotEmpty() || current != Dest.HOME
    }
}
