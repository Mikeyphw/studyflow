plugins {
    id("studyflow.kotlin.library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:portability"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
