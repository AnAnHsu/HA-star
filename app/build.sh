#!/bin/bash
# HA star 壳构建脚本
#
# 依赖（Ubuntu / aarch64，可从 apt 装）：
#   apt install aapt apksigner zipalign android-sdk-platform-23
#   另外需要 JDK 17，以及 r8.jar（放 tools/r8.jar）或 d8
#
# 用法：
#   bash build.sh            生成未签名包
#   bash build.sh sign       顺带签名（先在下面填自己的 keystore）

set -e

APP="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$APP")"
OUT="$ROOT/build"
JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
R8="${R8_JAR:-$ROOT/tools/r8.jar}"

# ---- 换成你自己的签名 ----
KS="${HASTAR_KS:-$ROOT/hastar-release.jks}"
ALIAS="${HASTAR_ALIAS:-hastar}"
PASS="${HASTAR_PASS:-please-change-me}"

# 网页本体在仓库根目录，构建时拷进 assets
mkdir -p "$APP/assets"
cp "$ROOT/ha_star.html" "$APP/assets/ha_star.html"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dexout"

echo "== 1/5 打包资源与清单"
aapt package -f \
  -M "$APP/AndroidManifest.xml" \
  -S "$APP/res" \
  -A "$APP/assets" \
  -I "$JAR" \
  -F "$OUT/base.apk"

echo "== 2/5 编译 Java"
javac -source 8 -target 8 -encoding utf-8 \
  -bootclasspath "$JAR" \
  -d "$OUT/classes" \
  $(find "$APP/src" -name '*.java')

echo "== 3/5 转成 classes.dex"
java -cp "$R8" com.android.tools.r8.D8 \
  --min-api 26 --release \
  --output "$OUT/dexout" \
  $(find "$OUT/classes" -name '*.class')
cp "$OUT/dexout/classes.dex" "$OUT/classes.dex"

echo "== 4/5 装回 apk"
cd "$OUT"
aapt add -f base.apk classes.dex
cd - >/dev/null

echo "== 5/5 对齐"
zipalign -f 4 "$OUT/base.apk" "$OUT/ha_star-unsigned.apk"

if [ "$1" = "sign" ]; then
  echo "== 签名"
  apksigner sign \
    --ks "$KS" --ks-key-alias "$ALIAS" \
    --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
    --out "$OUT/ha_star.apk" \
    "$OUT/ha_star-unsigned.apk"
  apksigner verify --print-certs "$OUT/ha_star.apk" | head -5
  echo "产物：$OUT/ha_star.apk"
else
  echo "产物：$OUT/ha_star-unsigned.apk"
fi