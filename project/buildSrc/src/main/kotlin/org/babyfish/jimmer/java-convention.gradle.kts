plugins {
    `java-library`
    id("publish-convention")
}

extensions.configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    //gradle 9 + 需要显式声明
    "testRuntimeOnly"(libs.findLibrary("jupiter-platform-launcher").get())
}

tasks.test {
    useJUnitPlatform()
}
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}
tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}
