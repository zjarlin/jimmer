rootProject.name = rootDir.name
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "+"
    //仓库配置
    id("site.addzero.gradle.plugin.repo-buddy") version "+"
    //全自动发现模块
    id("site.addzero.gradle.plugin.modules-buddy") version "+"
    //远程git依赖
//    https://melix.github.io/includegit-gradle-plugin/latest/index.html#_known_limitations
    id("me.champeau.includegit") version "+"
}
gitRepositories {
    include("lsi") {
        uri.set("https://github.com/zjarlin/lsi.git")
        branch.set("main")
    }

    include("ddlgenerator") {
        uri.set("https://github.com/zjarlin/ddlgenerator.git")
        branch.set("main")
    }

}
autoModules {
    excludeModules = arrayOf(":jimmer-jackson")
}
