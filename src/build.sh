#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
AJ=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf out && mkdir -p out/stubs out/classes
# Stubs: compile against the JDK (Executable, AppComponentFactory stub), plus android.jar on cp.
javac -nowarn --release 8 -cp "$AJ" -d out/stubs $(find stubs -name '*.java')
# Module: android-23 boot + our stubs. Module code may use Executable (API24) via the shim;
# android.jar 23 lacks it, so also put the JDK rt implicitly by using --release 8 w/o boot.
javac -nowarn --release 8 -cp "$AJ:out/stubs" -d out/classes $(find src -name '*.java')
dalvik-exchange --dex --min-sdk-version=26 --output=out/classes.dex out/classes
aapt package -f -M AndroidManifest.xml -S res -I "$AJ" -0 arsc -F out/unsigned.apk
cd out
zip -q -0 unsigned.apk classes.dex
mkdir -p META-INF/xposed
cp ../meta/xposed/java_init.list ../meta/xposed/module.prop ../meta/xposed/scope.list META-INF/xposed/
zip -q -X unsigned.apk META-INF/xposed/java_init.list META-INF/xposed/module.prop META-INF/xposed/scope.list
cd ..
zipalign -f -p 4 out/unsigned.apk out/aligned.apk
[ -f key.jks ] || keytool -genkeypair -keystore key.jks -alias pixelsuite -keyalg RSA -keysize 2048 \
  -validity 10000 -storepass pixelsuite -keypass pixelsuite -dname "CN=PixelSuite" >/dev/null 2>&1
apksigner sign --ks key.jks --ks-pass pass:pixelsuite --ks-key-alias pixelsuite \
  --min-sdk-version 26 --v4-signing-enabled false --out out/PixelSuite.apk out/aligned.apk
apksigner verify -v out/PixelSuite.apk | head -5
