plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
    jacoco
}

android {
    namespace = "kr.roadcanvas.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.roadcanvas.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "io.cucumber.android.runner.CucumberAndroidJUnitRunner"
        testInstrumentationRunnerArguments["optionsAnnotationPackage"] = "kr.roadcanvas.app.test"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("io.cucumber:cucumber-android:7.18.1")
    androidTestImplementation("io.cucumber:cucumber-picocontainer:7.18.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register<JacocoReport>("unitTestCoverageReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    val excludedClasses =
        listOf("**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*")
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
            exclude(excludedClasses)
        },
        fileTree(
            layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")
        ) {
            exclude(excludedClasses)
        }
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"))
    onlyIf { executionData.files.any { it.exists() } }
}
