plugins {
    id("studyflow.android.library.compose")
}

android {
    namespace = "com.mikeyphw.studyflow.feature.reminders"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:link"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
}
