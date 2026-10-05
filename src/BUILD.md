# Building Pixel Suite from Source

## Requirements

### Linux / macOS (Recommended)
- Bash
- JDK 8+
- Android SDK (api-23 minimum)
- Tools: apt, zipalign, pksigner, dalvik-exchange

### Windows
- Windows Subsystem for Linux (WSL) or Git Bash
- Or: Docker with an Android build image

## Build Steps

### On Linux / macOS:
\\\ash
cd PixelSuite-source-v2.1
bash build.sh
\\\

The script will:
1. Compile Java stubs and module code
2. Convert to DEX
3. Package APK
4. Sign with included key.jks
5. Output: \out/PixelSuite.apk\

### On Windows:
Use WSL:
\\\ash
wsl
cd /mnt/c/path/to/PixelSuite-source-v2.1
bash build.sh
\\\

Or use Android Studio's built-in tools.

## Signing

If you need to sign with your own keystore:
\\\ash
apksigner sign --ks your-key.jks --ks-pass pass:YOUR_PASS \\
  --ks-key-alias YOUR_ALIAS --min-sdk-version 26 \\
  --out PixelSuite-signed.apk out/unsigned.apk
\\\

## Verification

\\\ash
apksigner verify -v out/PixelSuite.apk
\\\

## License

Built with LSPosed framework. See LICENSE for details.
