package com.example.util

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated AES-256-GCM backup encryption using PBKDF2WithHmacSHA256 key derivation.
 * Envelope format is portable standard JSON so encrypted files are safe to export via SAF.
 */
object BackupCrypto {

    const val ENCRYPTED_FORMAT: String = "martinu-encrypted-backup"
    const val ENCRYPTED_SCHEMA: Int = 2
    const val ENCRYPTED_SCHEMA_LEGACY: Int = 1
    const val KDF_ALGORITHM: String = "PBKDF2WithHmacSHA256"
    const val CIPHER_ALGORITHM: String = "AES/GCM/NoPadding"
    const val PBKDF2_ITERATIONS: Int = 600_000
    const val MIN_ITERATIONS: Int = 100_000
    const val MIN_LEGACY_ITERATIONS: Int = 10_000
    // Cap accepted KDF work factor at the value we actually use for new envelopes (600k).
    // Envelope-supplied iteration counts are attacker-controlled on import; without a tight cap a
    // hostile backup could force ~1M PBKDF2 iterations per guess (CPU-DoS). Legacy v1 envelopes
    // are still accepted via MIN_LEGACY_ITERATIONS.
    const val MAX_ITERATIONS: Int = 600_000
    const val MAX_ENCRYPTED_ENVELOPE_CHARS: Int = 16_000_000
    const val SALT_BYTES: Int = 16
    const val IV_BYTES: Int = 12
    const val KEY_BITS: Int = 256
    const val GCM_TAG_BITS: Int = 128

    private val ENVELOPE_AAD_V1: ByteArray = "martinu-encrypted-backup:v1".toByteArray(Charsets.UTF_8)
    private val secureRandom = SecureRandom()

    private fun buildV2Aad(
        kdf: String,
        iterations: Int,
        cipherAlg: String,
        saltB64: String,
        ivB64: String
    ): ByteArray = "martinu-encrypted-backup:v2|$kdf|$iterations|$cipherAlg|$saltB64|$ivB64"
        .toByteArray(Charsets.UTF_8)

    sealed interface DecryptResult {
        data class Success(val plaintext: String) : DecryptResult
        data object WrongPassphraseOrTampered : DecryptResult
        data object MalformedEnvelope : DecryptResult
    }

