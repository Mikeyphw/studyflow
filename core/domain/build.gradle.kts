plugins {
    id("studyflow.kotlin.library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:portability"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(kotlin("test-junit"))
    testImplementation(project(":core:link"))
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
