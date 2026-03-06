plugins {
    `kotlin-convention`
    alias(libs.plugins.ksp)
}

dependencies {
    api(projects.project.jimmerSql)
    api(projects.project.jimmerSqlKotlin)
    api(project(":project:compiler:client:jimmer-client"))
    api(libs.spring.boot.jdbc)
    api(libs.spring.data.commons)

    compileOnly(libs.spring.boot.starter.web)
    compileOnly(libs.spring.data.redis)
    compileOnly(libs.caffeine)
    compileOnly(libs.spring.graphql)
    compileOnly(libs.jakartaee.api)
    compileOnly(libs.springdoc.openapi.common)

    annotationProcessor(libs.spring.boot.configurationProcessor)

    testAnnotationProcessor(projects.project.jimmerApt)
    testAnnotationProcessor(libs.lombok)

    kspTest(projects.project.jimmerKsp)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.h2)
    testRuntimeOnly(libs.bundles.jackson)
    testRuntimeOnly(project(":project:compiler:client:jimmer-client-swagger"))
}

tasks.processResources {
    inputs.property("swagger", libs.versions.swaggerUi.get())
    filesMatching("application.properties") {
        expand(
            mapOf(
                "swaggerUiVersion" to libs.versions.swaggerUi.get().toString()
            )
        )
    }
}

kotlin {
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}

ksp {
    arg("jimmer.source.excludes", "org.babyfish.jimmer.spring.java")
}
