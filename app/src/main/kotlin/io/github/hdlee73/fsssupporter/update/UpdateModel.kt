package io.github.hdlee73.fsssupporter.update

import io.github.hdlee73.fsssupporter.data.SettingsRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val at: Long) : UpdateState
    data class Available(val manifest: UpdateManifest, val urls: Map<String, String>) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val progress: Float) : UpdateState
    /** 확인을 마친 설치 파일. 설치하면 앱이 닫힌다. */
    data class Ready(val manifest: UpdateManifest, val setup: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class UpdateModel(private val settings: SettingsRepo, private val scope: CoroutineScope, private val updater: Updater = Updater()) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** 앱을 켤 때: 자동 업데이트가 켜져 있고 하루가 지났으면 조용히 확인한다(실패해도 알리지 않음). */
    fun autoCheck() {
        val s = settings.state.value
        if (!s.autoUpdate || System.currentTimeMillis() - s.lastUpdateCheck < 20 * 3600 * 1000L) return
        check(quiet = true)
    }

    fun check(quiet: Boolean = false) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        scope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                val found = updater.check()
                settings.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
                when {
                    found == null -> UpdateState.UpToDate(System.currentTimeMillis())
                    quiet && found.first.version == settings.state.value.skippedVersion -> UpdateState.Idle
                    else -> UpdateState.Available(found.first, found.second)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (quiet) UpdateState.Idle else UpdateState.Failed(e.message ?: "업데이트를 확인하지 못했습니다.")
            }
        }
    }

    fun download() {
        val s = _state.value as? UpdateState.Available ?: return
        scope.launch {
            _state.value = UpdateState.Downloading(s.manifest, 0f)
            _state.value = try {
                val file = updater.download(s.manifest, s.urls) { p -> _state.value = UpdateState.Downloading(s.manifest, p) }
                UpdateState.Ready(s.manifest, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: "내려받지 못했습니다.")
            }
        }
    }

    /** 내부망: 옮겨 온 latest.json을 골라 확인한다. */
    fun fromFile(manifest: File) {
        scope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                val (m, setup) = updater.verifyLocal(manifest)
                if (Updater.compareVersions(m.version, io.github.hdlee73.fsssupporter.BuildInfo.VERSION) <= 0) {
                    UpdateState.Failed("이미 ${io.github.hdlee73.fsssupporter.BuildInfo.VERSION} 이상을 쓰고 있습니다(파일 버전 ${m.version}).")
                } else UpdateState.Ready(m, setup)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: "파일을 확인하지 못했습니다.")
            }
        }
    }

    fun skip() {
        val v = when (val s = _state.value) {
            is UpdateState.Available -> s.manifest.version
            is UpdateState.Ready -> s.manifest.version
            else -> return
        }
        settings.update { it.copy(skippedVersion = v) }
        _state.value = UpdateState.Idle
    }

    /** 설치를 시작한다. true면 앱을 바로 끝내야 한다. */
    fun install(): Boolean {
        val s = _state.value as? UpdateState.Ready ?: return false
        return runCatching { updater.launchInstaller(s.setup) }
            .onFailure { _state.value = UpdateState.Failed("설치 파일을 실행하지 못했습니다: ${it.message}") }
            .isSuccess
    }
}
