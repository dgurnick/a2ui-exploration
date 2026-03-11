plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    id("io.ktor.plugin") version "2.3.11"
    application
}

group = "com.dgurnick.bff"
version = "0.1.0"

application {
    mainClass.set("com.dgurnick.bff.ApplicationKt")
}

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.11"
val kotlinxSerializationVersion = "1.6.3"
val logbackVersion = "1.4.14"
val graphqlKotlinVersion = "7.1.4"
val jacksonVersion = "2.17.2"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")

    // GraphQL – graphql-kotlin Ktor server plugin
    // Brings in graphql-java, schema generator, and Ktor routing helpers
    implementation("com.expediagroup:graphql-kotlin-ktor-server:$graphqlKotlinVersion")

    // Jackson – required by graphql-kotlin's HTTP transport layer
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    // kotlinx.serialization – used in model classes & agent code
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
}

tasks.test {
    useJUnitPlatform()
}
