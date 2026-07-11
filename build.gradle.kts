plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.example"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Phase 1+: property-based тесты для инвариантов matching-алгоритма
    // testImplementation("net.jqwik:jqwik:1.9.1")

    // Phase 2: WebSocket market data feed
    // implementation("org.springframework.boot:spring-boot-starter-websocket")

    // Phase 3: lock-free конкурентность
    // implementation("com.lmax:disruptor:4.0.0")

    // Phase 3: JMH-бенчмарки обычно выносятся в отдельный source set / модуль,
    // подключим когда дойдём до фазы — не тащим заранее.
}

tasks.withType<Test> {
    useJUnitPlatform()
}
