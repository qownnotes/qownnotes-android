plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.licensee) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint("1.7.1")
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint("1.7.1")
    }
    format("misc") {
        target(".gitignore", "Justfile", "**/*.md", "**/*.yml", "**/*.yaml", "**/*.properties")
        targetExclude("**/build/**", ".devenv/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
