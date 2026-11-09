import com.android.build.api.dsl.VariantDimension
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint")
    jacoco
}

// 설정 파일이 없는 CI에서도 기존 기능을 빌드한다.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "kr.roadcanvas.app"
    compileSdk = 35
    useLibrary("android.car")

    defaultConfig {
        applicationId = "kr.roadcanvas.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "kr.roadcanvas.app.test.RoadCanvasTestRunner"
        testInstrumentationRunnerArguments["optionsAnnotationPackage"] = "kr.roadcanvas.app.test"
        naverMapFields()
    }

    buildTypes {
        debug {
            vehicleIntegrationFields(defaultLocation = "pleos", defaultDriving = "aaos")
        }
        release {
            vehicleIntegrationFields(defaultLocation = "pleos", defaultDriving = "aaos")
        }
    }

    sourceSets {
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
        // Room 마이그레이션 테스트가 버전별 스키마를 읽는다.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // Room 저장소 단위 테스트를 Robolectric으로 JVM에서 실행한다.
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // SDG가 끌어오는 lifecycle 2.9의 LiveData 검사기는 AGP 8.7 Lint에서 IncompatibleClassChangeError로 중단된다.
        // 앱은 LiveData를 쓰지 않으므로 이 검사만 끈다.
        disable += "NullSafeMutableLiveData"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

/**
 * 차량 연동 구현을 빌드 속성으로 고른다.
 * 기본값은 debug와 release 모두 실제 SDK(위치 pleos, 주행 규제 aaos)다.
 * 모의 구현은 명시했을 때만 쓴다. 예: -Proadcanvas.location=simulated -Proadcanvas.driving=simulated -Proadcanvas.simRoute=circle
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * 네이버 지도 키 ID를 환경 변수 NAVER_MAP_CLIENT_ID 또는 local.properties의 같은 이름 값에서 읽는다.
 * 값이 없으면 빈 값으로 빌드하고 앱은 지도 영역에 키 없음 안내를 표시한다.
 */
fun VariantDimension.naverMapFields() {
    val name = "NAVER_MAP_CLIENT_ID"
    val localProperties = rootProject.file("local.properties")
    val fromLocal = if (localProperties.isFile) {
        Properties().apply { localProperties.inputStream().use(::load) }.getProperty(name)
    } else {
        null
    }
    val clientId = (
        providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
            ?: fromLocal
        )
        ?.trim()
        .orEmpty()
    manifestPlaceholders["naverMapClientId"] = clientId
    buildConfigField("String", name, "\"$clientId\"")
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // SDG 디자인 시스템. SDG 코드는 Hilt를 쓰지 않으므로 앱에 Hilt를 들이지 않는다.
    implementation("com.shoplworks:SDG-Android:1.26.3") {
        exclude(group = "com.google.dagger", module = "hilt-android")
    }
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.naver.maps:map-sdk:3.24.0")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")

    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("io.cucumber:cucumber-android:7.18.1")
    androidTestImplementation("io.cucumber:cucumber-picocontainer:7.18.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Robolectric이 불러온 클래스도 커버리지에 포함한다.
tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
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
