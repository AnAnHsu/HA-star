# tools

把 `r8.jar` 放在本目录。没有它也可以用系统自带的 `d8`，把 `app/build.sh` 里的 R8 路径改掉即可。

下载（约 16MB，纯 Java，任何平台都能跑）：

```bash
curl -L -o tools/r8.jar https://maven.aliyun.com/repository/google/com/android/tools/r8/8.3.37/r8-8.3.37.jar
```

官方备用地址：`https://maven.google.com/com/android/tools/r8/8.3.37/r8-8.3.37.jar`
