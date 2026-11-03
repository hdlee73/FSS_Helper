package io.github.hdlee73.fsssupporter.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/** 메뉴 묶음: 주 업무, 보조 기능, 아래쪽(설정·앱 정보) */
enum class NavGroup(val title: String) { MAIN(""), AUX("보조 기능"), LOWER("") }

/** 왼쪽 메뉴의 목적지 */
enum class Dest(val label: String, val icon: ImageVector, val group: NavGroup = NavGroup.MAIN) {
    DASHBOARD("대시보드", Icons.Outlined.Dashboard),
    CASES("검사건", Icons.Outlined.FactCheck),
    SCHEDULE("일정·할 일", Icons.Outlined.CalendarMonth),
    VERIFY("문서 법규 검증", Icons.Outlined.Rule),
    GUIDE("서식·매뉴얼", Icons.Outlined.MenuBook),
    LAW("법률 도우미", Icons.Outlined.ManageSearch, NavGroup.AUX),
    DART("기업 개황·공시", Icons.Outlined.Business, NavGroup.AUX),
    SANCTION("양정 기준·계산기", Icons.Outlined.Balance, NavGroup.AUX),
    SETTINGS("설정", Icons.Outlined.Settings, NavGroup.LOWER),
    ABOUT("앱 정보", Icons.Outlined.Info, NavGroup.LOWER),
    ;

    val lower: Boolean get() = group == NavGroup.LOWER
}

/**
 * 화면 이동과 뒤로가기. 뒤로가기(Alt+←, 마우스 뒤로 버튼)는 먼저 지금 화면 안의 단계(예: 검색 결과 → 이전 결과)를
 * 되돌리고, 더 없으면 직전 메뉴로 돌아간다. 앱을 닫지는 않는다.
 */
class Navigator {
    var current by mutableStateOf(Dest.DASHBOARD)
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
        canGoBack = stack.isNotEmpty() || current != Dest.DASHBOARD
    }
}
