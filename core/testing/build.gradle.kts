plugins {
    id("studyflow.kotlin.library")
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:model"))
    testImplementation(libs.junit4)
}
