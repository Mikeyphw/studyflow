plugins {
    id("studyflow.kotlin.library.serialization")
}

dependencies {
    implementation(libs.kotlinx.serialization.core)
    testImplementation(libs.junit4)
}
