# kotlinx.serialization keeps its generated serializers via the annotation; keep names of our models for JSON.
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class app.hissa.**$$serializer { *; }
-keepclassmembers class app.hissa.** { *** Companion; }
-keepclasseswithmembers class app.hissa.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
