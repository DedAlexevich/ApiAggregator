plugins {
    id("java")
    id("application")
}

group = "ru.dedalexeich"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}


application {
    mainClass.set("ru.dedalexeich.Main")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.4")
    implementation("org.apache.commons:commons-csv:1.14.1")
    implementation("ch.qos.logback:logback-classic:1.6.5")
    implementation("org.slf4j:slf4j-api:2.0.20")
}


tasks.test {
    useJUnitPlatform()
}