import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val junitJupiterVersion = "5.3.1"
val assertJVersion = "3.11.1"
val ktorVersion = "1.1.2"
val prometheusVersion = "0.5.0"
val mainClass = "no.nav.helse.spinder.AppKt"
val navStreamsVersion = "1a24b7e"
val fuelVersion = "1.15.1"
val arrowVersion = "0.9.0"

plugins {
    java
    kotlin("jvm") version "1.3.30"
}

repositories {
    jcenter()
    mavenCentral()
    maven("http://packages.confluent.io/maven/")
    maven("https://dl.bintray.com/kotlin/ktor")
    mavenLocal()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    compile("ch.qos.logback:logback-classic:1.2.3")
    compile("net.logstash.logback:logstash-logback-encoder:5.2")
    compile("io.ktor:ktor-server-netty:$ktorVersion")
    compile("io.prometheus:simpleclient_common:$prometheusVersion")
    compile("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    compile("com.github.kittinunf.fuel:fuel:$fuelVersion")
    compile("io.arrow-kt:arrow-core-data:$arrowVersion")


    compile("org.apache.kafka:kafka-streams:2.1.1")
    compile("no.nav.helse:streams:$navStreamsVersion")

    compile("org.flywaydb:flyway-core:5.2.3")
    compile("no.nav:vault-jdbc:1.3.1")
    compile("org.postgresql:postgresql:42.2.5")

    compile("commons-lang:commons-lang:2.6")

    testCompile("com.h2database:h2:1.4.199")
    testCompile("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testCompile("org.assertj:assertj-core:$assertJVersion")

    testCompile ("org.apache.kafka:kafka_2.12:2.1.1") // overrides 2.0.1 in kafka-embedded
    testCompile ("no.nav:kafka-embedded-env:2.1.1")
    testCompile("com.github.tomakehurst:wiremock:2.19.0") {
        exclude(group = "junit")
    }

}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.named<Jar>("jar") {
    baseName = "app"

    manifest {
        attributes["Main-Class"] = mainClass
        attributes["Class-Path"] = configurations["compile"].map {
            it.name
        }.joinToString(separator = " ")
    }

    doLast {
        configurations["compile"].forEach {
            val file = File("$buildDir/libs/${it.name}")
            if (!file.exists())
                it.copyTo(file)
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "5.1.1"
}