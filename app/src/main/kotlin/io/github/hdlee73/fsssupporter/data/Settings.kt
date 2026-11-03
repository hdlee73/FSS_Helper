package io.github.hdlee73.fsssupporter.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppSettings(
    /** 국가법령정보 Open API 인증값(OC). DPAPI로 감싼 값 */
    val lawOcSealed: String = "",
    /** OpenDART 인증키. DPAPI로 감싼 값 */
    val dartKeySealed: String = "",
    /** 한글 문서(HWP·HWPX)를 열 프로그램 */
    val hwpApp: HwpApp = HwpApp.AUTO,
    /** 이음오피스를 기본 위치가 아닌 곳에 설치했을 때 IeumOffice.exe 경로 */
    val ieumPath: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** 내부망에서 쓰므로 기본은 끔(앱 정보에서 직접 확인·파일로 설치) */
    val autoUpdate: Boolean = false,
    val lastUpdateCheck: Long = 0,
    val skippedVersion: String = "",
    val recentLaw: List<String> = emptyList(),
    val favoriteLaw: List<String> = emptyList(),
    /** 처음 실행 안내를 본 버전 */
    val seenWelcome: String = "",
    /** 인증키 안내 창을 다시 보지 않기로 한 것("law", "dart") */
    val keyGuideHidden: Set<String> = emptySet(),
)

/** 설정 저장소. 화면은 [state]를 구독하고, 바꿀 때는 [update]를 부른다. */
class SettingsRepo(file: File = File(AppDirs.root, "settings.json")) {
    private val store = JsonFile(file, AppSettings.serializer()) { AppSettings() }
    private val _state = MutableStateFlow(store.read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val lawOc: String get() = Secrets.open(_state.value.lawOcSealed)
    val dartKey: String get() = Secrets.open(_state.value.dartKeySealed)

    fun update(block: (AppSettings) -> AppSettings) {
        _state.update(block)
        runCatching { store.write(_state.value) }
    }

    fun setLawOc(value: String) = update { it.copy(lawOcSealed = Secrets.seal(value.trim())) }
    fun setDartKey(value: String) = update { it.copy(dartKeySealed = Secrets.seal(value.trim())) }

    fun addRecentLaw(q: String) = update { s -> s.copy(recentLaw = (listOf(q) + s.recentLaw.filter { it != q }).take(15)) }
    fun clearRecentLaw() = update { it.copy(recentLaw = emptyList()) }
    fun toggleFavoriteLaw(q: String) = update { s ->
        s.copy(favoriteLaw = if (q in s.favoriteLaw) s.favoriteLaw - q else (s.favoriteLaw + q).take(30))
    }
}
