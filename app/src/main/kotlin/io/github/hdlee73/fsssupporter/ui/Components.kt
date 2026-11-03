package io.github.hdlee73.fsssupporter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

/** 흰 카드. 테두리 한 줄로 구분하고 그림자는 쓰지 않는다. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

enum class Tone { OK, WARN, BAD, ACCENT, NEUTRAL }

@Composable
fun Pill(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier) {
    val s = LocalStatus.current
    val (fg, bg) = when (tone) {
        Tone.OK -> s.ok to s.okBg
        Tone.WARN -> s.warn to s.warnBg
        Tone.BAD -> s.bad to s.badBg
        Tone.ACCENT -> MaterialTheme.colorScheme.onPrimaryContainer to MaterialTheme.colorScheme.primaryContainer
        Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** 데이터가 없을 때 무엇이 나타날지와 다음 행동을 알려 주는 안내 */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/** 검색창. Enter로 [onSearch], Esc로 지운다. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    // 입력 중인 글은 화면 안에 두고 밖으로는 알리기만 한다(한글 조합이 끊기지 않게). 밖에서 값을 바꾸면 따라간다.
    var local by remember { mutableStateOf(value) }
    val sent = remember { ArrayDeque<String>() }
    // 늦게 도착한 '내가 보낸 값'은 무시하고, 밖에서 새로 정한 값(뒤로가기 등)만 받아들인다.
    LaunchedEffect(value) { if (value != local && value !in sent) local = value }
    val set: (String) -> Unit = { local = it; sent.addLast(it); if (sent.size > 64) sent.removeFirst(); onValueChange(it) }
    OutlinedTextField(
        value = local,
        onValueChange = set,
        modifier = modifier.onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { sent.clear(); onSearch(); true }
            else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape && local.isNotEmpty()) { set(""); true }
            else false
        },
        singleLine = true,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (local.isNotEmpty()) IconButton(onClick = { set("") }) { Icon(Icons.Outlined.Close, "지우기", modifier = Modifier.size(16.dp)) }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { sent.clear(); onSearch() }),
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/** 화면 맨 위 제목 줄 */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, actions: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions?.invoke()
    }
}

@Composable
fun LabelValue(label: String, value: String, labelWidth: Int = 96) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(labelWidth.dp))
        Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    val s = LocalStatus.current
    Surface(modifier.fillMaxWidth(), color = s.badBg, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = s.bad, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            action?.invoke()
        }
    }
}

@Composable
fun InfoBox(message: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Surface(modifier.fillMaxWidth(), color = color, shape = MaterialTheme.shapes.medium) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
    }
}

object Os {
    fun copy(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    fun browse(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    fun open(file: java.io.File) {
        runCatching { Desktop.getDesktop().open(file) }
    }

    val isArm: Boolean get() = System.getProperty("os.arch").orEmpty().lowercase().let { it.contains("aarch64") || it.contains("arm64") }
    val archLabel: String get() = if (isArm) "ARM64" else "x64"
}

/** yyyyMMdd → yyyy.MM.dd */
fun ymdLabel(ymd: String?): String =
    if (ymd != null && ymd.length == 8 && ymd.all(Char::isDigit)) "${ymd.substring(0, 4)}.${ymd.substring(4, 6)}.${ymd.substring(6)}" else ymd.orEmpty()

/** "2026-11-03", "2026.11.03", "20261103" → "20261103". 알아볼 수 없으면 null */
fun parseYmd(text: String): String? {
    val digits = text.filter(Char::isDigit)
    if (digits.length != 8) return null
    return runCatching { java.time.LocalDate.parse(digits, java.time.format.DateTimeFormatter.BASIC_ISO_DATE); digits }.getOrNull()
}

/** 파일 열기 창. [filter]는 소문자 파일 이름을 받는다. */
fun pickFile(title: String, filter: ((String) -> Boolean)? = null): java.io.File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    if (filter != null) dialog.setFilenameFilter { _, name -> filter(name.lowercase()) }
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return java.io.File(dir, name)
}
