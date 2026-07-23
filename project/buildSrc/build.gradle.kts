plugins {
    `kotlin-dsl`
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20")
    api("org.jetbrains.dokka:dokka-gradle-plugin:2.1.0")
    api("com.vanniktech:gradle-maven-publish-plugin:0.33.0")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
