plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "2.2.4"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-auth:2.2.4")
    implementation("io.ktor:ktor-server-auth-jwt:2.2.4")
    implementation("io.ktor:ktor-server-netty:2.2.4")
    implementation("io.ktor:ktor-server-content-negotiation:2.2.4")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.2.4")
    implementation("io.ktor:ktor-server-cors:2.2.4")
    implementation("org.apache.commons:commons-email:1.5") // Для отправки email
    implementation("com.sun.mail:javax.mail:1.6.2") // Для IMAP
    implementation("org.jsoup:jsoup:1.15.3") // Для парсинга писем
    implementation("com.auth0:java-jwt:4.2.1") // JWT для авторизации
}


tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}