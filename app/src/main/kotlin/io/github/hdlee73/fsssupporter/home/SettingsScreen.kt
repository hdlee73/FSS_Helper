package io.github.hdlee73.fsssupporter.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsssupporter.dart.DartModel
import io.github.hdlee73.fsssupporter.data.AppDirs
import io.github.hdlee73.fsssupporter.data.SettingsRepo
import io.github.hdlee73.fsssupporter.data.ThemeMode
import io.github.hdlee73.fsssupporter.law.LawModule
import io.github.hdlee73.fsssupporter.ui.AppCard
import io.github.hdlee73.fsssupporter.ui.CardTitle
import io.github.hdlee73.fsssupporter.ui.InfoBox
import io.github.hdlee73.fsssupporter.ui.Os
import io.github.hdlee73.fsssupporter.ui.Pill
import io.github.hdlee73.fsssupporter.ui.ScreenHeader
import io.github.hdlee73.fsssupporter.ui.Tone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 미리 받기 진행 상태(화면을 떠나도 유지) */
class PrefetchUi {
    var running by mutableStateOf(false)
    var done by mutableStateOf(0)
    var total by mutableStateOf(0)
    var current by mutableStateOf("")
    var result by mutableStateOf<String?>(null)
}

@Composable
fun SettingsScreen(settings: SettingsRepo, law: LawModule, dart: DartModel, prefetch: PrefetchUi, appScope: kotlinx.coroutines.CoroutineScope, onMessage: (String) -> Unit) {
    val s by settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("설정", "인증키는 이 PC의 Windows 계정으로 암호화해 저장합니다(다른 PC·계정에서는 열리지 않음).")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).widthIn(max = 860.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("국가법령정보 Open API") { Pill(if (settings.lawOc.isNotBlank()) "연결됨" else "데모 데이터", if (settings.lawOc.isNotBlank()) Tone.OK else Tone.WARN) }
                Text("open.law.go.kr에서 신청한 OC(보통 이메일 아이디)를 넣습니다. 내부망에서도 www.law.go.kr로 연결됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KeyField("OC", settings.lawOc, onSave = { settings.setLawOc(it); onMessage("법령 OC를 저장했습니다.") }) {
                    scope.launch {
                        val r = runCatching { law.repository.searchLaws("은행법") }
                        onMessage(r.fold({ if (it.isNotEmpty()) "법령 연결 확인: '은행법' ${it.size}건" else "응답은 왔지만 결과가 없습니다. OC를 확인하세요." }, { "연결 실패: ${it.message}" }))
                    }
                }
                TextButton(onClick = { Os.browse("https://open.law.go.kr/LSO/openApi/guideList.do") }) { Text("OC 신청 안내 열기") }
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("OpenDART") { Pill(if (dart.hasKey) "연결됨" else "키 없음", if (dart.hasKey) Tone.OK else Tone.WARN) }
                Text("opendart.fss.or.kr에서 발급받은 인증키(40자)를 넣습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KeyField("인증키", settings.dartKey, onSave = { settings.setDartKey(it); onMessage("DART 인증키를 저장했습니다. 회사 목록을 받아 옵니다."); dart.ensureIndex(force = true) }) {
                    dart.ensureIndex(force = true); onMessage("회사 목록을 다시 받는 중입니다. 기업 개황·공시 화면에서 결과를 확인하세요.")
                }
                TextButton(onClick = { Os.browse("https://opendart.fss.or.kr/uss/umt/EgovMberInsertView.do") }) { Text("인증키 신청 열기") }
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("법령 저장본 (오프라인 대비)")
                val (files, bytes) = remember(prefetch.running, prefetch.result) { law.cache.stats() }
                Text("한 번 본 법령 본문은 PC에 저장해 두고, 연결이 끊기면 저장본으로 보여 줍니다. 지금 ${files}건 · ${"%.1f".format(bytes / 1024.0 / 1024.0)}MB",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (prefetch.running) {
                    LinearProgressIndicator(progress = { if (prefetch.total == 0) 0f else prefetch.done.toFloat() / prefetch.total }, modifier = Modifier.fillMaxWidth())
                    Text("${prefetch.done}/${prefetch.total} ${prefetch.current}", style = MaterialTheme.typography.bodySmall)
                }
                prefetch.result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !prefetch.running && settings.lawOc.isNotBlank(), onClick = {
                        prefetch.running = true; prefetch.result = null
                        appScope.launch {
                            try {
                                val failed = law.prefetchMajor { d, t, n -> prefetch.done = d; prefetch.total = t; prefetch.current = n }
                                prefetch.result = if (failed.isEmpty()) "주요 금융법령을 모두 받았습니다." else "받지 못한 법령 ${failed.size}건: ${failed.take(5).joinToString(", ")}"
                            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                                prefetch.result = "중단됨: ${e.message}"
                            } finally { prefetch.running = false }
                        }
                    }) { Text("주요 금융법령 미리 받기") }
                    OutlinedButton(enabled = !prefetch.running, onClick = { law.cache.clear(); prefetch.result = "저장본을 비웠습니다." }) { Text("저장본 비우기") }
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("화면")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ThemeMode.SYSTEM to "시스템 따라가기", ThemeMode.LIGHT to "밝게", ThemeMode.DARK to "어둡게").forEach { (m, label) ->
                        FilterChip(s.theme == m, { settings.update { it.copy(theme = m) } }, label = { Text(label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("왼쪽 메뉴 접기 (Ctrl+B)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(s.sidebarCollapsed, { v -> settings.update { it.copy(sidebarCollapsed = v) } })
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("업데이트")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("자동으로 새 버전 확인", style = MaterialTheme.typography.bodyMedium)
                        Text("인터넷이 되는 PC에서 하루 한 번 확인합니다. 내부망 PC는 앱 정보의 '파일로 설치'를 쓰세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(s.autoUpdate, { v -> settings.update { it.copy(autoUpdate = v) } })
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("내 자료")
                Text("작업·일정·초안·양정 기준: ${AppDirs.root.absolutePath}\n내보낸 파일(HWPX·DOCX·XLSX): ${AppDirs.exports.absolutePath}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Os.open(AppDirs.root) }) { Text("자료 폴더 열기") }
                    OutlinedButton(onClick = { AppDirs.exports.mkdirs(); Os.open(AppDirs.exports) }) { Text("내보낸 파일 폴더 열기") }
                }
                InfoBox("자료는 이 PC에만 저장되고 외부로 보내지 않습니다. PC를 옮길 때는 자료 폴더를 통째로 복사하세요(인증키는 새 PC에서 다시 입력).")
            }
        }
    }
}

@Composable
private fun KeyField(label: String, current: String, onSave: (String) -> Unit, onTest: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    var show by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text, onValueChange = { text = it.trim() }, label = { Text(label) }, singleLine = true,
            modifier = Modifier.weight(1f),
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "가리기" else "보기") } },
        )
        Button(onClick = { onSave(text) }, enabled = text != current) { Text("저장") }
        OutlinedButton(onClick = onTest, enabled = current.isNotBlank()) { Text("연결 확인") }
    }
}
