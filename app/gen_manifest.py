import io, re, sys
# 用法: python3 gen_manifest.py <输出的Manifest路径> <versionCode> <versionName>
src = '/sdcard/hastar/app/AndroidManifest.xml'
out, vc, vn = sys.argv[1], sys.argv[2], sys.argv[3]
s = io.open(src, encoding='utf-8').read()
s, n1 = re.subn(r'android:versionCode="[^"]*"', 'android:versionCode="%s"' % vc, s, count=1)
s, n2 = re.subn(r'android:versionName="[^"]*"', 'android:versionName="%s"' % vn, s, count=1)
assert n1 == 1 and n2 == 1, (n1, n2)
io.open(out, 'w', encoding='utf-8').write(s)
print('manifest ->', out, 'code', vc, 'name', vn)
