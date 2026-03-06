plugins {
    `java-platform`
    `publish-convention`
}

dependencies {


    constraints {


        api(project(":project:compiler:client:jimmer-client"))

        api(project(":project:jimmer-apt"))

        api(project(":project:compiler:client:jimmer-client-swagger"))

        api(project(":project:compiler:client:jimmer-client-scalar"))

//        api(projects.project.jimmerApt)

//        api(projects.project.jimmerClient)
//        api(projects.project.jimmerClientSwagger)

//        api(projects.project.jimmerClientScalar)

        api(projects.project.jimmerCore)
        api(projects.project.jimmerCoreKotlin)
        api(projects.project.jimmerDtoCompiler)

        api(projects.project.jimmerKsp)
        api(projects.project.jimmerMapstructApt)
        api(projects.project.jimmerSpringBootStarter)
        api(projects.project.jimmerSql)
        api(projects.project.jimmerSqlKotlin)
    }
}
