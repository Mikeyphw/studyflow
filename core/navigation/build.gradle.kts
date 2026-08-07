plugins {
    id("studyflow.kotlin.library.serialization")
}

dependencies {
    compileOnly(libs.androidx.annotation)
    implementation(libs.kotlinx.serialization.core)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.serialization.json)
}
