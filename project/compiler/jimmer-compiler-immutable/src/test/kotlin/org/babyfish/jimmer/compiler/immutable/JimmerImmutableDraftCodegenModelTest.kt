package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.jimmer.ImmutableDraftPatternFlag
import site.addzero.lsi.jimmer.ImmutableDraftRuntimePropKind
import site.addzero.lsi.jimmer.ImmutablePropValueCategory
import site.addzero.lsi.jimmer.ImmutableDraftValidationStep
import site.addzero.lsi.jimmer.ImmutablePrecompileException
import site.addzero.lsi.jimmer.toImmutableSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiTypeArgument
import site.addzero.lsi.type.LsiTypeParameter
import site.addzero.lsi.type.LsiTypeParameterRef
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableDraftCodegenModelTest {

    @Test
    fun `freezes accessor storage associated id and validation plans`() {
        val workspace = workspace(activeGetterName = "isActive")
        val schema = workspace.toImmutableSchema()
        val draftSchema = JimmerImmutableDraftCodegenPrecompiler().compile(
            schema,
            workspace,
            JimmerImmutableDraftCodegenOptions.DEFAULT,
        )
        val book = draftSchema.typesById.getValue(BOOK)

        val id = book.propsById.getValue(LsiSymbolId.property(BOOK, "id"))
        assertEquals(0, id.slotIndex)
        assertEquals(JimmerImmutableDraftAccessorStyle.JAVA_BEAN_GET, id.accessorStyle)
        assertEquals("setId", id.javaSetterName)
        assertEquals("getId", id.javaBeanGetterName)
        assertEquals("SLOT_ID", id.slotName)
        assertEquals("applyId", id.javaApplierName)
        assertEquals("addIntoId", id.javaAdderByName)
        assertEquals("__idValue", id.valueFieldName)
        assertEquals("__idLoaded", id.loadedStateFieldName)
        assertEquals(JimmerImmutableDraftValueState.VALUE_AND_LOADED, id.valueState)
        assertEquals(ImmutableDraftRuntimePropKind.ID, id.runtimeProp.kind)
        assertEquals(ImmutablePropValueCategory.SCALAR, id.runtimeProp.valueCategory)
        assertEquals(LsiPrimitiveType(LsiPrimitiveKind.LONG), id.runtimeProp.metadataElementType)

        val active = book.propsById.getValue(LsiSymbolId.property(BOOK, "active"))
        assertEquals(JimmerImmutableDraftAccessorStyle.JAVA_BEAN_IS, active.accessorStyle)
        assertEquals("setActive", active.javaSetterName)
        assertEquals("isActive", active.javaBeanGetterName)
        assertEquals(JimmerImmutableDraftValueState.VALUE_AND_LOADED, active.valueState)

        val title = book.propsById.getValue(LsiSymbolId.property(BOOK, "title"))
        assertEquals(JimmerImmutableDraftAccessorStyle.JAVA_BARE, title.accessorStyle)
        assertEquals("setTitle", title.javaSetterName)
        assertEquals("getTitle", title.javaBeanGetterName)
        assertEquals("Title documentation", title.documentation)
        assertEquals("Title source documentation", title.sourceDocumentation)
        assertEquals(JimmerImmutableDraftValueState.VALUE_AND_LOADED, title.valueState)
        val titleValidation = title.validationPlan.customValidatorSteps.single()
        assertEquals(VALID_BOOK, titleValidation.annotationTypeId)
        assertEquals(listOf(VALIDATOR), titleValidation.validatorTypeIds)
        assertEquals("invalid title", titleValidation.message)
        assertEquals(null, titleValidation.sourceAnnotationUseSiteTarget)
        assertEquals(
            listOf(
                ImmutableDraftValidationStep.NotBlank::class,
                ImmutableDraftValidationStep.Size::class,
                ImmutableDraftValidationStep.Size::class,
                ImmutableDraftValidationStep.Pattern::class,
            ),
            title.validationPlan.builtInSteps.map { step -> step::class },
        )
        assertEquals(null, title.validationPlan.requiredNullCheck)
        val pattern = title.validationPlan.builtInSteps
            .filterIsInstance<ImmutableDraftValidationStep.Pattern>()
            .single()
        assertEquals("[A-Z].+", pattern.regexp)
        assertEquals(listOf(ImmutableDraftPatternFlag.CASE_INSENSITIVE), pattern.flags)
        assertEquals(2, pattern.flags.toJvmPatternFlagMask())
        val patternIndex = title.validationPlan.patternIndexOf(pattern)
        assertEquals("__TITLE_PATTER", title.javaPatternFieldName(patternIndex))
        assertEquals("__TITLE_PATTERN", title.kotlinPatternFieldName(patternIndex))
        assertTrue(pattern.failure.skipWhenNull)
        assertEquals(
            LsiSymbolId.type("jakarta.validation.ValidationException"),
            pattern.failure.exceptionTypeId,
        )
        assertEquals(
            listOf(
                VALID_BOOK,
                LsiSymbolId.type("jakarta.validation.constraints.NotBlank"),
                LsiSymbolId.type("jakarta.validation.constraints.Size"),
                LsiSymbolId.type("jakarta.validation.constraints.Pattern"),
            ),
            title.annotationPlan.builderMethodAnnotations.map(LsiAnnotation::type),
        )
        assertTrue(title.annotationPlan.builderMethodAnnotations.all { annotation ->
            annotation.useSiteTarget == null
        })

        val boxedEnabled = book.propsById.getValue(LsiSymbolId.property(BOOK, "enabled"))
        assertEquals("enabled", boxedEnabled.name)
        assertEquals("isEnabled", boxedEnabled.codegenName)
        assertEquals(JimmerImmutableDraftAccessorStyle.JAVA_BARE, boxedEnabled.accessorStyle)
        assertEquals("setIsEnabled", boxedEnabled.javaSetterName)
        assertEquals("getIsEnabled", boxedEnabled.javaBeanGetterName)
        assertEquals("SLOT_IS_ENABLED", boxedEnabled.slotName)

        val url = book.propsById.getValue(LsiSymbolId.property(BOOK, "URL"))
        assertEquals("URL", url.name)
        assertEquals("uRL", url.codegenName)
        assertEquals("setURL", url.javaSetterName)
        assertEquals("getURL", url.javaBeanGetterName)
        assertEquals("SLOT_U_RL", url.slotName)
        assertNotNull(url.validationPlan.requiredNullCheck)

        val snakeName = book.propsById.getValue(LsiSymbolId.property(BOOK, "first_name"))
        assertEquals("SLOT_FIRST_NAME", snakeName.slotName)

        val author = book.propsById.getValue(LsiSymbolId.property(BOOK, "author"))
        val associatedId = assertNotNull(author.associatedId)
        assertEquals("authorId", associatedId.name)
        assertEquals(LsiSymbolId.property(AUTHOR, "id"), associatedId.targetIdPropId)
        assertTrue(author.autoCreateSupported)
        assertTrue(author.referenceMutationSupported)
        assertEquals(ImmutableDraftRuntimePropKind.KEY_REFERENCE, author.runtimeProp.kind)
        assertEquals(
            ImmutablePropValueCategory.REFERENCE,
            author.runtimeProp.valueCategory,
        )
        assertEquals(MANY_TO_ONE, author.runtimeProp.associationAnnotationTypeId)
        assertEquals(LsiDeclaredType(AUTHOR), author.runtimeProp.metadataElementType)

        assertEquals(listOf(VALIDATOR), book.customValidations.single().validatorTypeIds)
        assertEquals("invalid book", book.customValidations.single().message)
        val javaArtifact = schema.toDraftPoetArtifacts(
            draftSchema = draftSchema,
            types = listOf(book),
            language = LsiLanguage.JAVA,
            workspace = workspace,
        ).single()
        assertTrue(javaArtifact.dependencySymbols.containsAll(setOf(VALID_BOOK, VALIDATOR)))
        assertTrue(VALIDATOR_SOURCE in javaArtifact.dependencySources)
        assertEquals("demo.BookDraft", book.javaDraftQualifiedName())
        assertEquals("demo.ModelsDraft", book.kotlinDraftQualifiedFileName())
        assertEquals(
            listOf(AUTHOR, BASE_ID, BOOK),
            draftSchema.generatedDraftTypes(setOf(BOOK, BASE_ID, AUTHOR)).map { type -> type.typeId },
        )
        val excludedAnnotationSchema = JimmerImmutableDraftCodegenPrecompiler().compile(
            schema,
            workspace,
            JimmerImmutableDraftCodegenOptions.DEFAULT.copy(
                excludedUserAnnotationPrefixes = listOf("demo.Valid"),
            ),
        )
        val excludedTitle = excludedAnnotationSchema.typesById.getValue(BOOK)
            .propsById
            .getValue(LsiSymbolId.property(BOOK, "title"))
        assertTrue(excludedTitle.annotationPlan.builderMethodAnnotations.none { annotation ->
            annotation.type == VALID_BOOK
        })
        assertTrue(excludedTitle.annotationPlan.methodAnnotations.none { annotation ->
            annotation.type == VALID_BOOK
        })
        assertEquals(VALID_BOOK, excludedTitle.validationPlan.customValidatorSteps.single().annotationTypeId)
        assertNotEquals(draftSchema.fingerprint(), excludedAnnotationSchema.fingerprint())
        assertEquals(
            draftSchema.fingerprint(),
            JimmerImmutableDraftCodegenPrecompiler()
                .compile(
                    schema,
                    LsiWorkspace(
                        sources = workspace.sources,
                        declarations = workspace.declarations.reversed(),
                        annotationScopes = workspace.annotationScopes,
                    ),
                    JimmerImmutableDraftCodegenOptions.DEFAULT,
                )
                .fingerprint(),
        )

        val alternateWorkspace = workspace(activeGetterName = "active")
        val alternateSchema = alternateWorkspace.toImmutableSchema()
        val alternateDraftSchema = JimmerImmutableDraftCodegenPrecompiler().compile(
            alternateSchema,
            alternateWorkspace,
            JimmerImmutableDraftCodegenOptions.DEFAULT,
        )
        assertEquals(draftSchema.normalizedSnapshot(), alternateDraftSchema.normalizedSnapshot())
        assertNotEquals(draftSchema.fingerprint(), alternateDraftSchema.fingerprint())

        val getterValidationWorkspace = workspace(
            activeGetterName = "isActive",
            validationUseSiteTarget = LsiAnnotationUseSiteTarget.GETTER,
        )
        val getterValidationSchema = getterValidationWorkspace.toImmutableSchema()
        val getterValidationDraftSchema = JimmerImmutableDraftCodegenPrecompiler().compile(
            getterValidationSchema,
            getterValidationWorkspace,
            JimmerImmutableDraftCodegenOptions.DEFAULT,
        )
        assertEquals(draftSchema.normalizedSnapshot(), getterValidationDraftSchema.normalizedSnapshot())
        assertNotEquals(draftSchema.fingerprint(), getterValidationDraftSchema.fingerprint())
    }

    @Test
    fun `distinguishes immutable references from scalar values and preserves nullable list elements`() {
        val addressCity = LsiSymbolId.property(ADDRESS, "city")
        val address = LsiSymbolId.property(REFERENCE_MODEL, "address")
        val aliases = LsiSymbolId.property(REFERENCE_MODEL, "aliases")
        val workspace = LsiWorkspace(
            sources = listOf(SOURCE),
            declarations = listOf(
                immutableType(
                    id = ADDRESS,
                    memberIds = listOf(addressCity),
                    marker = EMBEDDABLE,
                ),
                property(
                    id = addressCity,
                    ownerId = ADDRESS,
                    type = LsiDeclaredType(STRING),
                    getterName = "city",
                ),
                immutableType(
                    id = REFERENCE_MODEL,
                    memberIds = listOf(address, aliases),
                    marker = IMMUTABLE,
                ),
                property(
                    id = address,
                    ownerId = REFERENCE_MODEL,
                    type = LsiDeclaredType(ADDRESS),
                    getterName = "address",
                ),
                property(
                    id = aliases,
                    ownerId = REFERENCE_MODEL,
                    type = LsiDeclaredType(
                        declarationId = LIST,
                        arguments = listOf(
                            LsiTypeArgument.invariant(
                                LsiDeclaredType(STRING, nullability = LsiNullability.NULLABLE)
                            )
                        ),
                    ),
                    getterName = "aliases",
                ),
            ),
        )
        val schema = workspace.toImmutableSchema()
        val draftSchema = JimmerImmutableDraftCodegenPrecompiler().compile(
            schema,
            workspace,
            JimmerImmutableDraftCodegenOptions.DEFAULT,
        )
        val type = draftSchema.typesById.getValue(REFERENCE_MODEL)

        val addressPlan = type.propsById.getValue(address)
        assertFalse(addressPlan.association)
        assertTrue(addressPlan.immutableReference)
        assertTrue(addressPlan.autoCreateSupported)
        assertTrue(addressPlan.referenceMutationSupported)
        assertEquals(ImmutablePropValueCategory.REFERENCE, addressPlan.runtimeProp.valueCategory)

        val aliasesPlan = type.propsById.getValue(aliases)
        assertFalse(aliasesPlan.association)
        assertFalse(aliasesPlan.immutableReference)
        assertEquals(ImmutablePropValueCategory.SCALAR_LIST, aliasesPlan.runtimeProp.valueCategory)
        assertEquals(LsiNullability.NULLABLE, aliasesPlan.elementType.nullability)
    }

    @Test
    fun `distinguishes generic scalar values from generic associations`() {
        val parameterId = LsiSymbolId.typeParameter(GENERIC_BASE, "T")
        val scalar = LsiSymbolId.property(GENERIC_BASE, "scalar")
        val reference = LsiSymbolId.property(GENERIC_BASE, "reference")
        val workspace = LsiWorkspace(
            sources = listOf(SOURCE),
            declarations = listOf(
                immutableType(
                    id = GENERIC_BASE,
                    memberIds = listOf(scalar, reference),
                    marker = MAPPED_SUPERCLASS,
                    typeParameters = listOf(LsiTypeParameter(parameterId, "T")),
                ),
                property(
                    id = scalar,
                    ownerId = GENERIC_BASE,
                    type = LsiTypeParameterRef(parameterId),
                    getterName = "scalar",
                ),
                property(
                    id = reference,
                    ownerId = GENERIC_BASE,
                    type = LsiTypeParameterRef(parameterId),
                    getterName = "reference",
                    annotations = listOf(LsiAnnotation(MANY_TO_ONE)),
                ),
            ),
        )
        val schema = workspace.toImmutableSchema()
        val draftSchema = JimmerImmutableDraftCodegenPrecompiler().compile(
            schema,
            workspace,
            JimmerImmutableDraftCodegenOptions.DEFAULT,
        )
        val type = draftSchema.typesById.getValue(GENERIC_BASE)

        val scalarPlan = type.propsById.getValue(scalar)
        assertTrue(scalarPlan.genericTarget)
        assertFalse(scalarPlan.association)
        assertFalse(scalarPlan.immutableReference)
        assertFalse(scalarPlan.autoCreateSupported)
        assertEquals(ImmutablePropValueCategory.SCALAR, scalarPlan.runtimeProp.valueCategory)

        val referencePlan = type.propsById.getValue(reference)
        assertTrue(referencePlan.genericTarget)
        assertTrue(referencePlan.association)
        assertTrue(referencePlan.immutableReference)
        assertFalse(referencePlan.autoCreateSupported)
        assertEquals(ImmutablePropValueCategory.REFERENCE, referencePlan.runtimeProp.valueCategory)
    }

    @Test
    fun `rejects invalid built-in validation contracts before rendering`() {
        val workspace = workspace(
            activeGetterName = "isActive",
            titleBuiltIns = listOf(
                builtInValidation(
                    name = "Size",
                    arguments = mapOf(
                        "min" to LsiAnnotationValue.IntValue(10),
                        "max" to LsiAnnotationValue.IntValue(2),
                    ),
                )
            ),
        )
        val schema = workspace.toImmutableSchema()

        val exception = assertFailsWith<ImmutablePrecompileException> {
            JimmerImmutableDraftCodegenPrecompiler().compile(
                schema,
                workspace,
                JimmerImmutableDraftCodegenOptions.DEFAULT,
            )
        }

        assertTrue(exception.message.orEmpty().contains("no valid length"))
    }

    private fun workspace(
        activeGetterName: String,
        validationUseSiteTarget: LsiAnnotationUseSiteTarget? = null,
        titleBuiltIns: List<LsiAnnotation> = defaultTitleBuiltIns(),
    ): LsiWorkspace {
        val baseId = LsiSymbolId.property(BASE_ID, "id")
        val bookId = LsiSymbolId.property(BOOK, "id")
        val active = LsiSymbolId.property(BOOK, "active")
        val title = LsiSymbolId.property(BOOK, "title")
        val boxedEnabled = LsiSymbolId.property(BOOK, "enabled")
        val url = LsiSymbolId.property(BOOK, "URL")
        val snakeName = LsiSymbolId.property(BOOK, "first_name")
        val author = LsiSymbolId.property(BOOK, "author")
        val declarations = listOf(
            annotationType(),
            validatorType(),
            builtInAnnotationType("NotBlank"),
            builtInAnnotationType("Size"),
            builtInAnnotationType("Pattern"),
            immutableType(
                id = BASE_ID,
                memberIds = listOf(baseId),
                marker = MAPPED_SUPERCLASS,
                origin = BASE_ORIGIN,
            ),
            property(
                id = baseId,
                ownerId = BASE_ID,
                type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
                getterName = "getId",
                annotations = listOf(LsiAnnotation(ID)),
                origin = BASE_ORIGIN,
            ),
            immutableType(
                id = AUTHOR,
                memberIds = emptyList(),
                superTypes = listOf(LsiDeclaredType(BASE_ID)),
            ),
            immutableType(
                id = BOOK,
                memberIds = listOf(title, active, boxedEnabled, url, snakeName, author, bookId),
                annotations = listOf(validation("invalid book")),
            ),
            property(
                id = title,
                ownerId = BOOK,
                type = LsiDeclaredType(STRING, nullability = LsiNullability.NULLABLE),
                getterName = "title",
                documentation = "Title documentation",
                sourceDocumentation = "Title source documentation",
                annotations = listOf(
                    validation("invalid title", validationUseSiteTarget),
                ) + titleBuiltIns,
            ),
            property(
                id = active,
                ownerId = BOOK,
                type = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
                getterName = activeGetterName,
            ),
            property(
                id = boxedEnabled,
                ownerId = BOOK,
                type = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN, boxed = true),
                getterName = "isEnabled",
            ),
            property(
                id = url,
                ownerId = BOOK,
                type = LsiDeclaredType(STRING),
                getterName = "getURL",
            ),
            property(
                id = snakeName,
                ownerId = BOOK,
                type = LsiDeclaredType(STRING),
                getterName = "first_name",
            ),
            property(
                id = author,
                ownerId = BOOK,
                type = LsiDeclaredType(AUTHOR),
                getterName = "author",
                annotations = listOf(LsiAnnotation(MANY_TO_ONE), LsiAnnotation(KEY)),
            ),
            property(
                id = bookId,
                ownerId = BOOK,
                type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
                getterName = "getId",
                annotations = listOf(LsiAnnotation(ID)),
            ),
        )
        return LsiWorkspace(
            sources = listOf(SOURCE, BASE_SOURCE, VALIDATOR_SOURCE),
            declarations = declarations,
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        memberIds: List<LsiSymbolId>,
        annotations: List<LsiAnnotation> = emptyList(),
        marker: LsiSymbolId = ENTITY,
        origin: LsiOrigin = ORIGIN,
        superTypes: List<LsiDeclaredType> = emptyList(),
        typeParameters: List<LsiTypeParameter> = emptyList(),
    ): LsiClass {
        val qualifiedName = id.requireTypeQualifiedName()
        return LsiClass(
            id = id,
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.INTERFACE,
            typeParameters = typeParameters,
            superTypes = superTypes,
            memberIds = memberIds,
            annotations = listOf(LsiAnnotation(marker)) + annotations,
            origin = origin,
        )
    }

    private fun property(
        id: LsiSymbolId,
        ownerId: LsiSymbolId,
        type: site.addzero.lsi.type.LsiType,
        getterName: String,
        annotations: List<LsiAnnotation> = emptyList(),
        origin: LsiOrigin = ORIGIN,
        documentation: String? = null,
        sourceDocumentation: String? = null,
    ): LsiProperty {
        return LsiProperty(
            id = id,
            name = id.value.substringAfterLast(':'),
            ownerId = ownerId,
            type = type,
            getterName = getterName,
            annotations = annotations,
            documentation = documentation,
            sourceDocumentation = sourceDocumentation,
            origin = origin,
        )
    }

    private fun annotationType(): LsiClass {
        return LsiClass(
            id = VALID_BOOK,
            name = "ValidBook",
            qualifiedName = "demo.ValidBook",
            kind = LsiTypeDeclarationKind.ANNOTATION,
            annotations = listOf(
                LsiAnnotation(
                    type = JAKARTA_CONSTRAINT,
                    arguments = mapOf(
                        "validatedBy" to LsiAnnotationArgument(
                            value = LsiAnnotationValue.ArrayValue(
                                listOf(
                                    LsiAnnotationValue.ClassValue(LsiDeclaredType(VALIDATOR))
                                )
                            ),
                            origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                        )
                    ),
                )
            ),
            origin = ORIGIN,
        )
    }

    private fun validatorType(): LsiClass {
        return LsiClass(
            id = VALIDATOR,
            name = "ValidBookValidator",
            qualifiedName = "demo.ValidBookValidator",
            kind = LsiTypeDeclarationKind.CLASS,
            origin = VALIDATOR_ORIGIN,
        )
    }

    private fun builtInAnnotationType(name: String): LsiClass {
        val typeId = LsiSymbolId.type("jakarta.validation.constraints.$name")
        return LsiClass(
            id = typeId,
            name = name,
            qualifiedName = typeId.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.ANNOTATION,
            annotations = listOf(
                LsiAnnotation(
                    type = JAKARTA_CONSTRAINT,
                    arguments = mapOf(
                        "validatedBy" to LsiAnnotationArgument(
                            value = LsiAnnotationValue.ArrayValue(emptyList()),
                            origin = LsiAnnotationArgumentOrigin.DEFAULT,
                        )
                    ),
                )
            ),
            origin = ORIGIN,
        )
    }

    private fun validation(
        message: String,
        useSiteTarget: LsiAnnotationUseSiteTarget? = null,
    ): LsiAnnotation {
        return LsiAnnotation(
            type = VALID_BOOK,
            arguments = mapOf(
                "message" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.StringValue(message),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                )
            ),
            useSiteTarget = useSiteTarget,
        )
    }

    private fun builtInValidation(
        name: String,
        arguments: Map<String, LsiAnnotationValue> = emptyMap(),
    ): LsiAnnotation {
        return LsiAnnotation(
            type = LsiSymbolId.type("jakarta.validation.constraints.$name"),
            arguments = buildMap {
                put(
                    "message",
                    LsiAnnotationArgument(
                        value = LsiAnnotationValue.StringValue(
                            "{jakarta.validation.constraints.$name.message}"
                        ),
                        origin = LsiAnnotationArgumentOrigin.DEFAULT,
                    ),
                )
                arguments.forEach { (argumentName, value) ->
                    put(
                        argumentName,
                        LsiAnnotationArgument(
                            value = value,
                            origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                        ),
                    )
                }
            },
        )
    }

    private fun defaultTitleBuiltIns(): List<LsiAnnotation> {
        return listOf(
            builtInValidation("NotBlank"),
            builtInValidation(
                name = "Size",
                arguments = mapOf(
                    "min" to LsiAnnotationValue.IntValue(2),
                    "max" to LsiAnnotationValue.IntValue(64),
                ),
            ),
            builtInValidation(
                name = "Pattern",
                arguments = mapOf(
                    "regexp" to LsiAnnotationValue.StringValue("[A-Z].+"),
                    "flags" to LsiAnnotationValue.ArrayValue(
                        listOf(
                            LsiAnnotationValue.EnumValue(
                                enumType = PATTERN_FLAG,
                                entryName = "CASE_INSENSITIVE",
                            )
                        )
                    ),
                ),
            ),
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/java/demo/Models.java", LsiLanguage.JAVA)
        val BASE_SOURCE = LsiSource.of("src/main/java/demo/BaseId.java", LsiLanguage.JAVA)
        val VALIDATOR_SOURCE = LsiSource.of(
            "src/main/java/demo/ValidBookValidator.java",
            LsiLanguage.JAVA,
        )
        val ORIGIN = LsiOrigin(LsiOriginKind.SOURCE, SOURCE)
        val BASE_ORIGIN = LsiOrigin(LsiOriginKind.SOURCE, BASE_SOURCE)
        val VALIDATOR_ORIGIN = LsiOrigin(LsiOriginKind.SOURCE, VALIDATOR_SOURCE)
        val BASE_ID = LsiSymbolId.type("demo.BaseId")
        val AUTHOR = LsiSymbolId.type("demo.Author")
        val ADDRESS = LsiSymbolId.type("demo.Address")
        val BOOK = LsiSymbolId.type("demo.Book")
        val REFERENCE_MODEL = LsiSymbolId.type("demo.ReferenceModel")
        val GENERIC_BASE = LsiSymbolId.type("demo.GenericBase")
        val VALID_BOOK = LsiSymbolId.type("demo.ValidBook")
        val VALIDATOR = LsiSymbolId.type("demo.ValidBookValidator")
        val STRING = LsiSymbolId.type("java.lang.String")
        val ENTITY = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        val IMMUTABLE = LsiSymbolId.type("org.babyfish.jimmer.Immutable")
        val EMBEDDABLE = LsiSymbolId.type("org.babyfish.jimmer.sql.Embeddable")
        val MAPPED_SUPERCLASS = LsiSymbolId.type("org.babyfish.jimmer.sql.MappedSuperclass")
        val LIST = LsiSymbolId.type("java.util.List")
        val ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")
        val MANY_TO_ONE = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToOne")
        val KEY = LsiSymbolId.type("org.babyfish.jimmer.sql.Key")
        val JAKARTA_CONSTRAINT = LsiSymbolId.type("jakarta.validation.Constraint")
        val PATTERN_FLAG = LsiSymbolId.type("jakarta.validation.constraints.Pattern.Flag")
    }
}
