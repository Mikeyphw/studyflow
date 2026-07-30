plugins {
    id("studyflow.kotlin.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.core)
    testImplementation(libs.junit4)
}
