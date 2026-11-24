package com.example.argus.crypto

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMEncryptedKeyPair
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.PKCS8Generator
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import java.io.StringReader
import java.io.StringWriter
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

data class GeneratedKeyPairPem(
    val publicKeyPem: String,
    val privateKeyPem: String,
    val keySizeBits: Int,
    val isPassphraseProtected: Boolean
)

data class DecryptionResult(
    val decryptedText: String,
    val totalChunks: Int,
    val successChunks: Int,
    val errorChunks: Int
)

object RsaCryptoEngine {

    private const val SHA3_512_DIGEST_BYTES = 64
    private const val OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA3-512AndMGF1Padding"
    private const val START_MARKER = "---START---"
    private const val END_MARKER = "---END---"

    private val bcProvider = BouncyCastleProvider()

    private val oaepSpec = OAEPParameterSpec(
        "SHA3-512",
        "MGF1",
        MGF1ParameterSpec("SHA3-512"),
        PSource.PSpecified.DEFAULT
    )

    fun generateKeyPair(keySizeBits: Int = 4096, passphrase: String = ""): GeneratedKeyPairPem {
        val generator = KeyPairGenerator.getInstance("RSA", bcProvider)
        generator.initialize(keySizeBits, SecureRandom())
        val pair = generator.generateKeyPair()

        val pubWriter = StringWriter()
        JcaPEMWriter(pubWriter).use { pemWriter ->
            pemWriter.writeObject(pair.public)
        }
        val publicPem = pubWriter.toString().trim()

        val privWriter = StringWriter()
        JcaPEMWriter(privWriter).use { pemWriter ->
            if (passphrase.isNotEmpty()) {
                val encryptor = JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.AES_256_CBC)
                    .setProvider(bcProvider)
                    .setRandom(SecureRandom())
                    .setPassword(passphrase.toCharArray())
                    .build()
                val pkcs8Gen = JcaPKCS8Generator(pair.private, encryptor)
                pemWriter.writeObject(pkcs8Gen)
            } else {
                val pkcs8Gen = JcaPKCS8Generator(pair.private, null)
                pemWriter.writeObject(pkcs8Gen)
            }
        }
        val privatePem = privWriter.toString().trim()

