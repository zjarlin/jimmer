plugins {
   id("site.addzero.gradle.plugin.kotlin-convention") version "+"
}

dependencies {
    implementation("site.addzero:tool-str:2026.02.23")
//    kotlin("stdlib")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

description = "语言无关的不完备抽象层"
