import com.android.build.api.dsl.VariantDimension

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
        testInstrumentationRunner = "kr.roadcanvas.app.test.RoadCanvasTestRunner"
        testInstrumentationRunnerArguments["optionsAnnotationPackage"] = "kr.roadcanvas.app.test"
    }

    buildTypes {
        debug {
            vehicleIntegrationFields(defaultLocation = "simulated", defaultDriving = "aaos")
        }
        release {
            vehicleIntegrationFields(defaultLocation = "pleos", defaultDriving = "aaos")
        }
    }

    sourceSets {
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

/**
 * 차량 연동 구현을 빌드 속성으로 고른다.
 * 예: -Proadcanvas.location=simulated -Proadcanvas.driving=simulated -Proadcanvas.simRoute=circle
 * 실제 구현(pleos, aaos)이 들어오기 전에는 앱이 모의 구현으로 대체하고 화면에 미구현으로 표시한다.
 */
fun VariantDimension.vehicleIntegrationFields(defaultLocation: String, defaultDriving: String) {
    fun option(property: String, field: String, default: String, allowed: List<String>) {
        val value = providers.gradleProperty("roadcanvas.$property").orNull ?: default
        require(value in allowed) {
            "roadcanvas.$property 값 '$value'는 지원하지 않습니다. 사용 가능: ${allowed.joinToString()}"
        }
        buildConfigField("String", field, "\"$value\"")
    }
    option("location", "VEHICLE_LOCATION", defaultLocation, listOf("simulated", "pleos"))
    option("driving", "VEHICLE_DRIVING", defaultDriving, listOf("simulated", "aaos"))
    option(
        "simRoute",
        "VEHICLE_SIM_ROUTE",
        "circle",
        listOf("circle", "squished", "line", "stationary")
    )
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

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
