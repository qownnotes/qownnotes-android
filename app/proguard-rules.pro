# The Nextcloud SSO library's NextcloudRetrofitServiceMethod inspects generic return types
# reflectively (method.getGenericReturnType()) to decide how to deserialize responses. Without
# this attribute R8 erases that generic signature, the type check silently falls back to a
# different code path, and every SSO-relayed request breaks in release builds only.
-keepattributes Signature

# Android-SingleSignOn 1.3.4 ships consumer proguard rules of its own (including the
# -keepattributes Signature above), but they don't keep NextcloudRetrofitServiceMethod or
# ParsedResponse themselves, so R8 full mode is still free to rename/optimize them — which
# corrupts the nested generic Signature (Observable<ParsedResponse<OcsResponse>> loses its
# inner <OcsResponse> argument) when it remaps the Signature attribute string. Keep just the
# two classes proven necessary rather than the whole com.nextcloud.android.sso namespace.
-keep class com.nextcloud.android.sso.api.NextcloudRetrofitServiceMethod { *; }
-keep class com.nextcloud.android.sso.api.ParsedResponse { *; }

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
