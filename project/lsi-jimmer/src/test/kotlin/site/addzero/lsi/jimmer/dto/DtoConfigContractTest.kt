package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeHierarchyEntry
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.stableSignature
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace

class DtoConfigContractTest {

    @Test
    fun `exposes nested config implementation type from frozen contracts`() {
        val outerTypeId = LsiSymbolId.type("demo.Filters")
        val implementationTypeId = LsiSymbolId.type("demo.Filters.AuthorFilter")
        val graph = graph(
            implementationTypeId = implementationTypeId,
            kind = DtoConfigContractKind.FILTER,
            targetPackageName = "demo.dto",
        )
        val resolution = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationTypeId = implementationTypeId,
            implementationSuperTypes = listOf(
                declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID)),
            ),
            enclosingTypeId = outerTypeId,
        )
        val prop = graph.props.single() as DtoBaseProp

        assertEquals(
            LsiDeclaredType(declarationId = implementationTypeId),
            prop.configImplementationTypeOrNull(
                graph = graph,
                resolution = resolution,
                kind = DtoConfigContractKind.FILTER,
            ),
        )
        assertNull(
            prop.configImplementationTypeOrNull(
                graph = graph,
                resolution = resolution,
                kind = DtoConfigContractKind.RECURSION,
            ),
        )
    }

    @Test
    fun `rejects config contracts that do not exactly match the frozen property`() {
        val graph = graph(
            implementationTypeId = FILTER_TYPE_ID,
            kind = DtoConfigContractKind.FILTER,
            targetPackageName = "demo.dto",
        )
        val resolution = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationTypeId = FILTER_TYPE_ID,
            implementationSuperTypes = listOf(
                declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID)),
            ),
        )
        val prop = graph.props.single() as DtoBaseProp
        val contract = resolution.contracts.single()

        assertFailsWith<IllegalArgumentException> {
            prop.configImplementationTypeOrNull(
                graph,
                resolution.copy(contracts = emptyList()),
                DtoConfigContractKind.FILTER,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            prop.configImplementationTypeOrNull(
                graph,
                resolution.copy(
                    contracts = listOf(contract.copy(kind = DtoConfigContractKind.RECURSION)),
                ),
                DtoConfigContractKind.RECURSION,
            )
        }
        val wrongImplementationTypeId = LsiSymbolId.type("demo.WrongAuthorFilter")
        assertFailsWith<IllegalArgumentException> {
            prop.configImplementationTypeOrNull(
                graph,
                resolution.copy(
                    contracts = listOf(
                        contract.copy(
                            implementationTypeId = wrongImplementationTypeId,
                            dependencyTypeIds = listOf(AUTHOR_TYPE_ID, wrongImplementationTypeId).sorted(),
                        )
                    ),
                ),
                DtoConfigContractKind.FILTER,
            )
        }
    }

    @Test
    fun `java filter requires exact generated table and freezes canonical dependencies`() {
        val resolution = resolve(
            targetLanguage = LsiLanguage.JAVA,
            implementationSuperTypes = listOf(
                declared(FIELD_FILTER_TYPE_ID, declared(AUTHOR_TABLE_TYPE_ID)),
            ),
            hierarchy = listOf(tableHierarchy(AUTHOR_TABLE_TYPE_ID, AUTHOR_TYPE_ID)),
        )

        assertTrue(resolution.successful)
        val contract = resolution.contracts.single()
        assertEquals(AUTHOR_TYPE_ID, contract.targetEntityTypeId)
        assertEquals(listOf(AUTHOR_TYPE_ID, FILTER_TYPE_ID), contract.dependencyTypeIds)
    }

    @Test
    fun `java filter rejects wrong table even when table entity is correct`() {
        val resolution = resolve(
            targetLanguage = LsiLanguage.JAVA,
            implementationSuperTypes = listOf(
                declared(FIELD_FILTER_TYPE_ID, declared(OTHER_AUTHOR_TABLE_TYPE_ID)),
            ),
            hierarchy = listOf(tableHierarchy(OTHER_AUTHOR_TABLE_TYPE_ID, AUTHOR_TYPE_ID)),
        )

        assertFalse(resolution.successful)
        val diagnostic = resolution.diagnostics.single()
        assertEquals("jimmer.dto.config.target-mismatch", diagnostic.code)
        assertEquals(AUTHOR_TABLE_TYPE_ID.value, diagnostic.details["expectedContractArgumentTypeId"])
        assertEquals(OTHER_AUTHOR_TABLE_TYPE_ID.value, diagnostic.details["actualContractArgumentTypeId"])
        assertEquals(CONFIG_LOCATION, diagnostic.location)
        val snapshot = resolution.normalizedSnapshot()
        assertFalse(AUTHOR_TABLE_TYPE_ID.value in snapshot)
        assertFalse(OTHER_AUTHOR_TABLE_TYPE_ID.value in snapshot)
    }

    @Test
    fun `java and kotlin filter contracts have identical normalized snapshot and fingerprint`() {
        val javaResolution = resolve(
            targetLanguage = LsiLanguage.JAVA,
            implementationSuperTypes = listOf(
                declared(FIELD_FILTER_TYPE_ID, declared(AUTHOR_TABLE_TYPE_ID)),
            ),
            hierarchy = listOf(tableHierarchy(AUTHOR_TABLE_TYPE_ID, AUTHOR_TYPE_ID)),
        )
        val kotlinResolution = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(
                declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID)),
            ),
        )

        assertEquals(javaResolution.normalizedSnapshot(), kotlinResolution.normalizedSnapshot())
        assertEquals(javaResolution.fingerprint(), kotlinResolution.fingerprint())
    }

    @Test
    fun `invalid raw java and kotlin filters have identical semantic fingerprint`() {
        val javaResolution = resolve(
            targetLanguage = LsiLanguage.JAVA,
            implementationSuperTypes = listOf(declared(FIELD_FILTER_TYPE_ID)),
        )
        val kotlinResolution = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID)),
        )

        assertFalse(
            javaResolution.diagnostics.single().details["path"] ==
                kotlinResolution.diagnostics.single().details["path"],
        )
        assertEquals(javaResolution.normalizedSnapshot(), kotlinResolution.normalizedSnapshot())
        assertEquals(javaResolution.fingerprint(), kotlinResolution.fingerprint())
    }

    @Test
    fun `diagnostic input order cannot change normalized fingerprint`() {
        val source = LsiSource.of("demo/src/main/dto/demo/Stable.dto")
        val baseDiagnostic = LsiDiagnostic(
            code = "jimmer.dto.config.stable-order",
            severity = LsiDiagnosticSeverity.INFO,
            message = "stable diagnostic",
            symbolId = FILTER_TYPE_ID,
            location = LsiLocation(
                source = source,
                start = LsiPosition(1, 1),
                end = LsiPosition(1, 2),
            ),
        )
        val diagnostics = listOf(
            baseDiagnostic,
            baseDiagnostic.copy(severity = LsiDiagnosticSeverity.WARNING),
            baseDiagnostic.copy(
                location = LsiLocation(
                    source = LsiSource.of("demo/src/main/dto/demo/Other.dto"),
                    start = LsiPosition(1, 1),
                    end = LsiPosition(1, 2),
                ),
            ),
            baseDiagnostic.copy(location = requireNotNull(baseDiagnostic.location).copy(end = LsiPosition(1, 3))),
        )
        val forward = DtoConfigContractResolution(
            contracts = emptyList(),
            diagnostics = diagnostics.sortedWith(DTO_CONFIG_DIAGNOSTIC_COMPARATOR),
        )
        val reversed = DtoConfigContractResolution(
            contracts = emptyList(),
            diagnostics = diagnostics.reversed().sortedWith(DTO_CONFIG_DIAGNOSTIC_COMPARATOR),
        )

        assertEquals(forward.diagnostics, reversed.diagnostics)
        assertEquals(forward.normalizedSnapshot(), reversed.normalizedSnapshot())
        assertEquals(forward.fingerprint(), reversed.fingerprint())
    }

    @Test
    fun `user generic dependency path participates in normalized fingerprint`() {
        val direct = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(
                declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID)),
            ),
        )
        val baseTypeId = LsiSymbolId.type("demo.AuthorFilterBase")
        val throughBase = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(baseTypeId)),
            hierarchy = listOf(
                hierarchy(
                    baseTypeId,
                    directSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
                ),
            ),
        )

        assertTrue(direct.successful)
        assertTrue(throughBase.successful)
        assertEquals(
            listOf(AUTHOR_TYPE_ID, FILTER_TYPE_ID, baseTypeId).sorted(),
            throughBase.contracts.single().dependencyTypeIds,
        )
        assertFalse(direct.fingerprint() == throughBase.fingerprint())
    }

    @Test
    fun `generic arguments are substituted through remapped intermediate contracts`() {
        val baseTypeId = LsiSymbolId.type("demo.GenericFilterBase")
        val middleTypeId = LsiSymbolId.type("demo.RemappedFilter")
        val baseParameterId = LsiSymbolId.typeParameter(baseTypeId, "E")
        val middleParameterId = LsiSymbolId.typeParameter(middleTypeId, "T")
        val resolution = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(middleTypeId, declared(AUTHOR_TYPE_ID))),
            hierarchy = listOf(
                hierarchy(
                    middleTypeId,
                    typeParameters = listOf(LsiTypeParameter(middleParameterId, "T")),
                    directSuperTypes = listOf(
                        declared(baseTypeId, LsiTypeParameterRef(middleParameterId)),
                    ),
                ),
                hierarchy(
                    baseTypeId,
                    typeParameters = listOf(LsiTypeParameter(baseParameterId, "E")),
                    directSuperTypes = listOf(
                        declared(K_FIELD_FILTER_TYPE_ID, LsiTypeParameterRef(baseParameterId)),
                    ),
                ),
            ),
        )

        assertTrue(resolution.successful)
        assertEquals(AUTHOR_TYPE_ID, resolution.contracts.single().targetEntityTypeId)
        assertEquals(
            listOf(AUTHOR_TYPE_ID, FILTER_TYPE_ID, baseTypeId, middleTypeId).sorted(),
            resolution.contracts.single().dependencyTypeIds,
        )
    }

    @Test
    fun `identical diamond collapses deterministically while conflicting diamond fails`() {
        val leftTypeId = LsiSymbolId.type("demo.LeftFilter")
        val rightTypeId = LsiSymbolId.type("demo.RightFilter")
        val identicalHierarchy = listOf(
            hierarchy(leftTypeId, directSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID)))),
            hierarchy(rightTypeId, directSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID)))),
        )
        val first = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(leftTypeId), declared(rightTypeId)),
            hierarchy = identicalHierarchy,
        )
        val reversed = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(rightTypeId), declared(leftTypeId)),
            hierarchy = identicalHierarchy.reversed(),
        )

        assertTrue(first.successful)
        assertEquals(first, reversed)
        assertEquals(
            listOf(AUTHOR_TYPE_ID, FILTER_TYPE_ID, leftTypeId, rightTypeId).sorted(),
            first.contracts.single().dependencyTypeIds,
        )

        val conflicting = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(leftTypeId), declared(rightTypeId)),
            hierarchy = listOf(
                hierarchy(
                    leftTypeId,
                    directSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
                ),
                hierarchy(
                    rightTypeId,
                    directSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(BOOK_TYPE_ID))),
                ),
            ),
        )

        assertEquals("jimmer.dto.config.contract-ambiguous", conflicting.diagnostics.single().code)
    }

    @Test
    fun `raw and residual generic contracts have stable diagnostics`() {
        val raw = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID)),
        )
        assertEquals("jimmer.dto.config.raw-contract", raw.diagnostics.single().code)

        val genericBaseTypeId = LsiSymbolId.type("demo.GenericFilterBase")
        val parameterId = LsiSymbolId.typeParameter(genericBaseTypeId, "E")
        val residual = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(genericBaseTypeId)),
            hierarchy = listOf(
                hierarchy(
                    genericBaseTypeId,
                    typeParameters = listOf(LsiTypeParameter(parameterId, "E")),
                    directSuperTypes = listOf(
                        declared(K_FIELD_FILTER_TYPE_ID, LsiTypeParameterRef(parameterId)),
                    ),
                ),
            ),
        )
        val diagnostic = residual.diagnostics.single()
        assertEquals("jimmer.dto.config.argument-unresolved", diagnostic.code)
        assertTrue(diagnostic.details.getValue("reason").startsWith("residual-type-parameter:"))
    }

    @Test
    fun `nested construction only rejects declarations requiring enclosing instance`() {
        val enclosingTypeId = LsiSymbolId.type("demo.Filters")
        val nested = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            enclosingTypeId = enclosingTypeId,
            requiresEnclosingInstance = false,
        )
        assertTrue(nested.successful)

        val inner = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            enclosingTypeId = enclosingTypeId,
            requiresEnclosingInstance = true,
        )
        val diagnostic = inner.diagnostics.single()
        assertEquals("jimmer.dto.config.not-instantiable", diagnostic.code)
        assertEquals("enclosing-instance-required", diagnostic.details["reason"])
    }

    @Test
    fun `kotlin internal construction accepts current source and rejects binary dependency`() {
        val sourceInternal = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            visibility = LsiVisibility.INTERNAL,
            origin = SOURCE_ORIGIN,
        )
        assertTrue(sourceInternal.successful)

        val binaryInternal = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            visibility = LsiVisibility.INTERNAL,
            origin = BINARY_ORIGIN,
        )
        val diagnostic = binaryInternal.diagnostics.single()
        assertEquals("jimmer.dto.config.not-instantiable", diagnostic.code)
        assertEquals("implementation-visibility:INTERNAL", diagnostic.details["reason"])
    }

    @Test
    fun `recursion contract validates canonical target entity`() {
        val success = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            kind = DtoConfigContractKind.RECURSION,
            implementationSuperTypes = listOf(
                declared(RECURSION_STRATEGY_TYPE_ID, declared(AUTHOR_TYPE_ID)),
            ),
        )
        assertTrue(success.successful)
        assertEquals(AUTHOR_TYPE_ID, success.contracts.single().targetEntityTypeId)

        val mismatch = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            kind = DtoConfigContractKind.RECURSION,
            implementationSuperTypes = listOf(
                declared(RECURSION_STRATEGY_TYPE_ID, declared(BOOK_TYPE_ID)),
            ),
        )
        assertEquals("jimmer.dto.config.target-mismatch", mismatch.diagnostics.single().code)
        assertEquals(BOOK_TYPE_ID.value, mismatch.diagnostics.single().details["actualTargetTypeId"])
    }

    @Test
    fun `abstract and missing zero argument construction are rejected`() {
        val abstract = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            abstractDeclaration = true,
        )
        assertEquals("abstract-declaration", abstract.diagnostics.single().details["reason"])

        val parameterized = resolve(
            targetLanguage = LsiLanguage.JAVA,
            implementationSuperTypes = listOf(
                declared(FIELD_FILTER_TYPE_ID, declared(AUTHOR_TABLE_TYPE_ID)),
            ),
            hierarchy = listOf(tableHierarchy(AUTHOR_TABLE_TYPE_ID, AUTHOR_TYPE_ID)),
            constructorShapes = listOf(ConstructorShape(parameterCount = 1)),
        )
        assertEquals("zero-argument-constructor-missing", parameterized.diagnostics.single().details["reason"])
    }

    @Test
    fun `kotlin constructor with all default parameters supports zero argument call`() {
        val resolution = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            constructorShapes = listOf(
                ConstructorShape(
                    parameterCount = 2,
                    defaultParameterIndexes = setOf(0, 1),
                ),
            ),
        )

        assertTrue(resolution.successful)
    }

    @Test
    fun `vararg constructors support zero argument calls on both languages`() {
        val java = resolve(
            targetLanguage = LsiLanguage.JAVA,
            implementationSuperTypes = listOf(
                declared(FIELD_FILTER_TYPE_ID, declared(AUTHOR_TABLE_TYPE_ID)),
            ),
            hierarchy = listOf(tableHierarchy(AUTHOR_TABLE_TYPE_ID, AUTHOR_TYPE_ID)),
            constructorShapes = listOf(
                ConstructorShape(parameterCount = 1, varargParameterIndexes = setOf(0)),
            ),
        )
        assertTrue(java.successful)

        val kotlin = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            constructorShapes = listOf(
                ConstructorShape(
                    parameterCount = 2,
                    defaultParameterIndexes = setOf(0),
                    varargParameterIndexes = setOf(1),
                ),
            ),
        )
        assertTrue(kotlin.successful)
    }

    @Test
    fun `exact zero constructor wins and ambiguous optional overloads fail`() {
        val exactWins = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            constructorShapes = listOf(
                ConstructorShape(),
                ConstructorShape(parameterCount = 1, defaultParameterIndexes = setOf(0)),
            ),
        )
        assertTrue(exactWins.successful)

        val javaAmbiguous = resolve(
            targetLanguage = LsiLanguage.JAVA,
            implementationSuperTypes = listOf(
                declared(FIELD_FILTER_TYPE_ID, declared(AUTHOR_TABLE_TYPE_ID)),
            ),
            hierarchy = listOf(tableHierarchy(AUTHOR_TABLE_TYPE_ID, AUTHOR_TYPE_ID)),
            constructorShapes = listOf(
                ConstructorShape(parameterCount = 1, varargParameterIndexes = setOf(0)),
                ConstructorShape(parameterCount = 1, varargParameterIndexes = setOf(0)),
            ),
        )
        assertEquals("jimmer.dto.config.constructor-ambiguous", javaAmbiguous.diagnostics.single().code)
        assertTrue(javaAmbiguous.diagnostics.single().details.getValue("candidateConstructorIds").contains(','))

        val kotlinPreferred = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            constructorShapes = listOf(
                ConstructorShape(parameterCount = 1, defaultParameterIndexes = setOf(0)),
                ConstructorShape(parameterCount = 2, defaultParameterIndexes = setOf(0, 1)),
            ),
        )
        assertTrue(kotlinPreferred.successful)

        val defaultsPreferredOverVararg = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            constructorShapes = listOf(
                ConstructorShape(parameterCount = 2, defaultParameterIndexes = setOf(0, 1)),
                ConstructorShape(parameterCount = 1, varargParameterIndexes = setOf(0)),
            ),
        )
        assertTrue(defaultsPreferredOverVararg.successful)

        val pureVarargPreferred = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            constructorShapes = listOf(
                ConstructorShape(parameterCount = 1, varargParameterIndexes = setOf(0)),
                ConstructorShape(
                    parameterCount = 2,
                    defaultParameterIndexes = setOf(0),
                    varargParameterIndexes = setOf(1),
                ),
            ),
        )
        assertTrue(pureVarargPreferred.successful)

        val kotlinAmbiguous = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            constructorShapes = listOf(
                ConstructorShape(parameterCount = 1, defaultParameterIndexes = setOf(0)),
                ConstructorShape(parameterCount = 1, defaultParameterIndexes = setOf(0)),
            ),
        )
        assertEquals("jimmer.dto.config.constructor-ambiguous", kotlinAmbiguous.diagnostics.single().code)
    }

    @Test
    fun `java chooses the most specific zero-call vararg constructor`() {
        val objectTypeId = LsiSymbolId.type("java.lang.Object")
        val stringTypeId = LsiSymbolId.type("java.lang.String")
        val resolution = resolve(
            targetLanguage = LsiLanguage.JAVA,
            implementationSuperTypes = listOf(
                declared(FIELD_FILTER_TYPE_ID, declared(AUTHOR_TABLE_TYPE_ID)),
            ),
            hierarchy = listOf(
                tableHierarchy(AUTHOR_TABLE_TYPE_ID, AUTHOR_TYPE_ID),
                hierarchy(stringTypeId, directSuperTypes = listOf(declared(objectTypeId))),
            ),
            constructorShapes = listOf(
                ConstructorShape(
                    parameterCount = 1,
                    varargParameterIndexes = setOf(0),
                    parameterTypes = listOf(LsiArrayType(declared(objectTypeId))),
                ),
                ConstructorShape(
                    parameterCount = 1,
                    varargParameterIndexes = setOf(0),
                    parameterTypes = listOf(LsiArrayType(declared(stringTypeId))),
                ),
            ),
        )

        assertTrue(resolution.successful)
    }

    @Test
    fun `java rejects checked constructor exceptions and permits unchecked exceptions`() {
        val checked = resolve(
            targetLanguage = LsiLanguage.JAVA,
            implementationSuperTypes = listOf(
                declared(FIELD_FILTER_TYPE_ID, declared(AUTHOR_TABLE_TYPE_ID)),
            ),
            hierarchy = listOf(tableHierarchy(AUTHOR_TABLE_TYPE_ID, AUTHOR_TYPE_ID)),
            constructorShapes = listOf(
                ConstructorShape(thrownTypes = listOf(declared(LsiSymbolId.type("java.io.IOException")))),
            ),
        )
        val checkedDiagnostic = checked.diagnostics.single()
        assertEquals("jimmer.dto.config.not-instantiable", checkedDiagnostic.code)
        assertEquals("checked-constructor-exception", checkedDiagnostic.details["reason"])
        assertTrue(checkedDiagnostic.details.getValue("checkedThrownTypes").contains("java.io.IOException"))

        val unchecked = resolve(
            targetLanguage = LsiLanguage.JAVA,
            implementationSuperTypes = listOf(
                declared(FIELD_FILTER_TYPE_ID, declared(AUTHOR_TABLE_TYPE_ID)),
            ),
            hierarchy = listOf(tableHierarchy(AUTHOR_TABLE_TYPE_ID, AUTHOR_TYPE_ID)),
            constructorShapes = listOf(
                ConstructorShape(
                    thrownTypes = listOf(declared(LsiSymbolId.type("java.lang.RuntimeException"))),
                ),
            ),
        )
        assertTrue(unchecked.successful)
    }

    @Test
    fun `unresolved constructor types defer config resolution`() {
        val resolution = resolve(
            targetLanguage = LsiLanguage.JAVA,
            implementationSuperTypes = listOf(
                declared(FIELD_FILTER_TYPE_ID, declared(AUTHOR_TABLE_TYPE_ID)),
            ),
            hierarchy = listOf(tableHierarchy(AUTHOR_TABLE_TYPE_ID, AUTHOR_TYPE_ID)),
            constructorShapes = listOf(
                ConstructorShape(thrownTypes = listOf(LsiUnresolvedType("demo.GeneratedException"))),
            ),
        )

        assertFalse(resolution.successful)
        assertTrue(resolution.diagnostics.isEmpty())
        assertEquals(listOf(FILTER_TYPE_ID), resolution.unresolvedTypeIds)
    }

    @Test
    fun `unresolved contract argument defers config resolution`() {
        val resolution = resolve(
            targetLanguage = LsiLanguage.JAVA,
            implementationSuperTypes = listOf(
                LsiDeclaredType(
                    declarationId = FIELD_FILTER_TYPE_ID,
                    arguments = listOf(LsiTypeArgument.invariant(LsiUnresolvedType("demo.AuthorTable"))),
                ),
            ),
        )

        assertFalse(resolution.successful)
        assertTrue(resolution.diagnostics.isEmpty())
        assertEquals(listOf(FILTER_TYPE_ID), resolution.unresolvedTypeIds)
    }

    @Test
    fun `java accepts zero component record implementation`() {
        val resolution = resolve(
            targetLanguage = LsiLanguage.JAVA,
            implementationKind = LsiTypeDeclarationKind.RECORD,
            implementationSuperTypes = listOf(
                declared(FIELD_FILTER_TYPE_ID, declared(AUTHOR_TABLE_TYPE_ID)),
            ),
            hierarchy = listOf(tableHierarchy(AUTHOR_TABLE_TYPE_ID, AUTHOR_TYPE_ID)),
        )

        assertTrue(resolution.successful)
    }

    @Test
    fun `cyclic hierarchy is reported deterministically`() {
        val leftTypeId = LsiSymbolId.type("demo.CycleLeft")
        val rightTypeId = LsiSymbolId.type("demo.CycleRight")
        val resolution = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(declared(leftTypeId)),
            hierarchy = listOf(
                hierarchy(leftTypeId, directSuperTypes = listOf(declared(rightTypeId))),
                hierarchy(rightTypeId, directSuperTypes = listOf(declared(leftTypeId))),
            ),
        )

        val diagnostic = resolution.diagnostics.single()
        assertEquals("jimmer.dto.config.cyclic-hierarchy", diagnostic.code)
        assertTrue(diagnostic.details.getValue("path").contains(leftTypeId.value))
        assertTrue(diagnostic.details.getValue("path").contains(rightTypeId.value))
    }

    @Test
    fun `static nested package private implementation uses real package`() {
        val outerTypeId = LsiSymbolId.type("demo.Filters")
        val nestedFilterTypeId = LsiSymbolId.type("demo.Filters.AuthorFilter")
        val resolution = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationTypeId = nestedFilterTypeId,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            enclosingTypeId = outerTypeId,
            requiresEnclosingInstance = false,
            visibility = LsiVisibility.PACKAGE_PRIVATE,
            targetPackageName = "demo",
        )

        assertTrue(resolution.successful)
    }

    @Test
    fun `protected construction follows Java package access only`() {
        val outerTypeId = LsiSymbolId.type("demo.Filters")
        val nestedFilterTypeId = LsiSymbolId.type("demo.Filters.AuthorFilter")
        listOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN).forEach { targetLanguage ->
            val javaSamePackage = resolve(
                targetLanguage = targetLanguage,
                implementationTypeId = nestedFilterTypeId,
                implementationSuperTypes = if (targetLanguage == LsiLanguage.JAVA) {
                    listOf(declared(FIELD_FILTER_TYPE_ID, declared(AUTHOR_TABLE_TYPE_ID)))
                } else {
                    listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID)))
                },
                hierarchy = if (targetLanguage == LsiLanguage.JAVA) {
                    listOf(tableHierarchy(AUTHOR_TABLE_TYPE_ID, AUTHOR_TYPE_ID))
                } else {
                    emptyList()
                },
                enclosingTypeId = outerTypeId,
                visibility = LsiVisibility.PROTECTED,
                origin = JAVA_SOURCE_ORIGIN,
                targetPackageName = "demo",
            )
            assertTrue(javaSamePackage.successful, targetLanguage.name)

            val javaCrossPackage = resolve(
                targetLanguage = targetLanguage,
                implementationTypeId = nestedFilterTypeId,
                implementationSuperTypes = if (targetLanguage == LsiLanguage.JAVA) {
                    listOf(declared(FIELD_FILTER_TYPE_ID, declared(AUTHOR_TABLE_TYPE_ID)))
                } else {
                    listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID)))
                },
                hierarchy = if (targetLanguage == LsiLanguage.JAVA) {
                    listOf(tableHierarchy(AUTHOR_TABLE_TYPE_ID, AUTHOR_TYPE_ID))
                } else {
                    emptyList()
                },
                enclosingTypeId = outerTypeId,
                visibility = LsiVisibility.PROTECTED,
                origin = JAVA_SOURCE_ORIGIN,
                targetPackageName = "demo.dto",
            )
            assertEquals(
                "implementation-visibility:PROTECTED",
                javaCrossPackage.diagnostics.single().details["reason"],
            )
        }

        val kotlinProtected = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationTypeId = nestedFilterTypeId,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            enclosingTypeId = outerTypeId,
            visibility = LsiVisibility.PROTECTED,
            origin = SOURCE_ORIGIN,
            targetPackageName = "demo",
        )
        assertEquals(
            "implementation-visibility:PROTECTED",
            kotlinProtected.diagnostics.single().details["reason"],
        )
    }

    @Test
    fun `private enclosing type makes nested implementation inaccessible`() {
        val outerTypeId = LsiSymbolId.type("demo.Filters")
        val nestedFilterTypeId = LsiSymbolId.type("demo.Filters.AuthorFilter")
        val resolution = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationTypeId = nestedFilterTypeId,
            implementationSuperTypes = listOf(declared(K_FIELD_FILTER_TYPE_ID, declared(AUTHOR_TYPE_ID))),
            enclosingTypeId = outerTypeId,
            enclosingVisibility = LsiVisibility.PRIVATE,
            targetPackageName = "demo",
        )

        assertEquals(
            "enclosing-visibility:${outerTypeId.value}:PRIVATE",
            resolution.diagnostics.single().details["reason"],
        )
    }

    @Test
    fun `generic implementation is rejected before hierarchy traversal`() {
        val parameterId = LsiSymbolId.typeParameter(FILTER_TYPE_ID, "E")
        val resolution = resolve(
            targetLanguage = LsiLanguage.KOTLIN,
            implementationSuperTypes = listOf(
                declared(K_FIELD_FILTER_TYPE_ID, LsiTypeParameterRef(parameterId)),
            ),
            implementationTypeParameters = listOf(LsiTypeParameter(parameterId, "E")),
        )

        assertEquals("jimmer.dto.config.generic-implementation", resolution.diagnostics.single().code)
    }

    private fun resolve(
        targetLanguage: LsiLanguage,
        kind: DtoConfigContractKind = DtoConfigContractKind.FILTER,
        implementationTypeId: LsiSymbolId = FILTER_TYPE_ID,
        implementationKind: LsiTypeDeclarationKind = LsiTypeDeclarationKind.CLASS,
        implementationSuperTypes: List<LsiDeclaredType>,
        hierarchy: List<LsiTypeHierarchyEntry> = emptyList(),
        implementationTypeParameters: List<LsiTypeParameter> = emptyList(),
        enclosingTypeId: LsiSymbolId? = null,
        enclosingVisibility: LsiVisibility = LsiVisibility.PUBLIC,
        requiresEnclosingInstance: Boolean = false,
        visibility: LsiVisibility = LsiVisibility.PUBLIC,
        modality: LsiModality = LsiModality.FINAL,
        abstractDeclaration: Boolean = false,
        origin: LsiOrigin = SOURCE_ORIGIN,
        constructorShapes: List<ConstructorShape> = listOf(ConstructorShape()),
        targetPackageName: String = "demo.dto",
    ): DtoConfigContractResolution {
        val constructors = constructorShapes.mapIndexed { constructorIndex, shape ->
            require(shape.parameterTypes.isEmpty() || shape.parameterTypes.size == shape.parameterCount)
            val parameterTypes = List(shape.parameterCount) { parameterIndex ->
                shape.parameterTypes.getOrNull(parameterIndex)
                    ?: declared(LsiSymbolId.type("demo.Constructor${constructorIndex}Arg$parameterIndex"))
            }
            val parameterTypeSignatures = parameterTypes.map(LsiTypeRef::stableSignature)
            val constructorId = LsiSymbolId.constructor(implementationTypeId, parameterTypeSignatures)
            val constructorParameters = List(shape.parameterCount) { parameterIndex ->
                LsiParameter(
                    id = LsiSymbolId.parameter(constructorId, parameterIndex, "value$parameterIndex"),
                    name = "value$parameterIndex",
                    callableId = constructorId,
                    index = parameterIndex,
                    type = parameterTypes[parameterIndex],
                    vararg = parameterIndex in shape.varargParameterIndexes,
                    hasDefault = parameterIndex in shape.defaultParameterIndexes,
                    origin = origin,
                )
            }
            LsiConstructor(
                id = constructorId,
                ownerId = implementationTypeId,
                parameters = constructorParameters,
                thrownTypes = shape.thrownTypes,
                visibility = visibility,
                origin = origin,
            )
        }
        val implementation = LsiTypeDeclaration(
            id = implementationTypeId,
            name = implementationTypeId.requireTypeQualifiedName().substringAfterLast('.'),
            qualifiedName = implementationTypeId.requireTypeQualifiedName(),
            kind = implementationKind,
            enclosingTypeId = enclosingTypeId,
            requiresEnclosingInstance = requiresEnclosingInstance,
            visibility = visibility,
            modality = modality,
            abstractDeclaration = abstractDeclaration,
            typeParameters = implementationTypeParameters,
            superTypes = implementationSuperTypes,
            memberIds = constructors.map(LsiConstructor::id),
            origin = origin,
        )
        val enclosingDeclaration = enclosingTypeId?.let { typeId ->
            LsiTypeDeclaration(
                id = typeId,
                name = typeId.requireTypeQualifiedName().substringAfterLast('.'),
                qualifiedName = typeId.requireTypeQualifiedName(),
                kind = LsiTypeDeclarationKind.CLASS,
                visibility = enclosingVisibility,
                origin = origin,
            )
        }
        val workspace = LsiWorkspace(
            declarations = listOfNotNull(enclosingDeclaration, implementation) + constructors,
            typeHierarchy = hierarchy,
        )
        return workspace.resolveDtoConfigContracts(
            graph = graph(implementationTypeId, kind, targetPackageName),
            immutableSchema = IMMUTABLE_SCHEMA,
            targetLanguage = targetLanguage,
        )
    }

    private data class ConstructorShape(
        val parameterCount: Int = 0,
        val defaultParameterIndexes: Set<Int> = emptySet(),
        val varargParameterIndexes: Set<Int> = emptySet(),
        val parameterTypes: List<LsiTypeRef> = emptyList(),
        val thrownTypes: List<LsiTypeRef> = emptyList(),
    )

    private fun graph(
        implementationTypeId: LsiSymbolId,
        kind: DtoConfigContractKind,
        targetPackageName: String,
    ): DtoGraph {
        val type = GRAPH.types.single().copy(packageName = targetPackageName)
        val prop = (GRAPH.props.single() as DtoBaseProp).let { baseProp ->
            baseProp.copy(
                config = requireNotNull(baseProp.config).copy(
                    filter = if (kind == DtoConfigContractKind.FILTER) {
                        DtoConfigTypeRef(implementationTypeId, CONFIG_LOCATION)
                    } else {
                        null
                    },
                    recursion = if (kind == DtoConfigContractKind.RECURSION) {
                        DtoConfigTypeRef(implementationTypeId, CONFIG_LOCATION)
                    } else {
                        null
                    },
                ),
                recursive = kind == DtoConfigContractKind.RECURSION,
            )
        }
        return GRAPH.copy(types = listOf(type), props = listOf(prop))
    }

    companion object {
        private val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        private val AUTHOR_TYPE_ID = LsiSymbolId.type("demo.Author")
        private val FILTER_TYPE_ID = LsiSymbolId.type("demo.AuthorFilter")
        private val AUTHOR_TABLE_TYPE_ID = LsiSymbolId.type("demo.AuthorTable")
        private val OTHER_AUTHOR_TABLE_TYPE_ID = LsiSymbolId.type("demo.OtherAuthorTable")
        private val FIELD_FILTER_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.FieldFilter")
        private val K_FIELD_FILTER_TYPE_ID =
            LsiSymbolId.type("org.babyfish.jimmer.sql.kt.fetcher.KFieldFilter")
        private val RECURSION_STRATEGY_TYPE_ID =
            LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.RecursionStrategy")
        private val TABLE_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.Table")
        private val ID_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")
        private val AUTHORS_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "authors")
        private val DTO_TYPE_ID = DtoTypeId("dto#BookView")
        private val DTO_PROP_ID = DtoPropId("dto#BookView/authors")
        private val DTO_SOURCE = LsiSource.of("demo/src/main/dto/demo/Book.dto")
        private val CONFIG_LOCATION = LsiLocation(DTO_SOURCE, LsiPosition(5, 17))
        private val SOURCE_ORIGIN = LsiOrigin(
            kind = LsiOriginKind.SOURCE,
            source = LsiSource.of("demo/src/main/kotlin/demo/AuthorFilter.kt", LsiLanguage.KOTLIN),
        )
        private val JAVA_SOURCE_ORIGIN = LsiOrigin(
            kind = LsiOriginKind.SOURCE,
            source = LsiSource.of("demo/src/main/java/demo/Filters.java", LsiLanguage.JAVA),
        )
        private val BINARY_ORIGIN = LsiOrigin(
            kind = LsiOriginKind.BINARY,
            source = LsiSource.of(
                "libs/config.jar!/demo/AuthorFilter.class",
                LsiLanguage.KOTLIN,
                LsiSourceKind.BINARY,
            ),
        )
        private val GRAPH = DtoGraph(
            source = DTO_SOURCE,
            rootTypeIds = listOf(DTO_TYPE_ID),
            types = listOf(
                DtoType(
                    id = DTO_TYPE_ID,
                    baseTypeId = BOOK_TYPE_ID,
                    packageName = "demo.dto",
                    name = "BookView",
                    modifiers = emptySet(),
                    annotations = emptyList(),
                    superInterfaces = emptyList(),
                    documentation = null,
                    location = LsiLocation(DTO_SOURCE, LsiPosition(1, 1)),
                    focusedRecursion = false,
                    propIds = listOf(DTO_PROP_ID),
                    hiddenFlatPropIds = emptyList(),
                    polymorphism = null,
                ),
            ),
            props = listOf(
                DtoBaseProp(
                    id = DTO_PROP_ID,
                    ownerTypeId = DTO_TYPE_ID,
                    name = "authors",
                    alias = null,
                    nullable = false,
                    annotations = emptyList(),
                    documentation = null,
                    aliasLocation = LsiLocation(DTO_SOURCE, LsiPosition(4, 5)),
                    baseLocation = LsiLocation(DTO_SOURCE, LsiPosition(4, 5)),
                    baseProps = listOf(DtoBasePropBinding("authors", AUTHORS_PROP_ID)),
                    basePath = "authors",
                    nextPropId = null,
                    tailPropId = DTO_PROP_ID,
                    baseNullable = false,
                    inputModifier = DtoModifier.STATIC,
                    functionName = null,
                    targetTypeId = null,
                    enumType = null,
                    config = DtoPropConfig(
                        predicate = null,
                        orderItems = emptyList(),
                        filter = DtoConfigTypeRef(FILTER_TYPE_ID, CONFIG_LOCATION),
                        recursion = null,
                        fetchType = DtoFetchType.AUTO,
                        limit = null,
                        batch = null,
                        depth = null,
                    ),
                    recursive = false,
                    likeOptions = emptySet(),
                ),
            ),
        )
        private val IMMUTABLE_SCHEMA = ImmutableSchema(
            listOf(
                immutableType(
                    BOOK_TYPE_ID,
                    listOf(
                        immutableProp(
                            ownerTypeId = BOOK_TYPE_ID,
                            name = "authors",
                            targetTypeId = AUTHOR_TYPE_ID,
                        ),
                    ),
                ),
                immutableType(AUTHOR_TYPE_ID, emptyList()),
            ),
        )

        private fun declared(
            typeId: LsiSymbolId,
            vararg arguments: site.addzero.lsi.model.LsiTypeRef,
        ): LsiDeclaredType {
            return LsiDeclaredType(
                declarationId = typeId,
                arguments = arguments.map(LsiTypeArgument::invariant),
            )
        }

        private fun hierarchy(
            typeId: LsiSymbolId,
            typeParameters: List<LsiTypeParameter> = emptyList(),
            directSuperTypes: List<LsiDeclaredType> = emptyList(),
        ): LsiTypeHierarchyEntry {
            return LsiTypeHierarchyEntry(
                id = typeId,
                qualifiedName = typeId.requireTypeQualifiedName(),
                kind = LsiTypeDeclarationKind.CLASS,
                typeParameters = typeParameters,
                directSuperTypes = directSuperTypes,
            )
        }

        private fun tableHierarchy(
            tableTypeId: LsiSymbolId,
            entityTypeId: LsiSymbolId,
        ): LsiTypeHierarchyEntry {
            return hierarchy(
                typeId = tableTypeId,
                directSuperTypes = listOf(declared(TABLE_TYPE_ID, declared(entityTypeId))),
            )
        }

        private fun immutableType(
            typeId: LsiSymbolId,
            props: List<ImmutableProp>,
        ): ImmutableType {
            val completeProps = listOf(immutableIdProp(typeId)) + props
            return ImmutableType(
                id = typeId,
                qualifiedName = typeId.requireTypeQualifiedName(),
                kind = ImmutableTypeKind.ENTITY,
                documentation = null,
                annotations = emptyList(),
                typeParameterIds = emptyList(),
                superTypeIds = emptyList(),
                props = completeProps,
                primarySuperTypeId = null,
                inheritanceRootTypeId = null,
                inheritanceStrategy = null,
                joinedTableDissociateAction = null,
                instantiable = true,
                discriminatorValue = null,
                discriminatorPropId = null,
                idPropId = completeProps.singleOrNull { prop ->
                    prop.primaryMapping == PrimaryMapping.ID
                }?.id,
                versionPropId = completeProps.singleOrNull { prop ->
                    prop.primaryMapping == PrimaryMapping.VERSION
                }?.id,
                logicalDeletedPropId = completeProps.singleOrNull { prop ->
                    prop.primaryMapping == PrimaryMapping.LOGICAL_DELETED
                }?.id,
                acrossMicroServices = false,
                microServiceName = "",
            )
        }

        private fun immutableIdProp(ownerTypeId: LsiSymbolId): ImmutableProp {
            val propId = LsiSymbolId.property(ownerTypeId, "id")
            return ImmutableProp(
                id = propId,
                declarationId = propId,
                ownerTypeId = ownerTypeId,
                declaringTypeId = ownerTypeId,
                name = "id",
                documentation = null,
                type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
                annotations = listOf(LsiAnnotation(ID_ANNOTATION_TYPE_ID)),
                overrideChain = listOf(propId),
                inherited = false,
                overridden = false,
                nullable = false,
                list = false,
                association = false,
                embedded = false,
                targetTypeId = null,
                primaryMapping = PrimaryMapping.ID,
                primaryAnnotationTypeId = ID_ANNOTATION_TYPE_ID,
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

        private fun immutableProp(
            ownerTypeId: LsiSymbolId,
            name: String,
            targetTypeId: LsiSymbolId,
        ): ImmutableProp {
            val propId = LsiSymbolId.property(ownerTypeId, name)
            return ImmutableProp(
                id = propId,
                declarationId = propId,
                ownerTypeId = ownerTypeId,
                declaringTypeId = ownerTypeId,
                name = name,
                documentation = null,
                type = LsiDeclaredType(
                    declarationId = LsiSymbolId.type("java.util.List"),
                    arguments = listOf(LsiTypeArgument.invariant(declared(targetTypeId))),
                ),
                annotations = emptyList(),
                overrideChain = listOf(propId),
                inherited = false,
                overridden = false,
                nullable = false,
                list = true,
                association = true,
                embedded = false,
                targetTypeId = targetTypeId,
                primaryMapping = PrimaryMapping.ASSOCIATION,
                primaryAnnotationTypeId = null,
                defaultContract = null,
                associationKind = AssociationKind.MANY_TO_MANY,
                formulaKind = FormulaKind.NONE,
                mappedBy = null,
                associationStorage = AssociationStorageKind.MIDDLE_TABLE,
                transientResolver = null,
                view = null,
                genericTarget = false,
                remote = false,
                recursive = false,
                validations = emptyList(),
                converter = null,
            )
        }
    }
}
