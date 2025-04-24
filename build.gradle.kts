plugins {
    kotlin("jvm") version "1.9.22"
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("plugin.spring") version "1.7.22"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("com.sun.mail:jakarta.mail:2.0.1")
    implementation("org.apache.james:apache-mime4j-core:0.8.11")
    implementation("org.apache.james:apache-mime4j-dom:0.8.11")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.google.api-client:google-api-client:2.7.0")
    implementation("com.google.oauth-client:google-oauth-client:1.35.0")
    implementation("com.google.apis:google-api-services-gmail:v1-rev20220404-2.0.0")
    implementation("org.apache.tika:tika-core:3.1.0")


}


tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}