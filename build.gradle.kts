plugins {
    kotlin("jvm") version "2.1.0"
    application
    id("io.ktor.plugin") version "3.0.3"
}
group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://nexus.inmo.dev/repository/maven-releases/")
    maven("https://repo.kotlin.link")
}

dependencies {
    implementation("dev.inmo:tgbotapi:25.0.1")
    implementation ("me.y9san9.ksm:telegram:0.0.1-dev009")
    implementation ("me.y9san9.ksm:kotlinx-serialization-json:0.0.1-dev009")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("space.kscience:plotlykt-core:0.7.1.2-ktor3")
    implementation("space.kscience:plotlykt-server:0.7.1.2-ktor3")
    implementation("org.jfree:jfreechart:1.5.4")
    implementation("org.knowm.xchart:xchart:3.8.8")

    implementation("org.postgresql:postgresql:42.7.7")

    implementation("org.jetbrains.exposed:exposed-core:1.0.0-beta-2")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.0.0-beta-2")
}

application {
    mainClass = "org.example.MainKt"
}

ktor {
    fatJar {
        archiveFileName.set("app.jar")
    }
}