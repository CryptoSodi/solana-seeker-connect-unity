pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "UnitySeekerConnectNative"
include(":seekerBridge")
project(":seekerBridge").projectDir = file("../Packages/com.community.seekerconnect.unity/Native~")
