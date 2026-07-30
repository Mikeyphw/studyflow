plugins {
    id("studyflow.android.library")
}

android {
    namespace = "com.mikeyphw.studyflow.core.i18n"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
}
