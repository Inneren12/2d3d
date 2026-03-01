plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    jacoco
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation("io.kotest:kotest-runner-junit5:5.8.1")
    testImplementation("io.kotest:kotest-assertions-core:5.8.1")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
