package site.addzero.lsi.jimmer

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiWorkspace

class ImmutableDraftNamingExtensionsTest {

    @Test
    fun `derives Java Draft names from frozen getter semantics`() {
        val beanProp = prop("bookStore")
        val beanWorkspace = workspace(beanProp, "getBookStore", LsiLanguage.JAVA)
        assertEquals("getBookStore", beanProp.sourceGetterName(beanWorkspace))
        assertEquals("bookStore", beanProp.generatedDraftCodegenName(beanWorkspace))
        assertEquals("SLOT_BOOK_STORE", beanProp.generatedDraftSlotName(beanWorkspace))

        val acronymProp = prop("URL")
        val acronymWorkspace = workspace(acronymProp, "getURL", LsiLanguage.JAVA)
        assertEquals("uRL", acronymProp.generatedDraftCodegenName(acronymWorkspace))
        assertEquals("setURL", acronymProp.generatedJavaDraftSetterName(acronymWorkspace))
        assertEquals("SLOT_U_RL", acronymProp.generatedDraftSlotName(acronymWorkspace))

        val bareProp = prop("URLValue")
        val bareWorkspace = workspace(bareProp, "URLValue", LsiLanguage.JAVA)
        assertEquals("URLValue", bareProp.generatedDraftCodegenName(bareWorkspace))
        assertEquals("setURLValue", bareProp.generatedJavaDraftSetterName(bareWorkspace))
        assertEquals("SLOT_URLVALUE", bareProp.generatedDraftSlotName(bareWorkspace))
    }

    @Test
    fun `only normalizes Java primitive boolean is getters`() {
        val primitiveProp = prop(
            name = "URL",
            type = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
        )
        val primitiveWorkspace = workspace(primitiveProp, "isURL", LsiLanguage.JAVA)
        assertEquals("uRL", primitiveProp.generatedDraftCodegenName(primitiveWorkspace))
        assertEquals("setURL", primitiveProp.generatedJavaDraftSetterName(primitiveWorkspace))
        assertEquals("SLOT_U_RL", primitiveProp.generatedDraftSlotName(primitiveWorkspace))

        val boxedProp = prop(
            name = "URL",
            type = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN, boxed = true),
        )
        val boxedWorkspace = workspace(boxedProp, "isURL", LsiLanguage.JAVA)
        assertEquals("isURL", boxedProp.generatedDraftCodegenName(boxedWorkspace))
        assertEquals("setIsURL", boxedProp.generatedJavaDraftSetterName(boxedWorkspace))
        assertEquals("SLOT_IS_URL", boxedProp.generatedDraftSlotName(boxedWorkspace))

        val lowerCaseProp = prop(
            name = "enabled",
            type = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
        )
        val lowerCaseWorkspace = workspace(lowerCaseProp, "isenabled", LsiLanguage.JAVA)
        assertEquals("isenabled", lowerCaseProp.generatedDraftCodegenName(lowerCaseWorkspace))
        assertEquals("SLOT_ISENABLED", lowerCaseProp.generatedDraftSlotName(lowerCaseWorkspace))
    }

    @Test
    fun `keeps Kotlin names and falls back when declaration is absent`() {
        val kotlinProp = prop("URLValue")
        val kotlinWorkspace = workspace(kotlinProp, "getIgnored", LsiLanguage.KOTLIN)
        assertEquals("getIgnored", kotlinProp.sourceGetterName(kotlinWorkspace))
        assertEquals("URLValue", kotlinProp.generatedDraftCodegenName(kotlinWorkspace))
        assertEquals("SLOT_URLVALUE", kotlinProp.generatedDraftSlotName(kotlinWorkspace))

        val missingProp = prop("urlValue")
        assertEquals("urlValue", missingProp.sourceGetterName(LsiWorkspace()))
        assertEquals("urlValue", missingProp.generatedDraftCodegenName(LsiWorkspace()))
        assertEquals("setUrlValue", missingProp.generatedJavaDraftSetterName(LsiWorkspace()))
        assertEquals("SLOT_URL_VALUE", missingProp.generatedDraftSlotName(LsiWorkspace()))
    }

    private fun prop(
        name: String,
        type: site.addzero.lsi.model.LsiTypeRef = LsiDeclaredType(
            LsiSymbolId.type("java.lang.String"),
        ),
    ): ImmutableProp {
        val id = LsiSymbolId.property(OWNER_TYPE_ID, name)
        return ImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = OWNER_TYPE_ID,
            declaringTypeId = OWNER_TYPE_ID,
            name = name,
            documentation = null,
            type = type,
            annotations = emptyList(),
            overrideChain = emptyList(),
            inherited = false,
            overridden = false,
            nullable = false,
            list = false,
            association = false,
            embedded = false,
            targetTypeId = null,
            primaryMapping = PrimaryMapping.SCALAR,
            primaryAnnotationTypeId = null,
            defaultContract = null,
            associationKind = AssociationKind.NONE,
            formulaKind = FormulaKind.NONE,
            mappedBy = null,
            associationStorage = AssociationStorageKind.NONE,
            transientResolver = null,
            view = null,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = null,
        )
    }

    private fun workspace(
        prop: ImmutableProp,
        getterName: String,
        language: LsiLanguage,
    ): LsiWorkspace {
        val extension = if (language == LsiLanguage.JAVA) "java" else "kt"
        val source = LsiSource.of("src/main/$extension/demo/Book.$extension", language)
        val declaration = LsiProperty(
            id = prop.declarationId,
            name = prop.name,
            ownerId = prop.declaringTypeId,
            type = prop.type,
            getterName = getterName,
            origin = LsiOrigin(LsiOriginKind.SOURCE, source),
        )
        return LsiWorkspace(sources = listOf(source), declarations = listOf(declaration))
    }

    private companion object {
        val OWNER_TYPE_ID = LsiSymbolId.type("demo.Book")
    }
}
