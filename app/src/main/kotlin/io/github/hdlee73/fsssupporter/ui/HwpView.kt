package io.github.hdlee73.fsssupporter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsssupporter.data.HBlock
import io.github.hdlee73.fsssupporter.data.HCell
import io.github.hdlee73.fsssupporter.data.HPara
import io.github.hdlee73.fsssupporter.data.HTable

/**
 * 한글 문서에서 읽은 문단·표를 화면에 그린다. 표는 행·열과 합친 칸(가로·세로)을 살려 칸 너비 비율대로 그린다.
 * 글꼴 크기·줄 간격 같은 세부 서식은 원본과 다를 수 있다(원본 그대로는 '한글로 열기').
 */
@Composable
fun HwpBlocks(blocks: List<HBlock>, modifier: Modifier = Modifier) {
    Column(modifier) {
        blocks.forEach { b ->
            when (b) {
                is HPara -> Text(b.text.ifEmpty { " " }, style = MaterialTheme.typography.bodySmall)
                is HTable -> HwpTable(b)
            }
        }
    }
}

@Composable
private fun HwpTable(t: HTable) {
    val line = MaterialTheme.colorScheme.outline
    // 한 칸짜리 표(테두리 상자)는 상자로만 그린다.
    if (t.cells.size == 1) {
        Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).drawBehind { box(line, top = true, bottom = true) }.padding(6.dp)) {
            HwpBlocks(t.cells[0].blocks)
        }
        return
    }
    val rows = (t.cells.maxOfOrNull { it.row + it.rowSpan } ?: 0).coerceAtLeast(t.rows)
    // 행마다: 그 행에서 시작하는 칸 + 위에서 내려온(세로로 합친) 칸의 자리
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        for (r in 0 until rows) {
            val starting = t.cells.filter { it.row == r }
            val spanning = t.cells.filter { it.row < r && r < it.row + it.rowSpan }
            if (starting.isEmpty() && spanning.isEmpty()) continue
            val slots = (starting.map { it to true } + spanning.map { it to false }).sortedBy { it.first.col }
            val total = slots.sumOf { weightOf(it.first) }.coerceAtLeast(1.0)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                slots.forEach { (c, first) ->
                    val last = r == c.row + c.rowSpan - 1
                    Box(
                        Modifier.weight((weightOf(c) / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight()
                            .drawBehind { box(line, top = first, bottom = last) }
                            .background(Color.Transparent)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        if (first) HwpBlocks(c.blocks)
                    }
                }
            }
        }
    }
}

private fun weightOf(c: HCell): Double = if (c.width > 0) c.width.toDouble() else c.colSpan * 1000.0

private fun androidx.compose.ui.graphics.drawscope.DrawScope.box(color: Color, top: Boolean, bottom: Boolean) {
    val w = 1f
    drawLine(color, Offset(0f, 0f), Offset(0f, size.height), w)
    drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), w)
    if (top) drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), w)
    if (bottom) drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), w)
}
