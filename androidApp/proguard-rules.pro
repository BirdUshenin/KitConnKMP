# Ядро Xray зовётся через JNI/gomobile — его классы нельзя переименовывать
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-dontwarn libv2ray.**
