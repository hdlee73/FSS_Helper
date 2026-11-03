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
    "처음 설정" to "법률 검색과 양정·과징금·과태료 기준의 원문은 국가법령정보 OC, 기업 개황은 OpenDART 인증키가 필요합니다. 키 없이 그 메뉴를 처음 열면 발급 방법과 입력 칸이 함께 뜹니다(설정에서도 가능). 키는 이 PC의 Windows 계정으로 암호화해 앱 안에만 저장합니다. 내부망 PC로는 설정 > '인증키 파일로 옮기기'로 옮깁니다.",
    "홈" to "가운데 카드로 도구를 열고, 아래 목록에서 최근에 본 서식·매뉴얼 쪽·법령·기업·작성한 파일을 다시 엽니다. ☆을 누르면 즐겨찾기에 남습니다.",
    "검사매뉴얼" to "'가져오기'로 매뉴얼 한글 파일(HWP·HWPX)을 고르면 이 PC의 한글이 PDF로 바꿔 원본 모양 그대로 보여 줍니다. PDF를 바로 넣어도 됩니다. 왼쪽 목차나 찾기로 쪽을 옮기고, 오른쪽 위에서 확대·축소합니다.",
    "검사관련 서식" to "서식 한글 파일을 가져오면 서식별로 원본 쪽(PDF)을 보여 줍니다. '한글로 작성'을 누르면 원본에서 그 서식 부분만 떼어 새 한글 파일(문서\\FSS_Helper\\서식)로 엽니다. 처음 한 번 한글이 접근 허용을 물으면 '모두 허용'을 누르세요.",
    "법률 검색" to "'자본시장법 제54조'처럼 찾으면 조문을 항·호·목 단위로 보여 주고, 각 항·호·목을 위임 근거로 삼은 시행령·시행규칙·감독규정·세칙을 바로 아래에 붙여 보여 줍니다. 위임이 아니라 인용만 한 법규는 맨 아래 '인용된 법규'에 따로 모읍니다. '단계 비교'로 1~5단 비교표도 볼 수 있고, 기준일을 넣으면 그날 시행 조문을 봅니다.",
    "기업 개황" to "회사 이름이나 종목코드로 찾아 기업개황·최대주주·재무·공시를 보고, '한글 저장'·'PDF 저장'으로 한 문서에 묶어 저장합니다. ☆로 관심기업에 넣으면 새 공시 건수를 알려 줍니다.",
    "양정기준" to "임원·직원·기관을 고르고 위반 정도와 고의·과실로 기본 양정을 정한 뒤 가중·감경 단계를 더해 결과와 사전통지 표기를 봅니다. 오른쪽에는 검사·제재규정과 시행세칙의 제재 조문·별표 원문이 나옵니다.",
    "과징금·과태료 산정기준" to "법정한도와 세부평가(또는 위반결과×동기)를 고르면 기본금액, 가중·감경, 상한까지 단계별로 계산합니다. 기준 숫자는 '기준 고치기'로 현행 규정에 맞춰 바꿀 수 있고(이 PC에만 저장), 오른쪽에서 원문을 함께 봅니다.",
    "할 일" to "할 일을 적고 Enter. 기한은 '11/20', '내일'처럼 적고, ☆로 중요 표시, 체크로 끝냅니다.",
    "뒤로가기·단축키" to "Alt+← 또는 마우스 뒤로 버튼으로 직전 화면으로 돌아갑니다. Ctrl+B 메뉴 접기, Ctrl+K 법률 검색, Ctrl+1~9 메뉴 이동.",
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
                        Text("검사매뉴얼·서식 · 법률 검색 · 기업 개황 · 양정·과징금·과태료 산정기준 · 할 일", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
