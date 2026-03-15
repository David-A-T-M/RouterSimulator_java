plugins {
    id("java")
    id("application")
    id("checkstyle")
    id("com.diffplug.spotless") version "6.25.0"
}

application {
    mainClass.set("ar.edu.unc.david.routersimulator.Main")
}

checkstyle {
    toolVersion = "10.12.1"
    configFile = file("${rootProject.projectDir}/checkstyle.xml")
    isIgnoreFailures = false
    configProperties = mapOf("org.checkstyle.google.suppressionfilter.config" to "")
}

spotless {
    java {

        googleJavaFormat("1.19.1")

        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()

        target("src/**/*.java")
    }
}

group = "ar.edu.unc.david.routersimulator"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.jetbrains:annotations:24.0.0")
}

tasks.test {
    useJUnitPlatform()
}