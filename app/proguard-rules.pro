# Ktor and its optional logging backends
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# kotlinx.serialization: keep generated serializers for Still's models
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class io.github.stephane849.still.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.stephane849.still.**$$serializer { *; }

# Gson / Retrofit models used by CalmDirectory's place search
-keep class io.github.stephane849.still.** { *; }
-keepattributes Signature
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# MapLibre native bindings
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Google Places and libphonenumber
-dontwarn com.google.android.libraries.places.**
-dontwarn com.google.i18n.phonenumbers.**
