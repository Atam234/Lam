# Argus (Android)

Android Kotlin + Jetpack Compose port of **Atam234/Argus** for authorized security testing and educational session telemetry.

## Ported Features
- **Session Logger (`SessionLoggerScreen`, `KeystrokeLogEngine`, `KeyboardLayoutTranslator`)**:
  - `local` and `debug` logging modes.
  - Dynamic `CHAR_LIMIT` calculation based on active RSA modulus size (`ceil(bits/8) - 2*64 - 2`) or `20` bytes in plaintext mode.
  - `[WindowName: ...]`, `[Time: HH:MM]`, and opt-in `[Clipboard: ...]` context tagging.
  - `backspace_buffer_len` guard preserving technical tags while allowing editing of typed segments.
  - Full Latin ↔ Cyrillic keyboard layout translation (Russian, Ukrainian, Belarusian, Kazakh, Kyrgyz, Mongolian, Tajik, Tatar, Serbian, Uzbek, Azeri) with Shift + CapsLock state logic.
- **MD5 Daily Log Vault (`LogVaultScreen`, `LogVaultRepository`)**:
  - Daily local log files named `MD5(YYYY-MMM-dd).txt`.
  - Inspect raw chunks, export via Storage Access Framework, delete, send to Decryptor, or explicitly upload to a user-configured HTTP/HTTPS endpoint.
- **Log Decryptor (`LogDecryptorScreen`)**:
  - RSA PKCS#1 OAEP (SHA3-512) decryption of `---START---...---END---` Base64 chunks.
  - Supports passphrase-protected PKCS#8 `.pem` private keys, per-chunk error reporting (`- Message decryption error -`), `.txt` export, and output clearing.
- **RSA Key Manager (`RsaKeysScreen`, `RsaCryptoEngine`)**:
  - 2048/4096-bit RSA keypair generation with optional AES-256-CBC PKCS#8 passphrase encryption and PEM export/import.

## Notes on Platform Differences
- Windows-only stealth/persistence mechanisms (`winreg` startup persistence, console hiding, `Taskmgr.exe` termination, screenshot freezing, and silent Tor Browser auto-installation) were omitted in compliance with Android security and Google Play policies.
