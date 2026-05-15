import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.spring") version "1.9.20"
    kotlin("plugin.jpa") version "1.9.20"
}

group = "com.wrbug"
version = "1.1.3"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

configurations.configureEach {
    // Avoid JUL -> SLF4J bridging inside the executable jar. In this packaging
    // mode it can break Tomcat request logging with missing logback classes.
    exclude(group = "org.slf4j", module = "jul-to-slf4j")
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Database
    implementation("com.mysql:mysql-connector-j:8.2.0")
    implementation("org.flywaydb:flyway-core:10.17.0")
    implementation("org.flywaydb:flyway-mysql:10.17.0")
    
    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Excel report export
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // HTML parsing for score and crown responses
    implementation("org.jsoup:jsoup:1.17.2")
    
    // Logging
    implementation("org.slf4j:slf4j-api")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    archiveBaseName.set("auto-bookkeeping-backend")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    requiresUnpack("**/driver-*.jar", "**/driver-bundle-*.jar")
}