    fun isEncryptedBackup(rawText: String): Boolean {
        if (rawText.length > MAX_ENCRYPTED_ENVELOPE_CHARS) return false
        val trimmed = rawText.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}") || !trimmed.contains(ENCRYPTED_FORMAT)) return false
        return try {
            val root = JSONObject(trimmed)
            root.optString("_format", "").trim() == ENCRYPTED_FORMAT
        } catch (_: Exception) {
            false
        }
    }

    fun encryptJson(
        plaintextJson: String,
        passphrase: CharArray,
        iterations: Int = PBKDF2_ITERATIONS
    ): String {
        require(passphrase.isNotEmpty()) { "Passphrase must not be empty." }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) {
            "Iterations out of bounds: $iterations"
        }

        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
        val encoder = Base64.getEncoder()
        val saltB64 = encoder.encodeToString(salt)
        val ivB64 = encoder.encodeToString(iv)
        var derivedKey: ByteArray? = null
        var plaintextBytes: ByteArray? = null

        return try {
            derivedKey = deriveAesKeyBytes(passphrase, salt, iterations)
            val cipherKey = SecretKeySpec(derivedKey, "AES")
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, cipherKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(buildV2Aad(KDF_ALGORITHM, iterations, CIPHER_ALGORITHM, saltB64, ivB64))
            plaintextBytes = plaintextJson.toByteArray(Charsets.UTF_8)
            val cipherBytes = cipher.doFinal(plaintextBytes)

            JSONObject().apply {
                put("_format", ENCRYPTED_FORMAT)
                put("_schema", ENCRYPTED_SCHEMA)
                put("kdf", KDF_ALGORITHM)
                put("iterations", iterations)
                put("cipher", CIPHER_ALGORITHM)
                put("salt", saltB64)
                put("iv", ivB64)
                put("ciphertext", encoder.encodeToString(cipherBytes))
            }.toString()
        } finally {
            derivedKey?.fill(0)
            plaintextBytes?.fill(0)
        }
    }

    fun decryptJson(envelopeJson: String, passphrase: CharArray): DecryptResult {
        if (envelopeJson.length > MAX_ENCRYPTED_ENVELOPE_CHARS) {
            return DecryptResult.MalformedEnvelope
        }
        if (envelopeJson.isBlank() || passphrase.isEmpty()) {
            return if (envelopeJson.isBlank()) DecryptResult.MalformedEnvelope else DecryptResult.WrongPassphraseOrTampered
        }

        val root = try {
            JSONObject(envelopeJson.trim())
        } catch (_: Exception) {
            return DecryptResult.MalformedEnvelope
        } catch (_: StackOverflowError) {
            return DecryptResult.MalformedEnvelope
        }

        val format = root.optString("_format", "").trim()
        val schema = root.optInt("_schema", -1)
        val kdf = root.optString("kdf", "").trim()
        val cipherAlg = root.optString("cipher", "").trim()
        val iterations = root.optInt("iterations", -1)

        val minIterAllowed = when (schema) {
            ENCRYPTED_SCHEMA -> MIN_ITERATIONS
            ENCRYPTED_SCHEMA_LEGACY -> MIN_LEGACY_ITERATIONS
            else -> Int.MAX_VALUE
        }
        if (format != ENCRYPTED_FORMAT ||
            (schema != ENCRYPTED_SCHEMA && schema != ENCRYPTED_SCHEMA_LEGACY) ||
            kdf != KDF_ALGORITHM ||
            cipherAlg != CIPHER_ALGORITHM ||
            iterations !in minIterAllowed..MAX_ITERATIONS
        ) {
            return DecryptResult.MalformedEnvelope
        }

        val saltB64 = root.optString("salt", "").trim()
        val ivB64 = root.optString("iv", "").trim()
        val cipherB64 = root.optString("ciphertext", "").trim()
        if (saltB64.isBlank() || ivB64.isBlank() || cipherB64.isBlank()) {
            return DecryptResult.MalformedEnvelope
        }

        val decoder = Base64.getDecoder()
        val salt = try {
            decoder.decode(saltB64)
        } catch (_: IllegalArgumentException) {
            return DecryptResult.MalformedEnvelope
        }
        val iv = try {
            decoder.decode(ivB64)
        } catch (_: IllegalArgumentException) {
            return DecryptResult.MalformedEnvelope
        }
        val cipherBytes = try {
            decoder.decode(cipherB64)
        } catch (_: IllegalArgumentException) {
            return DecryptResult.MalformedEnvelope
        }

        if (salt.size != SALT_BYTES || iv.size != IV_BYTES || cipherBytes.size <= GCM_TAG_BITS / 8) {
            return DecryptResult.MalformedEnvelope
        }

        var derivedKey: ByteArray? = null
        var plaintextBytes: ByteArray? = null
        return try {
            derivedKey = deriveAesKeyBytes(passphrase, salt, iterations)
            val cipherKey = SecretKeySpec(derivedKey, "AES")
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, cipherKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val aad = if (schema == ENCRYPTED_SCHEMA) {
                buildV2Aad(kdf, iterations, cipherAlg, saltB64, ivB64)
            } else {
                ENVELOPE_AAD_V1
            }
            cipher.updateAAD(aad)
            plaintextBytes = cipher.doFinal(cipherBytes)
            DecryptResult.Success(String(plaintextBytes, Charsets.UTF_8))
        } catch (_: AEADBadTagException) {
            DecryptResult.WrongPassphraseOrTampered
        } catch (_: Exception) {
            DecryptResult.MalformedEnvelope
        } finally {
            derivedKey?.fill(0)
            plaintextBytes?.fill(0)
        }
    }

    private fun deriveAesKeyBytes(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int
    ): ByteArray {
        val pbkdfSpec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(pbkdfSpec).encoded
        } finally {
            pbkdfSpec.clearPassword()
        }
    }
}
