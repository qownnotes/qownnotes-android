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
