// Top-level build file where you can add configuration options common to all sub-projects/modules.
// AGP 9.x has built-in Kotlin support, so the org.jetbrains.kotlin.android plugin is NOT applied.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
