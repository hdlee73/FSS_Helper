package io.github.hdlee73.fsssupporter.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsssupporter.BuildInfo
import io.github.hdlee73.fsssupporter.update.UpdateModel
import io.github.hdlee73.fsssupporter.update.UpdateState
import io.github.hdlee73.fsssupporter.ui.AppCard
import io.github.hdlee73.fsssupporter.ui.CardTitle
import io.github.hdlee73.fsssupporter.ui.InfoBox
import io.github.hdlee73.fsssupporter.ui.LabelValue
import io.github.hdlee73.fsssupporter.ui.Os
import io.github.hdlee73.fsssupporter.ui.Pill
import io.github.hdlee73.fsssupporter.ui.ScreenHeader
import io.github.hdlee73.fsssupporter.ui.Tone
import java.io.File

const val APP_NAME = "FSS_Helper"
const val AUTHOR = "이현덕 with Claude"
const val AUTHOR_EMAIL = "hdlee73@gmail.com"
const val AUTHOR_HOME = "https://hdlee73.github.io/Home/"

private val GUIDE = listOf(
    "처음 설정" to "법률 도우미·문서 법규 검증은 국가법령정보 OC, 기업 개황·공시는 OpenDART 인증키가 필요합니다. 키 없이 그 메뉴를 처음 열면 발급 방법과 입력 칸이 함께 뜹니다(설정에서도 가능). 키는 이 PC의 Windows 계정으로 암호화해 앱 안에만 저장합니다. 내부망 PC로는 설정 > '인증키 파일로 옮기기'로 옮깁니다.",
    "서식·매뉴얼 가져오기" to "서식·매뉴얼 > 가져오기에서 검사품질관리 매뉴얼과 검사관련 서식 한글 파일을 한 번 고르면, 원본 사본을 이 PC에 두고 절·서식별로 나눕니다(외부로 보내지 않음).",
    "검사건" to "검사건마다 ① 진행 단계(검사준비 → 현장검사 → 결과 처리 → 사후관리, 착수일·종료일 기준 기한 계산) ② 지적사항·제재 ③ 문서·서식 ④ 메모·할 일을 관리합니다. 내가 하지 않는 단계는 '생략' 또는 '다른 팀 담당'으로 넘기면 진행률에서 빠집니다. 검사건은 오른쪽 위 휴지통으로 지웁니다.",
    "지적사항·제재" to "지적사항마다 위반 법규·사실관계·조치 구분을 적고, 기관 제재와 기관 과징금·과태료를 따로 넣습니다. 관련 임직원(행위자·보조자·지시·감독자)을 사람마다 넣으면 임원·직원 기준으로 양정(가중·감경, 사전통지 표기)과 개인 과태료를 계산하고, 조치 명세를 한글·엑셀로 저장합니다.",
    "서식 작성" to "단계의 관련 서식에서 '작성'을 누르면 이 PC의 한글이 원본 서식 부분만 잘라 새 파일로 저장해 엽니다(표·글꼴·쪽 설정이 원본 그대로). 파일은 문서\\FSS_Helper\\검사건 폴더에 검사건별로 모입니다. 처음 한 번 한글이 접근 허용을 물으면 '모두 허용'을 누르세요.",
    "문서 법규 검증" to "이미 작성한 의견서·조치안·검사서(HWP·HWPX)를 불러와 기준일(위반행위일 등)을 넣으면, 인용한 「법령」 제N조가 그날 시행 중이던 조문과 맞는지, 삭제·없는 조문은 아닌지, 그 뒤 개정됐는지 알려 줍니다.",
    "법률 도우미 (보조)" to "'자본시장법 제54조'처럼 찾으면 법률-시행령-시행규칙-감독규정-세칙을 '펼쳐 보기'(아래로 펼치고 접기) 또는 '단계 비교'(위임이 있는 단계만 1~5단 비교)로 봅니다. 기준일을 넣으면 그날 시행 조문을 봅니다.",
    "기업 개황·공시 (보조)" to "회사 이름이나 종목코드로 찾아 기업개황·최대주주·재무·공시를 보고, '한글 저장'·'PDF 저장'으로 한 문서에 묶어 저장합니다. ☆로 관심기업에 넣으면 새 공시 건수를 알려 줍니다.",
    "양정 기준·계산기 (보조)" to "검사건 양정에 쓰는 과징금·과태료·임원·직원·기관 기준을 근거 규정 별표에 맞춰 고치고, 한 건을 따로 계산해 봅니다. 기본 수치는 예시입니다.",
    "뒤로가기·단축키" to "Alt+← 또는 마우스 뒤로 버튼으로 직전 화면으로 돌아갑니다. Ctrl+B 메뉴 접기, Ctrl+K 법률 도우미, Ctrl+1~8 메뉴 이동.",
)

