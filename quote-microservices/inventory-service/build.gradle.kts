plugins {
    java
    application
    jacoco
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

application { mainClass = "dev.portfolio.inventory.Main" }

dependencies {
    implementation(project(":proto"))
    implementation(libs.javalin)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.logback.classic)
    implementation(libs.logstash.encoder)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.javalin.testtools)
    testImplementation(libs.grpc.inprocess)
    testImplementation(libs.grpc.testing)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required = true; html.required = true }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules { rule { limit { counter = "LINE"; minimum = "0.90".toBigDecimal() } } }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
