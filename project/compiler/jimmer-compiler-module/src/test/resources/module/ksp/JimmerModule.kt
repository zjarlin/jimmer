package demo

import org.babyfish.jimmer.sql.runtime.EntityManager

private class JimmerModule

public val ENTITY_MANAGER: EntityManager = EntityManager.fromResources(
            JimmerModule::class.java.classLoader
        ) {
            it.name.startsWith("demo.")
        }
