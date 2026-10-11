pluginManagement {
    repositories {
        google();
        mavenCentral();
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google();
        mavenCentral()
    }
}

rootProject.name = "SiluBrowser"
include(":app")
