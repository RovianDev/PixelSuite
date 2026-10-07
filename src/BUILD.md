# Building Pixel Suite from Source

## Requirements

Linux, macOS, or Windows with WSL (Ubuntu). These are the tools used to build the released APK.

- Bash
- JDK 8 or newer (tested with OpenJDK 25)
- Android platform 23 (`android.jar`)
- `aapt`, `zipalign`, `apksigner`, `dalvik-exchange`

On Ubuntu / Debian (including WSL):

```bash
sudo apt install default-jdk-headless aapt zipalign apksigner dalvik-exchange android-sdk-platform-23
```

## Build

From the root of the repository:

```bash
cd src
sed -i 's/\r$//' build.sh meta/xposed/*   # only needed if the files have Windows line endings
bash build.sh
```

The script:

1. Compiles the Java stubs and module code
2. Converts the classes to DEX
3. Packages the APK
4. Signs it with a throwaway test key (generated as `key.jks` on the first run)
5. Writes `out/PixelSuite.apk`

On Windows, run these commands inside WSL. Copying the project to your Linux home folder first makes the build faster and avoids line-ending problems:

```bash
cp -r "/mnt/c/path/to/PixelSuite/src" ~/pixelsuite-build
cd ~/pixelsuite-build
sed -i 's/\r$//' build.sh meta/xposed/*
bash build.sh
```

## Signing a release build

The test-signed APK is fine for trying the module. For a release, sign the aligned APK with your own keystore. The password is requested at the prompt:

```bash
apksigner sign --ks your-keystore.jks --ks-key-alias youralias \
  --min-sdk-version 26 --v4-signing-enabled false \
  --out PixelSuite-signed.apk out/aligned.apk
```

`--v4-signing-enabled false` stops apksigner from writing a separate `.idsig` file next to the APK; it isn't needed to install the module.

The build script generates a test keystore called `key.jks` on its first run. Don't commit it or use it for releases.

An update only installs over an existing install if it is signed with the same key. Keep your keystore private and never commit it to the repository.

## Verification

```bash
apksigner verify --print-certs PixelSuite-signed.apk
```

## License

Pixel Suite is released under the GPL-3.0 license. See [LICENSE](../LICENSE).