@Composable
fun AboutScreen(update: UpdateModel, onInstallAndExit: () -> Unit, onMessage: (String) -> Unit) {
    val state by update.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("앱 정보")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).widthIn(max = 860.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Image(painterResource("icons/app.png"), null, Modifier.size(64.dp))
                    Column {
                        Text(APP_NAME, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("버전 ${BuildInfo.VERSION} · Windows ${Os.archLabel}", style = MaterialTheme.typography.bodyMedium)
                        Text("검사건별 진행 관리 · 지적사항·행위자별 제재 양정 · 원본 서식 작성 · 문서 법규 검증", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LabelValue("만든이", AUTHOR)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("이메일", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 54.dp))
                    TextButton(onClick = { Os.browse("mailto:$AUTHOR_EMAIL") }) { Text(AUTHOR_EMAIL) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("홈페이지", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 42.dp))
                    TextButton(onClick = { Os.browse(AUTHOR_HOME) }) { Text(AUTHOR_HOME) }
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("업데이트") { Pill("서명 확인", Tone.OK) }
                Text("내부망에서 쓰는 앱이라 자동 업데이트는 꺼져 있습니다. 인터넷 PC에서는 '업데이트 확인'으로, 내부망 PC에서는 받은 파일로 직접 설치합니다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (val s = state) {
                    UpdateState.Idle -> Text("현재 버전 ${BuildInfo.VERSION}", style = MaterialTheme.typography.bodyMedium)
                    UpdateState.Checking -> { Text("확인하는 중…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    is UpdateState.UpToDate -> Text("최신 버전입니다 (${BuildInfo.VERSION}).", style = MaterialTheme.typography.bodyMedium)
                    is UpdateState.Available -> {
                        Text("새 버전 ${s.manifest.version}이 있습니다.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (s.manifest.notes.isNotBlank()) Text(s.manifest.notes, style = MaterialTheme.typography.bodySmall)
                    }
                    is UpdateState.Downloading -> { Text("${s.manifest.version} 내려받는 중… ${(s.progress * 100).toInt()}%"); LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth()) }
                    is UpdateState.Ready -> {
                        Text("${s.manifest.version} 설치 준비가 됐습니다. 서명과 파일 해시를 확인했습니다.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (s.manifest.notes.isNotBlank()) Text(s.manifest.notes, style = MaterialTheme.typography.bodySmall)
                    }
                    is UpdateState.Failed -> Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (state) {
                        is UpdateState.Available -> {
                            Button(onClick = { update.download() }) { Text("내려받기") }
                            TextButton(onClick = { update.skip() }) { Text("이 버전 건너뛰기") }
                        }
                        is UpdateState.Ready -> {
                            Button(onClick = { if (update.install()) onInstallAndExit() }) { Text("지금 설치하고 다시 시작") }
                            TextButton(onClick = { update.skip() }) { Text("나중에") }
                        }
                        else -> OutlinedButton(onClick = { update.check() }, enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading) { Text("업데이트 확인") }
                    }
                    OutlinedButton(onClick = {
                        pickManifest()?.let { update.fromFile(it) } ?: onMessage("파일을 고르지 않았습니다.")
                    }) { Text("파일로 설치 (내부망)") }
                }
                InfoBox("내부망 PC: 인터넷 PC에서 릴리스의 설치 파일(FSS_Helper-버전-${if (Os.isArm) "arm64" else "x64"}-Setup.exe), latest.json, latest.json.sig 세 파일을 한 폴더에 받아 옮긴 뒤 '파일로 설치'에서 latest.json을 고르세요. 공식 서명이 없는 파일은 설치하지 않습니다.")
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("기본 사용 방법")
                GUIDE.forEach { (title, body) ->
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    Text(body, style = MaterialTheme.typography.bodySmall)
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                CardTitle("자료 출처와 유의 사항")
                Text("법령 정보: 국가법령정보센터 Open API(법제처) · 공시 정보: OpenDART(금융감독원). 이 앱은 업무 보조 도구이며, 법령 해석과 제재 양정의 최종 판단은 원문과 내부 기준에 따릅니다. 입력한 자료는 이 PC에만 저장됩니다.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun pickManifest(): File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "latest.json 고르기", java.awt.FileDialog.LOAD)
    dialog.file = "latest.json"
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}
