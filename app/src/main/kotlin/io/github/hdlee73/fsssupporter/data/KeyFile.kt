package io.github.hdlee73.fsssupporter.data

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 인증키를 다른 PC(내부망 PC 등)로 옮길 때 쓰는 암호 파일(.fsskey).
 * 사용자가 정한 비밀번호로 PBKDF2(SHA-256, 21만 회) → AES-256-GCM으로 암호화한다. 비밀번호 없이는 열 수 없다.
 */
object KeyFile {
    @Serializable
    data class Keys(val lawOc: String = "", val dartKey: String = "")

    @Serializable
    private data class Box(val v: Int = 1, val salt: String, val iv: String, val data: String, val iter: Int = ITER)

    private const val ITER = 210_000

    private fun key(password: CharArray, salt: ByteArray, iter: Int): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = f.generateSecret(PBEKeySpec(password, salt, iter, 256)).encoded
        return SecretKeySpec(raw, "AES")
    }

    fun seal(keys: Keys, password: String): String {
        require(password.length >= 6) { "비밀번호는 6자 이상으로 정해 주세요." }
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password.toCharArray(), salt, ITER), GCMParameterSpec(128, iv))
        val plain = AppJson.encodeToString(Keys.serializer(), keys).toByteArray(Charsets.UTF_8)
        val b64 = Base64.getEncoder()
        return AppJson.encodeToString(Box.serializer(), Box(salt = b64.encodeToString(salt), iv = b64.encodeToString(iv), data = b64.encodeToString(c.doFinal(plain))))
    }

    fun open(text: String, password: String): Keys {
        val box = runCatching { AppJson.decodeFromString(Box.serializer(), text) }.getOrElse { throw IllegalArgumentException("인증키 파일이 아닙니다.") }
        val d = Base64.getDecoder()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(password.toCharArray(), d.decode(box.salt), box.iter), GCMParameterSpec(128, d.decode(box.iv)))
        val plain = runCatching { c.doFinal(d.decode(box.data)) }.getOrElse { throw IllegalArgumentException("비밀번호가 맞지 않습니다.") }
        return AppJson.decodeFromString(Keys.serializer(), plain.toString(Charsets.UTF_8))
    }
}
