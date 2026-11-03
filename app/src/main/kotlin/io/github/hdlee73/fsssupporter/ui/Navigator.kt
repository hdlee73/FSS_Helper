package io.github.hdlee73.fsssupporter.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/** 왼쪽 메뉴의 목적지. [lower]는 메뉴 아래쪽 묶음(설정·앱 정보). */
enum class Dest(val label: String, val icon: ImageVector, val lower: Boolean = false) {
    DASHBOARD("대시보드", Icons.Outlined.Dashboard),
    TASKS("작업 관리", Icons.Outlined.Assignment),
    LAW("스마트 법령검색", Icons.Outlined.ManageSearch),
    DART("기업 개황·공시", Icons.Outlined.Business),
    OPINION("의견서 도우미", Icons.Outlined.EditNote),
    SANCTION("제재 양정 계산기", Icons.Outlined.Balance),
    SETTINGS("설정", Icons.Outlined.Settings, lower = true),
    ABOUT("앱 정보", Icons.Outlined.Info, lower = true),
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
