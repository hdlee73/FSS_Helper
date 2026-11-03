package io.github.hdlee73.fsssupporter.data

import java.util.Base64

/**
 * API 인증키를 이 PC의 Windows 사용자 계정으로만 풀 수 있게 암호화한다(DPAPI).
 * Windows가 아니면(개발용) Base64로만 감싼다.
 */
object Secrets {
    private const val DPAPI = "dpapi:"
    private const val PLAIN = "b64:"
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    fun seal(value: String): String {
        if (value.isBlank()) return ""
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (isWindows) {
            runCatching {
                val enc = com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(bytes)
                return DPAPI + Base64.getEncoder().encodeToString(enc)
            }
        }
        return PLAIN + Base64.getEncoder().encodeToString(bytes)
    }

    fun open(sealed: String): String {
        if (sealed.isBlank()) return ""
        return runCatching {
            when {
                sealed.startsWith(DPAPI) -> {
                    val raw = Base64.getDecoder().decode(sealed.removePrefix(DPAPI))
                    String(com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(raw), Charsets.UTF_8)
                }
                sealed.startsWith(PLAIN) -> String(Base64.getDecoder().decode(sealed.removePrefix(PLAIN)), Charsets.UTF_8)
                else -> sealed
            }
        }.getOrDefault("")
    }
}
