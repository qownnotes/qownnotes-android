# The Nextcloud SSO library's NextcloudRetrofitServiceMethod inspects generic return types
# reflectively (method.getGenericReturnType()) to decide how to deserialize responses. Without
# this attribute R8 erases that generic signature, the type check silently falls back to a
# different code path, and every SSO-relayed request breaks in release builds only.
-keepattributes Signature

# The Nextcloud SSO library's own classes (ParsedResponse, NextcloudRetrofitServiceMethod) are
# not covered by any consumer proguard rules it ships, so R8 is free to rename/optimize them —
# which appears to corrupt the nested generic Signature (Observable<ParsedResponse<OcsResponse>>
# loses its inner <OcsResponse> argument) when it remaps the Signature attribute string.
-keep class com.nextcloud.android.sso.** { *; }
-keep interface com.nextcloud.android.sso.** { *; }

# Retrofit and Gson access Nextcloud API contracts and payloads reflectively.
-keep,allowoptimization interface org.qownnotes.mobile.backend.nextcloud.**Api { *; }
-keep,allowoptimization class org.qownnotes.mobile.backend.nextcloud.**Dto { *; }
-keep,allowoptimization class org.qownnotes.mobile.backend.nextcloud.OcsResponse { *; }
-keep,allowoptimization class org.qownnotes.mobile.backend.nextcloud.OcsEnvelope { *; }
-keep,allowoptimization class org.qownnotes.mobile.backend.nextcloud.CapabilitiesData { *; }

# Markwon probes these optional decoders, which QOwnNotes does not include or register.
-dontwarn com.caverock.androidsvg.SVG
-dontwarn com.caverock.androidsvg.SVGParseException
-dontwarn pl.droidsonroids.gif.GifDrawable