        return GeneratedKeyPairPem(
            publicKeyPem = publicPem,
            privateKeyPem = privatePem,
            keySizeBits = keySizeBits,
            isPassphraseProtected = passphrase.isNotEmpty()
        )
    }

    /**
     * Mirrors start.py CHAR_LIMIT formula:
     * ceil_div(size(key.n), 8) - 2 * SHA3_512.digest_size - 2
     */
    fun computeCharLimit(publicKeyPem: String): Int {
        val pubKey = parsePublicKey(publicKeyPem) as? RSAPublicKey
            ?: throw IllegalArgumentException("Not a valid RSA public key")
        val modulusBytes = (pubKey.modulus.bitLength() + 7) / 8
        val limit = modulusBytes - 2 * SHA3_512_DIGEST_BYTES - 2
        return limit.coerceAtLeast(16)
    }

    fun getPublicKeyBitLength(publicKeyPem: String): Int? = runCatching {
        val pubKey = parsePublicKey(publicKeyPem) as? RSAPublicKey
        pubKey?.modulus?.bitLength()
    }.getOrNull()

    fun parsePublicKey(pem: String): PublicKey {
        val converter = JcaPEMKeyConverter().setProvider(bcProvider)
        PEMParser(StringReader(pem.trim())).use { parser ->
            val obj = parser.readObject()
                ?: throw IllegalArgumentException("Empty or invalid public key PEM")
            return when (obj) {
                is SubjectPublicKeyInfo -> converter.getPublicKey(obj)
                else -> throw IllegalArgumentException("Unsupported public key PEM format: ${obj.javaClass.simpleName}")
            }
        }
    }

    fun parsePrivateKey(pem: String, passphrase: String? = null): PrivateKey {
        val converter = JcaPEMKeyConverter().setProvider(bcProvider)
        val pwd = passphrase?.takeIf { it.isNotEmpty() }?.toCharArray()
        PEMParser(StringReader(pem.trim())).use { parser ->
            val obj = parser.readObject()
                ?: throw IllegalArgumentException("Empty or invalid private key PEM")
            return when (obj) {
                is PEMKeyPair -> converter.getKeyPair(obj).private
                is PrivateKeyInfo -> converter.getPrivateKey(obj)
                is PEMEncryptedKeyPair -> {
                    val password = pwd ?: throw IllegalArgumentException("Passphrase required for encrypted private key")
                    val decProv = JcePEMDecryptorProviderBuilder().setProvider(bcProvider).build(password)
                    converter.getKeyPair(obj.decryptKeyPair(decProv)).private
                }
                is PKCS8EncryptedPrivateKeyInfo -> {
                    val password = pwd ?: throw IllegalArgumentException("Passphrase required for encrypted PKCS#8 key")
                    val decProv = JceOpenSSLPKCS8DecryptorProviderBuilder().setProvider(bcProvider).build(password)
                    converter.getPrivateKey(obj.decryptPrivateKeyInfo(decProv))
                }
                else -> throw IllegalArgumentException("Unsupported private key PEM format: ${obj.javaClass.simpleName}")
            }
        }
    }

    fun isPrivateKeyEncrypted(pem: String): Boolean = runCatching {
        PEMParser(StringReader(pem.trim())).use { parser ->
            val obj = parser.readObject()
            obj is PEMEncryptedKeyPair || obj is PKCS8EncryptedPrivateKeyInfo
        }
    }.getOrDefault(false)

    /**
     * Mirrors start.py encrypt():
     * Encrypts UTF-8 message in chunks <= CHAR_LIMIT bytes using PKCS1_OAEP (SHA3-512),
     * wrapping each Base64 chunk in \n---START---<b64>---END---\n.
     */
    fun encrypt(message: String, publicKeyPem: String): String {
        val publicKey = parsePublicKey(publicKeyPem)
        val charLimit = computeCharLimit(publicKeyPem)
        val cipher = Cipher.getInstance(OAEP_TRANSFORMATION, bcProvider)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSpec)

        val chunks = splitUtf8Safe(message, charLimit)
        val sb = StringBuilder()
        for (chunkBytes in chunks) {
            val encrypted = cipher.doFinal(chunkBytes)
            val b64 = Base64.getEncoder().encodeToString(encrypted)
            sb.append('\n')
                .append(START_MARKER)
                .append(b64)
                .append(END_MARKER)
                .append('\n')
        }
        return sb.toString()
    }

    /**
     * Mirrors log_decryptor.py decrypt_many() and decrypt():
     * Strips newlines and ---START---, splits on ---END---, decrypts each Base64 chunk.
     * Emits "\n- Message decryption error -\n" for any failing chunk.
     */
    fun decryptMany(
        encryptedLog: String,
        privateKeyPem: String,
        passphrase: String? = null
    ): DecryptionResult {
        val privateKey = parsePrivateKey(privateKeyPem, passphrase)
        val cipher = Cipher.getInstance(OAEP_TRANSFORMATION, bcProvider)
        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec)

        val normalized = encryptedLog
            .replace("\r", "")
            .replace("\n", "")
            .replace(START_MARKER, "")
        val rawChunks = normalized.split(END_MARKER).filter { it.isNotBlank() }

        var successCount = 0
        var errorCount = 0
        val decryptedParts = rawChunks.map { b64Chunk ->
            try {
                val decoded = Base64.getMimeDecoder().decode(b64Chunk.trim())
                val plainBytes = cipher.doFinal(decoded)
                successCount++
                String(plainBytes, Charsets.UTF_8)
            } catch (_: Exception) {
                errorCount++
                "\n- Message decryption error -\n"
            }
        }

        return DecryptionResult(
            decryptedText = decryptedParts.joinToString("\n"),
            totalChunks = rawChunks.size,
            successChunks = successCount,
            errorChunks = errorCount
        )
    }

    private fun splitUtf8Safe(text: String, maxBytes: Int): List<ByteArray> {
        if (text.isEmpty()) return listOf(ByteArray(0))
        val fullBytes = text.toByteArray(Charsets.UTF_8)
        if (fullBytes.size <= maxBytes) return listOf(fullBytes)

        val result = mutableListOf<ByteArray>()
        var current = StringBuilder()
        var currentByteCount = 0

        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            val piece = text.substring(index, index + charCount)
            val pieceBytes = piece.toByteArray(Charsets.UTF_8)

            if (currentByteCount + pieceBytes.size > maxBytes && currentByteCount > 0) {
                result.add(current.toString().toByteArray(Charsets.UTF_8))
                current = StringBuilder()
                currentByteCount = 0
            }

            if (pieceBytes.size > maxBytes) {
                // Fallback if maxBytes is somehow tiny
                result.add(pieceBytes)
            } else {
                current.append(piece)
                currentByteCount += pieceBytes.size
            }
            index += charCount
        }
        if (current.isNotEmpty()) {
            result.add(current.toString().toByteArray(Charsets.UTF_8))
        }
        return result
    }
}
