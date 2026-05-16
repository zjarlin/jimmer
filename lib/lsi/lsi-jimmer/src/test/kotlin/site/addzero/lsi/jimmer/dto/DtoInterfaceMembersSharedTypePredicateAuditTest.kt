package site.addzero.lsi.jimmer.dto

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DtoInterfaceMembersSharedTypePredicateAuditTest {

    @Test
    fun `dto interface member analysis reuses shared type predicates`() {
        val source = locateRepoRoot().resolve(
            "lib/lsi/lsi-jimmer/src/main/kotlin/site/addzero/lsi/jimmer/dto/DtoInterfaceMembers.kt"
        ).readText()

        assertTrue(source.contains("isLsiBooleanLikeQualifiedName"), "DtoInterfaceMembers 必须复用共享 boolean 判定\n$source")
        assertTrue(source.contains("isLsiVoidLikeQualifiedName"), "DtoInterfaceMembers 必须复用共享 void/unit 判定\n$source")
        assertTrue(source.contains("isLsiObjectLikeQualifiedName"), "DtoInterfaceMembers 必须复用共享 object/any 判定\n$source")
        assertFalse(source.contains("typeName == \"java.lang.Object\""), "DtoInterfaceMembers 不得保留本地 object-like 字符串表\n$source")
        assertFalse(source.contains("typeName == \"java.lang.Boolean\""), "DtoInterfaceMembers 不得保留本地 boolean 字符串表\n$source")
        assertFalse(source.contains("typeName == \"java.lang.Void\""), "DtoInterfaceMembers 不得保留本地 void 字符串表\n$source")
    }

    private fun locateRepoRoot(): File {
        var current = File(".").absoluteFile
        while (current.parentFile != null) {
            if (current.resolve("settings.gradle.kts").exists()) {
                return current
            }
            current = current.parentFile
        }
        error("Cannot locate repository root from ${File(".").absoluteFile}")
    }
}
