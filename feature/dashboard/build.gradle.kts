plugins {
    id("studyflow.android.library.compose")
}

android {
    namespace = "com.mikeyphw.studyflow.feature.dashboard"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    testImplementation(libs.junit4)
}
