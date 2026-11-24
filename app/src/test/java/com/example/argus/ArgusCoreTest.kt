package com.example.argus

import com.example.argus.crypto.RsaCryptoEngine
import com.example.argus.data.LogVaultRepository
import com.example.argus.logger.KeyboardLayoutTranslator
import com.example.argus.logger.KeystrokeLogEngine
import com.example.argus.logger.LoggerMode
import com.example.argus.logger.LoggerSessionState
import com.example.argus.logger.SpecialKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgusCoreTest {

    @Test
    fun rsaOaepSha3_512_roundTripWithPassphraseAndChunking() {
        val kp = RsaCryptoEngine.generateKeyPair(keySizeBits = 2048, passphrase = "argus-secret-passphrase")
        assertTrue(kp.publicKeyPem.contains("-----BEGIN PUBLIC KEY-----"))
        assertTrue(kp.privateKeyPem.contains("-----BEGIN ENCRYPTED PRIVATE KEY-----"))
        assertEquals(126, RsaCryptoEngine.computeCharLimit(kp.publicKeyPem))

        val sampleLog = "[Time: 10:15]: [WindowName: Terminal]: " +
            "Testing Cyrillic layout Привет мир! ".repeat(6) + "<ENTER>"
        val encrypted = RsaCryptoEngine.encrypt(sampleLog, kp.publicKeyPem)
        assertTrue(encrypted.contains("---START---"))
        assertTrue(encrypted.contains("---END---"))

        val decrypted = RsaCryptoEngine.decryptMany(encrypted, kp.privateKeyPem, "argus-secret-passphrase")
        assertEquals(0, decrypted.errorChunks)
        assertTrue(decrypted.totalChunks >= 2)
        assertEquals(sampleLog, decrypted.decryptedText.replace("\n", "").let {
            // Multi-chunk join adds \n between chunks matching log_decryptor.py
            decrypted.decryptedText.split("\n").joinToString("")
        })
    }

    @Test
    fun keyboardLayoutTranslator_latinToCyrillicAndCase() {
        val translated = KeyboardLayoutTranslator.translateCharacter(
            ch = 'g',
            initialLanguage = "English - United States",
            currentLanguage = "Russian",
            upperCase = false
        )
        assertEquals('п', translated)

        val upperTranslated = KeyboardLayoutTranslator.translateCharacter(
            ch = 'g',
            initialLanguage = "English - United States",
            currentLanguage = "Russian",
            upperCase = true
        )
        assertEquals('П', upperTranslated)
    }

    @Test
    fun keystrokeLogEngine_backspaceGuardAndMd5Filename() {
        val engine = KeystrokeLogEngine()
        var state = LoggerSessionState(
            mode = LoggerMode.DEBUG,
            encryptionEnabled = false,
            activeWindowName = "Notes",
            charLimit = 200
        )
        val persisted = mutableListOf<String>()

        state = engine.onCharacterTyped(state, 'a', null, null) { persisted.add(it) }
        state = engine.onCharacterTyped(state, 'b', null, null) { persisted.add(it) }
        // Backspace removes 'b', then 'a', then emits <BACKSPACE> at tag boundary
        state = engine.onSpecialKey(state, SpecialKey.BACKSPACE, null, null) { persisted.add(it) }
        state = engine.onSpecialKey(state, SpecialKey.BACKSPACE, null, null) { persisted.add(it) }
        state = engine.onSpecialKey(state, SpecialKey.BACKSPACE, null, null) { persisted.add(it) }

        assertTrue(state.lineBuffer.contains("[WindowName: Notes]: "))
        assertTrue(state.lineBuffer.endsWith("<BACKSPACE>"))
        assertEquals(32, LogVaultRepository.md5Hex("2026-Nov-24").length)
    }
}
