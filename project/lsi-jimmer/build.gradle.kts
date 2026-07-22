import org.babyfish.jimmer.build.VerifyCompilerArchitecture

plugins {
    `kotlin-publish-convention`
    `dokka-convention`
}

dependencies {
    api(projects.lsiCore)
    testImplementation(libs.kotlin.test)
}

val verifyLsiJimmerArchitecture by tasks.registering(VerifyCompilerArchitecture::class) {
    group = "verification"
    description = "验证 Jimmer LSI 语义扩展不依赖编译器平台与渲染器"

    baseDirectory.set(layout.projectDirectory)
    sourceFiles.from(fileTree("src/main") {
        include("**/*.kt")
    })
}

tasks.named("check") {
    dependsOn(verifyLsiJimmerArchitecture)
}
