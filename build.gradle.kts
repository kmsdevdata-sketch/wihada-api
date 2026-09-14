plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"

    id("com.diffplug.spotless") version "7.2.1"
    id("com.github.spotbugs") version "6.4.4"
    jacoco
}

group = "io.point3"
version = "0.0.1-SNAPSHOT"
description = "p3-api"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // =========================
    // Spring Boot
    // =========================
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // =========================
    // Persistence
    // =========================
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // =========================
    // Lombok
    // =========================
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // =========================
    // Configuration
    // =========================
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // =========================
    // Development
    // =========================
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // =========================
    // Test
    // =========================
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // =========================
    // AWS
    // =========================
    implementation(platform("io.awspring.cloud:spring-cloud-aws-dependencies:4.0.2"))

    implementation("io.awspring.cloud:spring-cloud-aws-starter-s3")
    implementation("io.awspring.cloud:spring-cloud-aws-starter-sqs")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

spotless {
    java {
        palantirJavaFormat("2.80.0").style("GOOGLE")

        target("src/**/*.java")

        removeUnusedImports() // 사용하지 않는 import 삭제

        trimTrailingWhitespace() // 줄 끝 공백 제거

        endWithNewline() // 파일 마지막 빈줄 추가
    }

    kotlinGradle {
        target("*.gradle.kts")

        ktlint()
    }
}

spotbugs {
    ignoreFailures.set(false) // 버그 발견시 빌드 실패

    effort.set(com.github.spotbugs.snom.Effort.MAX)

    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports {
        create("html") {
            required.set(true)
        }

        create("xml") {
            required.set(false)
        }
    }
}

jacoco {
    toolVersion = "0.8.13"
}
tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}
tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(true)
    }
}
