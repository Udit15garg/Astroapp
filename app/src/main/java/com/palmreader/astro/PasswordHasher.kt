package com.palmreader.astro

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {

    private const val PREFIX = "pbkdf2"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256
    private const val SALT_BYTES = 16
    private const val ALGO = "PBKDF2WithHmacSHA256"

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val digest = derive(password, salt, ITERATIONS, KEY_LENGTH)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val digestB64 = Base64.encodeToString(digest, Base64.NO_WRAP)
        return "$PREFIX$$ITERATIONS$$saltB64$$digestB64"
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size != 4 || parts[0] != PREFIX) return false

        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.decode(parts[2], Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(parts[3], Base64.NO_WRAP) }.getOrNull() ?: return false
        val actual = derive(password, salt, iterations, expected.size * 8)
        return constantTimeEquals(actual, expected)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        return SecretKeyFactory.getInstance(ALGO).generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }
}
