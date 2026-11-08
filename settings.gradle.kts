pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://repository.map.naver.com/archive/maven")
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/") {
            content { includeGroup("ai.pleos.playground") }
        }
    }
}

rootProject.name = "RoadCanvas"
include(":app")
