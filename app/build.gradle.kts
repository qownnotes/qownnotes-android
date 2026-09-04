import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.licensee)
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val releaseVersionName = requireNotNull(versionProperties.getProperty("VERSION_NAME"))
val releaseVersionCode = requireNotNull(versionProperties.getProperty("VERSION_CODE")).toInt()
val developmentKeystorePath = providers.environmentVariable("ANDROID_DEV_KEYSTORE_PATH").orNull
val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull

android {
    namespace = "org.qownnotes.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.qownnotes.mobile"
        minSdk = 28
        targetSdk = 36
        versionCode = providers.environmentVariable("ANDROID_VERSION_CODE").orNull?.toInt()
            ?: releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "org.qownnotes.mobile.QOwnNotesTestRunner"
    }

    signingConfigs {
        if (developmentKeystorePath != null) {
            create("development") {
                storeFile = file(developmentKeystorePath)
                storePassword = providers.environmentVariable("ANDROID_DEV_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_DEV_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_DEV_KEY_PASSWORD").get()
            }
        }
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "QOwnNotes Dev")
            signingConfigs.findByName("development")?.let { signingConfig = it }
        }
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

kotlin { jvmToolchain(17) }

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":backend-nextcloud"))
    implementation(project(":markdown-android"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

licensee {
    allow("Apache-2.0")
    allow("MIT-0")
    allowUrl("http://opensource.org/licenses/BSD-2-Clause")
    allowUrl("https://api.github.com/licenses/gpl-3.0")
}
