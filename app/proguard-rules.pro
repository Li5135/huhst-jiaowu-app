# 保留 kotlinx.serialization 生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.huhst.jiaowu.** {
    *** Companion;
}
-keepclasseswithmembers class com.huhst.jiaowu.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# jsoup
-keeppackagenames org.jsoup.nodes
