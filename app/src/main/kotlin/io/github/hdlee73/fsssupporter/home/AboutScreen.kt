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

const val APP_NAME = "FSS_Supporter"
const val AUTHOR = "이현덕 with Claude"
const val AUTHOR_EMAIL = "hdlee73@gmail.com"
const val AUTHOR_HOME = "https://hdlee73.github.io/Home/"

private val GUIDE = listOf(
    "처음 설정" to "설정에서 국가법령정보 OC와 OpenDART 인증키를 넣고 '연결 확인'을 누르세요. 키가 없으면 법령은 데모 데이터로 동작합니다. 인터넷이 될 때 '주요 금융법령 미리 받기'를 해 두면 연결이 불안정해도 저장본으로 볼 수 있습니다.",
    "대시보드" to "진행 중인 작업, 기한이 지난 작업, 14일 안의 일정·마감, 관심기업 새 공시, 최근 법령 검색을 한눈에 봅니다. 숫자 카드를 누르면 해당 화면으로 갑니다.",
    "작업 관리" to "검사·리포트·의견서 작업을 등록하고 상태·기한·체크리스트·관련 법령을 관리합니다. 달력 탭에서 일정을 넣으면 대시보드에 함께 보입니다.",
    "검사 진행 관리" to "검사품질관리 매뉴얼의 단계(검사준비 → 현장검사 → 검사결과 처리 → 사후관리)대로 점검 항목을 체크합니다. 착수일·현장검사 종료일을 넣으면 사전예고·검사반 편성 등 단계별 기한을 계산하고, 단계마다 관련 서식(제2-4호 검사사전예고 통지서 등)을 '작성'으로 바로 초안으로 엽니다. 매뉴얼 탭에서 매뉴얼·서식 HWP를 가져오면 원문을 단계별로 볼 수 있습니다(이 PC에만 저장).",
    "스마트 법령검색" to "'자본시장법 제54조', '지배구조법 내부통제'처럼 약칭과 조문으로 찾습니다. 법률-시행령-시행규칙-감독규정으로 이어지는 위임 관계를 나무 모양으로 보여 주고, 기준일을 넣으면 그날 시행 중이던 조문을 봅니다. '의견서에 넣기'로 조문을 초안에 바로 붙입니다.",
    "기업 개황·공시" to "회사 이름이나 종목코드로 찾아 기업개황·최대주주·재무·공시를 봅니다. ☆로 관심기업에 넣으면 새 공시 건수를 알려 줍니다.",
    "의견서 도우미" to "서식을 골라 초안을 쓰고 '인용 검증'을 누르면 글 속의 「법령」 제N조가 실제로 있는지, 삭제되지 않았는지, 정식 명칭인지 확인합니다. 위반 당시 기준일로도 검증할 수 있습니다. 한글(HWPX)·Word(DOCX)로 내보냅니다.",
    "제재 양정 계산기" to "과징금·과태료는 위반 관련 금액(또는 법정 최고한도) × 부과기준율에 가중·감경을 차례로 적용하고 법정 한도·끝수를 처리합니다. 임원·직원·기관 제재는 기본 양정에서 가중·감경 단계를 더하고 뺍니다. 기본 수치는 예시이므로 '기준 고치기'로 규정 별표에 맞추세요.",
    "뒤로가기·단축키" to "Alt+← 또는 마우스 뒤로 버튼으로 직전 화면(검색 결과·회사 등)으로 돌아갑니다. Ctrl+B 메뉴 접기, Ctrl+K 법령검색, Ctrl+1~7 메뉴 이동.",
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
                        Text("법령 조회 · 기업 공시 · 의견서 작성 · 제재 양정 · 작업 관리", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                InfoBox("내부망 PC: 인터넷 PC에서 릴리스의 설치 파일(FSS_Supporter-버전-${if (Os.isArm) "arm64" else "x64"}-Setup.exe), latest.json, latest.json.sig 세 파일을 한 폴더에 받아 옮긴 뒤 '파일로 설치'에서 latest.json을 고르세요. 공식 서명이 없는 파일은 설치하지 않습니다.")
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
