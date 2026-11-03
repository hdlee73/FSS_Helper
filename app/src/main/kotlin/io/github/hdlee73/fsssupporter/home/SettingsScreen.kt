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
import io.github.hdlee73.fsssupporter.data.KeyFile
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
        ScreenHeader("설정", "인증키는 이 앱 안에 저장하되, 이 PC의 Windows 계정으로 암호화합니다(다른 PC·계정에서는 열리지 않음). 저장소(깃허브)에는 들어가지 않습니다.")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).widthIn(max = 860.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("국가법령정보 OC · 법률 검색·제재 기준 원문에 필요") { Pill(if (settings.lawOc.isNotBlank()) "저장됨" else "데모 데이터", if (settings.lawOc.isNotBlank()) Tone.OK else Tone.WARN) }
                KeyGuideBody(KeyKind.LAW, settings.lawOc, onSave = { settings.setLawOc(it); onMessage("법령 OC를 저장했습니다.") }, onTest = { testLaw(law) })
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("OpenDART 인증키 · 기업 개황·공시에 필요") { Pill(if (dart.hasKey) "저장됨" else "키 없음", if (dart.hasKey) Tone.OK else Tone.WARN) }
                KeyGuideBody(KeyKind.DART, settings.dartKey, onSave = { settings.setDartKey(it); onMessage("DART 인증키를 저장했습니다. 회사 목록을 받아 옵니다."); dart.ensureIndex(force = true) }, onTest = { testDart(dart) })
            }

            KeyTransferCard(settings, onMessage)

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
                        Text("자동으로 새 버전 확인 (기본 끔)", style = MaterialTheme.typography.bodyMedium)
                        Text("내부망에서 쓰는 앱이라 기본으로 꺼 두었습니다. 새 버전은 앱 정보의 '업데이트 확인' 또는 '파일로 설치'로 직접 설치합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(s.autoUpdate, { v -> settings.update { it.copy(autoUpdate = v) } })
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("내 자료")
                Text("할 일·기준·가져온 매뉴얼·서식: ${AppDirs.root.absolutePath}\n작성한 서식·내보낸 파일(HWP·HWPX·PDF): ${AppDirs.exports.absolutePath}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Os.open(AppDirs.root) }) { Text("자료 폴더 열기") }
                    OutlinedButton(onClick = { AppDirs.exports.mkdirs(); Os.open(AppDirs.exports) }) { Text("내보낸 파일 폴더 열기") }
                }
                InfoBox("자료는 이 PC에만 저장되고 외부로 보내지 않습니다. PC를 옮길 때는 두 폴더를 통째로 복사하고, 인증키는 '인증키 파일로 옮기기'로 옮기세요.")
            }
        }
    }
}

suspend fun testLaw(law: LawModule): String =
    runCatching { law.repository.searchLaws("은행법") }.fold(
        { if (it.isNotEmpty()) "연결됐습니다: '은행법' ${it.size}건" else "응답은 왔지만 결과가 없습니다. OC와 신청한 IP를 확인하세요." },
        { "연결 실패: ${it.message} (내부망이면 www.law.go.kr 접속이 허용되는지 확인하세요)" },
    )

suspend fun testDart(dart: DartModel): String =
    runCatching { dart.client.company("00126380") }.fold({ "연결됐습니다: ${it.name}" }, { "연결 실패: ${it.message}" })

/** 인증키를 비밀번호로 잠근 파일로 내보내고, 다른 PC(내부망 PC 등)에서 불러온다. */
@Composable
private fun KeyTransferCard(settings: SettingsRepo, onMessage: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AppCard(Modifier.fillMaxWidth()) {
        CardTitle("인증키 파일로 옮기기")
        Text("인터넷 PC에서 받은 키를 내부망 PC로 옮길 때 씁니다. 정한 비밀번호로 암호화(AES-256)한 .fsskey 파일을 만들고, 다른 PC에서 같은 비밀번호로 불러옵니다. 파일과 비밀번호는 따로 전달하세요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(pw, { pw = it }, Modifier.weight(1f), singleLine = true, label = { Text("파일 비밀번호(6자 이상)") }, visualTransformation = PasswordVisualTransformation())
            Button(enabled = pw.length >= 6 && (settings.lawOc.isNotBlank() || settings.dartKey.isNotBlank()), onClick = {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "인증키 파일 저장", java.awt.FileDialog.SAVE).apply { file = "FSS_Helper_키.fsskey"; isVisible = true }
                val dir = dialog.directory; val name = dialog.file
                if (dir != null && name != null) {
                    runCatching { java.io.File(dir, if (name.endsWith(".fsskey")) name else "$name.fsskey").writeText(KeyFile.seal(KeyFile.Keys(settings.lawOc, settings.dartKey), pw)) }
                        .onSuccess { onMessage("인증키 파일을 저장했습니다.") }.onFailure { onMessage("저장하지 못했습니다: ${it.message}") }
                }
            }) { Text("파일로 내보내기") }
            OutlinedButton(enabled = pw.isNotEmpty(), onClick = {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "인증키 파일 고르기", java.awt.FileDialog.LOAD).apply { setFilenameFilter { _, n -> n.endsWith(".fsskey") }; isVisible = true }
                val dir = dialog.directory; val name = dialog.file
                if (dir != null && name != null) {
                    runCatching { KeyFile.open(java.io.File(dir, name).readText(), pw) }
                        .onSuccess { k ->
                            if (k.lawOc.isNotBlank()) settings.setLawOc(k.lawOc)
                            if (k.dartKey.isNotBlank()) settings.setDartKey(k.dartKey)
                            onMessage("인증키를 불러와 이 PC용으로 다시 암호화해 저장했습니다.")
                        }.onFailure { onMessage(it.message ?: "불러오지 못했습니다.") }
                }
            }) { Text("파일에서 불러오기") }
        }
    }
}
