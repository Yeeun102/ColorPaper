// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false // 👈 [필수 추가!!] 이 줄이 없어서 터진 거였습니다!
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.google.gms.google.services) apply false
}