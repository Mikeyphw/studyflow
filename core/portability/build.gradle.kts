plugins {
    id("studyflow.kotlin.library.serialization")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit4)
}
