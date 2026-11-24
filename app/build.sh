#!/bin/bash
# HA star 外壳构建脚本
# 依赖（Ubuntu, aarch64 可从 apt 装）：
#   apt install aapt dx apksigner zipalign android-sdk-platform-23
#
# 用法：
#   bash build.sh            生成未签名包
#   bash build.sh sign       顺带用 hastar-release.jks 签名

set -e

APP=/sdcard/hastar/app
OUT=/sdcard/hastar/build
JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
KS=/sdcard/hastar/hastar-release.jks
ALIAS=hastar
PASS=please-change-me

# 从网页页脚读版本号，自动写进门牌号，避免每一版都叫同一个号
VN=$(python3 -c "import re,io;s=io.open('$APP/assets/ha_star.html',encoding='utf-8').read();m=re.search(r'HA star . v([0-9]+)[.]([0-9]+)',s);print('%s.%s'%(m.group(1),m.group(2)) if m else '0.0')")
VC=$(echo "$VN" | awk -F. '{printf "%d", $1*100+$2}')
echo "版本 $VN（code $VC）"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

echo "== 1/5 打包资源与清单"
python3 "$APP/gen_manifest.py" "$OUT/AndroidManifest.xml" "$VC" "$VN"
aapt package -f \
  -M "$OUT/AndroidManifest.xml" \
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
mkdir -p "$OUT/dexout"
java -cp /sdcard/hastar/tools/r8.jar com.android.tools.r8.D8 \
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