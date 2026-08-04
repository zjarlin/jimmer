package site.addzero.lsi.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.AnnotationUseSiteTarget
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSReferenceElement
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Variance
import java.lang.annotation.RetentionPolicy
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.sequences.Sequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFileAnnotationScope
import site.addzero.lsi.model.LsiFrontendDocumentationConvention
import site.addzero.lsi.model.LsiFrontendOptions
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiGeneratedPeerDocumentationConvention
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiVariance
import site.addzero.lsi.model.LsiWorkspace

class KspLsiWorkspaceTest {

    private val frontendOptions = testLsiFrontendOptions()

    private enum class ReflectionMode {
        FIRST,
    }

    @Test
    fun `maps ksp all annotation use site target`() {
        assertEquals(LsiAnnotationUseSiteTarget.ALL, kspAnnotationUseSiteTarget("ALL"))
    }

    @Test
    fun `freezes type parameter use site nullability`() {
        val sourceFile = file("/workspace/src/main/kotlin/demo/Page.kt")
        lateinit var owner: KSClassDeclaration
        val parameter = typeParameter(
            name = "E",
            parent = { owner },
            bounds = { emptyList() },
        )
        owner = classDeclaration(
            qualifiedName = "demo.Page",
            classKind = ClassKind.INTERFACE,
            file = sourceFile,
            typeParameters = { listOf(parameter) },
        )
        val context = KspLsiTypeContext(resolver())
        val bareType = type(
            declaration = parameter,
            nullability = Nullability.NULLABLE,
            markedNullable = false,
        )
        val explicitNullableType = type(
            declaration = parameter,
            nullability = Nullability.NULLABLE,
            markedNullable = true,
        )

        assertEquals(
            LsiNullability.NON_NULL,
            assertIs<LsiTypeParameterRef>(context.toLsiType(typeReference(bareType))).nullability,
        )
        assertEquals(
            LsiNullability.NULLABLE,
            assertIs<LsiTypeParameterRef>(context.toLsiType(typeReference(explicitNullableType))).nullability,
        )
    }

    @Test
    fun `freezes description after direct doc string`() {
        val sourceFile = file("/workspace/src/main/kotlin/demo/DocumentedModels.kt")
        val descriptionType = classDeclaration(
            qualifiedName = DESCRIPTION_ANNOTATION,
            classKind = ClassKind.ANNOTATION_CLASS,
            origin = Origin.JAVA_LIB,
            file = null,
        )
        val stringType = classDeclaration(
            qualifiedName = "kotlin.String",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val description = { value: String ->
            annotation(descriptionType, listOf(valueArgument("value", value)))
        }
        lateinit var annotatedModel: KSClassDeclaration
        val annotatedName = property(
            name = "name",
            parent = { annotatedModel },
            type = typeReference(type(stringType)),
            annotations = sequenceOf(description("annotated property")),
            file = sourceFile,
            line = 2,
        )
        annotatedModel = classDeclaration(
            qualifiedName = "demo.AnnotatedModel",
            classKind = ClassKind.INTERFACE,
            file = sourceFile,
            declarations = { listOf(annotatedName) },
            annotations = sequenceOf(description("annotated type")),
        )
        lateinit var directModel: KSClassDeclaration
        val directName = property(
            name = "name",
            parent = { directModel },
            type = typeReference(type(stringType)),
            annotations = sequenceOf(description("ignored property")),
            documentation = "direct property",
            file = sourceFile,
            line = 4,
        )
        directModel = classDeclaration(
            qualifiedName = "demo.DirectModel",
            classKind = ClassKind.INTERFACE,
            file = sourceFile,
            declarations = { listOf(directName) },
            annotations = sequenceOf(description("ignored type")),
            documentation = "direct type",
        )
        val workspace = listOf(annotatedModel, directModel).toLsiWorkspace(
            resolver(
                classesByName = mapOf(
                    DESCRIPTION_ANNOTATION to descriptionType,
                    "kotlin.String" to stringType,
                )
            ),
            frontendOptions,
            fileScopes = listOfNotNull(annotatedModel.containingFile, directModel.containingFile)
                .distinct()
                .toKspLsiFileScopePlan()
                .validScopes,
        )

        val annotatedTypeId = LsiSymbolId.type("demo.AnnotatedModel")
        val directTypeId = LsiSymbolId.type("demo.DirectModel")
        assertEquals(
            "annotated type",
            assertIs<LsiTypeDeclaration>(workspace[annotatedTypeId]).documentation,
        )
        assertEquals(
            null,
            assertIs<LsiTypeDeclaration>(workspace[annotatedTypeId]).sourceDocumentation,
        )
        assertEquals("annotated property", workspace.requireProperty(annotatedTypeId, "name").documentation)
        assertEquals(null, workspace.requireProperty(annotatedTypeId, "name").sourceDocumentation)
        assertEquals(
            "direct type",
            assertIs<LsiTypeDeclaration>(workspace[directTypeId]).documentation,
        )
        assertEquals(
            "direct type",
            assertIs<LsiTypeDeclaration>(workspace[directTypeId]).sourceDocumentation,
        )
        assertEquals("direct property", workspace.requireProperty(directTypeId, "name").documentation)
        assertEquals("direct property", workspace.requireProperty(directTypeId, "name").sourceDocumentation)
    }

    @Test
    fun `freezes annotated file scope without declarations`() {
        val markerType = classDeclaration(
            qualifiedName = "demo.FileMarker",
            classKind = ClassKind.ANNOTATION_CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val sourceFile = file(
            path = "/workspace/src/main/kotlin/scoped/package.kt",
            packageName = "scoped",
            annotations = sequenceOf(
                annotation(
                    type = markerType,
                    arguments = emptyList(),
                    useSiteTarget = AnnotationUseSiteTarget.FILE,
                ),
            ),
        )
        val workspace = emptyList<KSClassDeclaration>().toLsiWorkspace(
            resolver = resolver(classesByName = mapOf("demo.FileMarker" to markerType)),
            frontendOptions = frontendOptions,
            fileScopes = listOf(sourceFile).toKspLsiFileScopePlan().validScopes,
        )

        assertTrue(workspace.declarations.isEmpty())
        val scope = assertIs<LsiFileAnnotationScope>(
            workspace.annotationScope(LsiSymbolId.fileScope("scoped", "package.kt")),
        )
        assertEquals("scoped", scope.packageName)
        assertEquals("package.kt", scope.logicalPath)
        assertEquals(LsiSymbolId.type("demo.FileMarker"), scope.annotations.single().type)
        assertEquals(LsiAnnotationUseSiteTarget.FILE, scope.annotations.single().useSiteTarget)
        assertEquals(LsiLanguage.KOTLIN, scope.origin.source?.language)
        assertTrue(scope.origin.source?.path?.endsWith("scoped/package.kt") == true)
    }

    @Test
    fun `freezes binary documentation from generated peer contract`() {
        val descriptionType = classDeclaration(
            qualifiedName = DESCRIPTION_ANNOTATION,
            classKind = ClassKind.ANNOTATION_CLASS,
            origin = Origin.JAVA_LIB,
            file = null,
        )
        val entityType = classDeclaration(
            qualifiedName = ENTITY_ANNOTATION,
            classKind = ClassKind.ANNOTATION_CLASS,
            origin = Origin.JAVA_LIB,
            file = null,
        )
        val stringType = classDeclaration(
            qualifiedName = "kotlin.String",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val description = { value: String ->
            annotation(descriptionType, listOf(valueArgument("value", value)))
        }
        lateinit var binaryBook: KSClassDeclaration
        val binaryName = property(
            name = "name",
            parent = { binaryBook },
            type = typeReference(type(stringType)),
            annotations = emptySequence(),
            origin = Origin.KOTLIN_LIB,
            file = null,
            line = 1,
        )
        binaryBook = classDeclaration(
            qualifiedName = "demo.BinaryBook",
            classKind = ClassKind.INTERFACE,
            origin = Origin.KOTLIN_LIB,
            file = null,
            declarations = { listOf(binaryName) },
            annotations = sequenceOf(annotation(entityType, emptyList())),
        )
        lateinit var peer: KSClassDeclaration
        val peerName = property(
            name = "name",
            parent = { peer },
            type = typeReference(type(stringType)),
            annotations = sequenceOf(description("binary property")),
            origin = Origin.KOTLIN_LIB,
            file = null,
            line = 1,
        )
        peer = classDeclaration(
            qualifiedName = "demo.BinaryBookPeer",
            classKind = ClassKind.INTERFACE,
            origin = Origin.KOTLIN_LIB,
            file = null,
            declarations = { listOf(peerName) },
            annotations = sequenceOf(description("binary type")),
        )
        val workspace = listOf(binaryBook).toLsiWorkspace(
            resolver(
                classesByName = mapOf(
                    DESCRIPTION_ANNOTATION to descriptionType,
                    ENTITY_ANNOTATION to entityType,
                    "kotlin.String" to stringType,
                    "demo.BinaryBook" to binaryBook,
                    "demo.BinaryBookPeer" to peer,
                )
            ),
            frontendOptions,
            fileScopes = listOfNotNull(binaryBook.containingFile).toKspLsiFileScopePlan().validScopes,
        )

        val bookId = LsiSymbolId.type("demo.BinaryBook")
        assertEquals("binary type", assertIs<LsiTypeDeclaration>(workspace[bookId]).documentation)
        assertEquals("binary property", workspace.requireProperty(bookId, "name").documentation)
    }

    @Test
    fun `freezes binary declarations with ksp frontend projection language`() {
        val javaBinary = classDeclaration(
            qualifiedName = "demo.JavaBinary",
            classKind = ClassKind.INTERFACE,
            origin = Origin.JAVA_LIB,
            file = null,
        )
        val kotlinBinary = classDeclaration(
            qualifiedName = "demo.KotlinBinary",
            classKind = ClassKind.INTERFACE,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val workspace = listOf(javaBinary, kotlinBinary).toLsiWorkspace(
            resolver = resolver(),
            frontendOptions = frontendOptions,
            fileScopes = emptyList(),
        )
        val javaDeclaration = assertIs<LsiTypeDeclaration>(
            workspace[LsiSymbolId.type("demo.JavaBinary")]
        )
        val kotlinDeclaration = assertIs<LsiTypeDeclaration>(
            workspace[LsiSymbolId.type("demo.KotlinBinary")]
        )

        assertEquals(LsiLanguage.JAVA, javaDeclaration.origin.language)
        assertEquals(LsiLanguage.KOTLIN, kotlinDeclaration.origin.language)
    }

    @Test
    fun `freezes java reflection enum annotation values`() {
        val retention = classDeclaration(
            qualifiedName = "java.lang.annotation.Retention",
            classKind = ClassKind.ANNOTATION_CLASS,
            origin = Origin.JAVA_LIB,
            file = null,
        )
        val annotation = annotation(
            type = retention,
            arguments = listOf(
                valueArgument("value", RetentionPolicy.RUNTIME),
                valueArgument("nested", ReflectionMode.FIRST),
            ),
        ).toLsiAnnotation(resolver())

        assertEquals(
            LsiAnnotationValue.EnumValue(
                enumType = LsiSymbolId.type("java.lang.annotation.RetentionPolicy"),
                entryName = "RUNTIME",
            ),
            annotation.arguments.getValue("value").value,
        )
        assertEquals(
            LsiAnnotationValue.EnumValue(
                enumType = LsiSymbolId.type(
                    "site.addzero.lsi.ksp.KspLsiWorkspaceTest.ReflectionMode"
                ),
                entryName = "FIRST",
            ),
            annotation.arguments.getValue("nested").value,
        )
    }

    @Test
    fun `ignores unresolved kotlin compiler type markers without dropping legal annotations`() {
        val sourceFile = file("/workspace/src/main/kotlin/demo/Service.kt")
        val stringType = classDeclaration(
            qualifiedName = "kotlin.String",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val legalAnnotationType = classDeclaration(
            qualifiedName = "demo.TypeUseTag",
            classKind = ClassKind.ANNOTATION_CLASS,
            file = sourceFile,
        )
        val compilerMarkerType = unresolvedAnnotationDeclaration("ExtensionFunctionType")
        val compilerMarker = annotation(
            type = compilerMarkerType,
            arguments = emptyList(),
            annotationType = typeReference(type(compilerMarkerType, error = true)),
            origin = Origin.KOTLIN_LIB,
        )
        val legalAnnotation = annotation(
            type = legalAnnotationType,
            arguments = listOf(valueArgument("value", "DETAIL")),
        )

        val frozenType = typeReference(
            type = type(stringType),
            annotations = sequenceOf(compilerMarker, legalAnnotation),
        ).toLsiType(
            resolver(
                classesByName = mapOf(
                    "kotlin.String" to stringType,
                    "demo.TypeUseTag" to legalAnnotationType,
                ),
            ),
        )

        assertEquals(listOf(LsiSymbolId.type("demo.TypeUseTag")), frozenType.annotations.map { it.type })
        assertEquals(
            LsiAnnotationValue.StringValue("DETAIL"),
            frozenType.annotations.single().arguments.getValue("value").value,
        )
    }

    @Test
    fun `preserves kotlin mutable list source identity`() {
        val sourceFile = file("/workspace/src/main/kotlin/demo/MutableLists.kt")
        val mutableListDeclaration = classDeclaration(
            qualifiedName = "kotlin.collections.MutableList",
            classKind = ClassKind.INTERFACE,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val stringDeclaration = classDeclaration(
            qualifiedName = "kotlin.String",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val mutableListKspType = type(
            declaration = mutableListDeclaration,
            arguments = listOf(typeArgument(type(stringDeclaration))),
        )
        val frozenType = typeReference(
            mutableListKspType
        ).toLsiType(resolver())

        val mutableListType = assertIs<LsiDeclaredType>(frozenType)
        assertEquals(
            LsiSymbolId.type("kotlin.collections.MutableList"),
            mutableListType.declarationId,
        )
        assertEquals(
            LsiSymbolId.type("java.lang.String"),
            assertIs<LsiDeclaredType>(mutableListType.arguments.single().type).declarationId,
        )

        lateinit var service: KSClassDeclaration
        lateinit var consume: KSFunctionDeclaration
        val values = valueParameter(
            name = "values",
            parent = { consume },
            type = typeReference(mutableListKspType),
            file = sourceFile,
        )
        consume = function(
            name = "consume",
            parent = { service },
            parameters = listOf(values),
            file = sourceFile,
        )
        service = classDeclaration(
            qualifiedName = "demo.MutableListService",
            classKind = ClassKind.INTERFACE,
            file = sourceFile,
            declarations = { listOf(consume) },
        )
        val jvmSignature = "type:java.util.List<type:java.lang.String>"

        assertEquals(jvmSignature, mutableListKspType.toKspStableSignature())
        assertEquals(
            LsiSymbolId.function(
                owner = LsiSymbolId.type("demo.MutableListService"),
                name = "consume",
                parameterTypeSignatures = listOf(jvmSignature),
            ),
            KspLsiTypeContext(resolver()).toLsiCallableId(consume),
        )
    }

    @Test
    fun `uses jvm primitive representations in callable ids`() {
        val sourceFile = file("/workspace/src/main/kotlin/demo/PrimitiveFactory.kt")
        val intDeclaration = classDeclaration(
            qualifiedName = "kotlin.Int",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val intArrayDeclaration = classDeclaration(
            qualifiedName = "kotlin.IntArray",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val arrayDeclaration = classDeclaration(
            qualifiedName = "kotlin.Array",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val listDeclaration = classDeclaration(
            qualifiedName = "kotlin.collections.List",
            classKind = ClassKind.INTERFACE,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val rawIntType = type(intDeclaration)
        val boxedIntType = type(intDeclaration, nullability = Nullability.NULLABLE)
        val primitiveArrayType = type(intArrayDeclaration)
        val boxedArrayType = type(
            declaration = arrayDeclaration,
            arguments = listOf(typeArgument(rawIntType)),
        )
        val genericType = type(
            declaration = listDeclaration,
            arguments = listOf(typeArgument(rawIntType)),
        )
        val nullableBoxedArrayType = type(
            declaration = arrayDeclaration,
            arguments = listOf(typeArgument(boxedIntType)),
        )
        val nullableGenericType = type(
            declaration = listDeclaration,
            arguments = listOf(typeArgument(boxedIntType)),
        )
        lateinit var factory: KSClassDeclaration

        fun method(name: String, parameterType: KSType): KSFunctionDeclaration {
            lateinit var method: KSFunctionDeclaration
            val value = valueParameter(
                name = "value",
                parent = { method },
                type = typeReference(parameterType),
                file = sourceFile,
            )
            method = function(
                name = name,
                parent = { factory },
                parameters = listOf(value),
                file = sourceFile,
            )
            return method
        }

        val methods = listOf(
            method("raw", rawIntType),
            method("boxed", boxedIntType),
            method("primitiveArray", primitiveArrayType),
            method("boxedArray", boxedArrayType),
            method("generic", genericType),
        )
        factory = classDeclaration(
            qualifiedName = "demo.PrimitiveFactory",
            classKind = ClassKind.CLASS,
            file = sourceFile,
            declarations = { methods },
        )
        val ownerId = LsiSymbolId.type("demo.PrimitiveFactory")
        val ids = methods.mapTo(linkedSetOf()) { method ->
            KspLsiTypeContext(resolver()).toLsiCallableId(method)
        }

        assertEquals(
            setOf(
                LsiSymbolId.function(ownerId, "raw", listOf("primitive:int")),
                LsiSymbolId.function(ownerId, "boxed", listOf("type:java.lang.Integer")),
                LsiSymbolId.function(ownerId, "primitiveArray", listOf("array:primitive:int")),
                LsiSymbolId.function(ownerId, "boxedArray", listOf("array:type:java.lang.Integer")),
                LsiSymbolId.function(
                    ownerId,
                    "generic",
                    listOf("type:java.util.List<type:java.lang.Integer>"),
                ),
            ),
            ids,
        )

        val frozenRaw = assertIs<LsiPrimitiveType>(typeReference(rawIntType).toLsiType(resolver()))
        val frozenBoxed = assertIs<LsiPrimitiveType>(typeReference(boxedIntType).toLsiType(resolver()))
        val frozenPrimitiveArray = assertIs<LsiArrayType>(
            typeReference(primitiveArrayType).toLsiType(resolver()),
        )
        val frozenBoxedArray = assertIs<LsiArrayType>(
            typeReference(boxedArrayType).toLsiType(resolver()),
        )
        val frozenGeneric = assertIs<LsiDeclaredType>(
            typeReference(genericType).toLsiType(resolver()),
        )
        val frozenNullableBoxedArray = assertIs<LsiArrayType>(
            typeReference(nullableBoxedArrayType).toLsiType(resolver()),
        )
        val frozenNullableGeneric = assertIs<LsiDeclaredType>(
            typeReference(nullableGenericType).toLsiType(resolver()),
        )

        assertFalse(frozenRaw.boxed)
        assertTrue(frozenBoxed.boxed)
        assertEquals(LsiNullability.NULLABLE, frozenBoxed.nullability)
        assertFalse(assertIs<LsiPrimitiveType>(frozenPrimitiveArray.elementType).boxed)
        val frozenBoxedArrayElement = assertIs<LsiPrimitiveType>(frozenBoxedArray.elementType)
        assertTrue(frozenBoxedArrayElement.boxed)
        assertEquals(LsiNullability.NON_NULL, frozenBoxedArrayElement.nullability)
        val frozenGenericArgument = assertIs<LsiPrimitiveType>(frozenGeneric.arguments.single().type)
        assertTrue(frozenGenericArgument.boxed)
        assertEquals(LsiNullability.NON_NULL, frozenGenericArgument.nullability)
        val frozenNullableArrayElement = assertIs<LsiPrimitiveType>(frozenNullableBoxedArray.elementType)
        assertTrue(frozenNullableArrayElement.boxed)
        assertEquals(LsiNullability.NULLABLE, frozenNullableArrayElement.nullability)
        val frozenNullableGenericArgument = assertIs<LsiPrimitiveType>(
            frozenNullableGeneric.arguments.single().type,
        )
        assertTrue(frozenNullableGenericArgument.boxed)
        assertEquals(LsiNullability.NULLABLE, frozenNullableGenericArgument.nullability)
    }

    @Test
    fun `freezes source type arguments before resolved arguments`() {
        val stringDeclaration = classDeclaration(
            qualifiedName = "kotlin.String",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val listDeclaration = classDeclaration(
            qualifiedName = "kotlin.collections.List",
            classKind = ClassKind.INTERFACE,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val arrayDeclaration = classDeclaration(
            qualifiedName = "kotlin.Array",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val annotationDeclaration = classDeclaration(
            qualifiedName = "demo.TypeUse",
            classKind = ClassKind.ANNOTATION_CLASS,
            file = null,
        )
        val markerDeclaration = classDeclaration(
            qualifiedName = "demo.Marker",
            classKind = ClassKind.CLASS,
            file = null,
        )
        val resolvedStringType = type(stringDeclaration)
        val resolvedArgument = typeArgument(resolvedStringType)
        val typeUseAnnotation = annotation(
            type = annotationDeclaration,
            arguments = listOf(valueArgument("type", type(markerDeclaration))),
        )
        val sourceArgument = typeArgument(
            reference = typeReference(
                type = resolvedStringType,
                annotations = sequenceOf(typeUseAnnotation),
            ),
        )
        val context = KspLsiTypeContext(resolver())

        val frozenDeclared = assertIs<LsiDeclaredType>(
            context.toLsiType(
                typeReference(
                    type = type(listDeclaration, listOf(resolvedArgument)),
                    sourceArguments = listOf(sourceArgument),
                ),
            ),
        )
        val frozenArray = assertIs<LsiArrayType>(
            context.toLsiType(
                typeReference(
                    type = type(arrayDeclaration, listOf(resolvedArgument)),
                    sourceArguments = listOf(sourceArgument),
                ),
            ),
        )
        val fallbackDeclared = assertIs<LsiDeclaredType>(
            context.toLsiType(
                typeReference(
                    type = type(listDeclaration, listOf(resolvedArgument)),
                    sourceArguments = emptyList(),
                ),
            ),
        )

        assertSourceTypeUseAnnotation(requireNotNull(frozenDeclared.arguments.single().type))
        assertSourceTypeUseAnnotation(frozenArray.elementType)
        assertTrue(requireNotNull(fallbackDeclared.arguments.single().type).annotations.isEmpty())
    }

    @Test
    fun `normalizes java getters and keeps kotlin property semantics`() {
        val sourceFile = file("/workspace/src/main/java/demo/Switches.java")
        val booleanDeclaration = classDeclaration(
            qualifiedName = "kotlin.Boolean",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val booleanType = typeReference(type(booleanDeclaration))

        lateinit var javaDeclaration: KSClassDeclaration
        val javaGetter = function(
            name = "isActive",
            parent = { javaDeclaration },
            parameters = emptyList(),
            returnType = booleanType,
            origin = Origin.JAVA,
            file = sourceFile,
        )
        javaDeclaration = classDeclaration(
            qualifiedName = "demo.Switches",
            classKind = ClassKind.INTERFACE,
            origin = Origin.JAVA,
            file = sourceFile,
            declarations = { listOf(javaGetter) },
        )

        lateinit var kotlinDeclaration: KSClassDeclaration
        val kotlinProperty = property(
            name = "isActive",
            parent = { kotlinDeclaration },
            type = booleanType,
            annotations = emptySequence(),
            file = sourceFile,
            line = 1,
        )
        kotlinDeclaration = classDeclaration(
            qualifiedName = "demo.Switches",
            classKind = ClassKind.INTERFACE,
            file = sourceFile,
            declarations = { listOf(kotlinProperty) },
        )

        val defaultJavaWorkspace = listOf(javaDeclaration).toLsiWorkspace(
            resolver(),
            frontendOptions,
            fileScopes = listOfNotNull(javaDeclaration.containingFile).toKspLsiFileScopePlan().validScopes,
        )
        val keepPrefixOptions = testLsiFrontendOptions(keepIsPrefix = true)
        val keepPrefixJavaWorkspace = listOf(javaDeclaration).toLsiWorkspace(
            resolver(),
            keepPrefixOptions,
            fileScopes = listOfNotNull(javaDeclaration.containingFile).toKspLsiFileScopePlan().validScopes,
        )
        val kotlinWorkspace = listOf(kotlinDeclaration).toLsiWorkspace(
            resolver(),
            frontendOptions,
            fileScopes = listOfNotNull(kotlinDeclaration.containingFile).toKspLsiFileScopePlan().validScopes,
        )
        val ownerId = LsiSymbolId.type("demo.Switches")

        assertEquals("isActive", defaultJavaWorkspace.requireProperty(ownerId, "active").getterName)
        val javaProperty = keepPrefixJavaWorkspace.requireProperty(ownerId, "isActive")
        val frozenKotlinProperty = kotlinWorkspace.requireProperty(ownerId, "isActive")
        assertEquals(frozenKotlinProperty.id, javaProperty.id)
        assertEquals(frozenKotlinProperty.name, javaProperty.name)
        assertTrue(defaultJavaWorkspace.declarationsOfType<LsiFunction>().none { function ->
            function.ownerId == ownerId && function.name == "isActive"
        })
    }

    @Test
    fun `freezes java fields separately from getter properties`() {
        val sourceFile = file("/workspace/src/main/java/demo/Tree.java")
        val stringDeclaration = classDeclaration(
            qualifiedName = "java.lang.String",
            classKind = ClassKind.CLASS,
            origin = Origin.JAVA_LIB,
            file = null,
        )
        val stringType = typeReference(type(stringDeclaration))
        lateinit var treeDeclaration: KSClassDeclaration
        val dataField = property(
            name = "data",
            parent = { treeDeclaration },
            type = stringType,
            annotations = emptySequence(),
            modifiers = setOf(Modifier.PRIVATE),
            origin = Origin.JAVA,
            mutable = true,
            documentation = "Backing field.",
            file = sourceFile,
            line = 3,
        )
        val dataGetter = function(
            name = "getData",
            parent = { treeDeclaration },
            parameters = emptyList(),
            modifiers = setOf(Modifier.PUBLIC),
            returnType = stringType,
            origin = Origin.JAVA,
            file = sourceFile,
        )
        treeDeclaration = classDeclaration(
            qualifiedName = "demo.Tree",
            classKind = ClassKind.CLASS,
            origin = Origin.JAVA,
            file = sourceFile,
            declarations = { listOf(dataField, dataGetter) },
        )

        val workspace = listOf(treeDeclaration).toLsiWorkspace(
            resolver = resolver(),
            frontendOptions = frontendOptions,
            fileScopes = listOfNotNull(treeDeclaration.containingFile).toKspLsiFileScopePlan().validScopes,
        )

        val ownerId = LsiSymbolId.type("demo.Tree")
        val fieldId = LsiSymbolId.field(ownerId, "data")
        val propertyId = LsiSymbolId.property(ownerId, "data")
        val frozenType = assertIs<LsiTypeDeclaration>(workspace[ownerId])
        val frozenField = assertIs<LsiField>(workspace[fieldId])
        val frozenProperty = assertIs<LsiProperty>(workspace[propertyId])
        assertEquals(setOf(fieldId, propertyId), frozenType.memberIds.toSet())
        assertEquals("Backing field.", frozenField.documentation)
        assertTrue(frozenField.mutable)
        assertEquals(LsiLanguage.JAVA, frozenField.origin.source?.language)
        assertEquals("getData", frozenProperty.getterName)
        assertTrue(workspace.declarationsOfType<LsiFunction>().none { function ->
            function.ownerId == ownerId && function.name == "getData"
        })
    }

    @Test
    fun `freezes enclosing type and data class flag`() {
        val sourceFile = file("/workspace/src/main/kotlin/demo/Outer.kt")
        lateinit var outer: KSClassDeclaration
        val nested = classDeclaration(
            qualifiedName = "demo.Outer.Row",
            classKind = ClassKind.CLASS,
            file = sourceFile,
            parent = { outer },
            modifiers = setOf(Modifier.DATA),
        )
        val inner = classDeclaration(
            qualifiedName = "demo.Outer.Inner",
            classKind = ClassKind.CLASS,
            file = sourceFile,
            parent = { outer },
            modifiers = setOf(Modifier.INNER),
        )
        outer = classDeclaration(
            qualifiedName = "demo.Outer",
            classKind = ClassKind.CLASS,
            file = sourceFile,
            declarations = { listOf(nested, inner) },
        )

        val workspace = listOf(outer).toLsiWorkspace(
            resolver = resolver(),
            frontendOptions = frontendOptions,
            fileScopes = listOfNotNull(outer.containingFile).toKspLsiFileScopePlan().validScopes,
        )

        val outerId = LsiSymbolId.type("demo.Outer")
        val outerSnapshot = assertIs<LsiTypeDeclaration>(workspace[outerId])
        assertEquals(null, outerSnapshot.enclosingTypeId)
        assertFalse(outerSnapshot.dataClass)
        val nestedSnapshot = assertIs<LsiTypeDeclaration>(workspace[LsiSymbolId.type("demo.Outer.Row")])
        assertEquals(outerId, nestedSnapshot.enclosingTypeId)
        assertFalse(nestedSnapshot.requiresEnclosingInstance)
        assertTrue(nestedSnapshot.dataClass)
        val innerSnapshot = assertIs<LsiTypeDeclaration>(workspace[LsiSymbolId.type("demo.Outer.Inner")])
        assertEquals(outerId, innerSnapshot.enclosingTypeId)
        assertTrue(innerSnapshot.requiresEnclosingInstance)
    }

    @Test
    fun `freezes java member class record and sealed declaration facts`() {
        val sourceFile = file("/workspace/src/main/java/demo/JavaTypes.java")
        val recordBase = classDeclaration(
            qualifiedName = "java.lang.Record",
            classKind = ClassKind.CLASS,
            origin = Origin.JAVA_LIB,
            file = null,
        )
        lateinit var outer: KSClassDeclaration
        val inner = classDeclaration(
            qualifiedName = "demo.JavaTypes.Inner",
            classKind = ClassKind.CLASS,
            origin = Origin.JAVA,
            file = sourceFile,
            parent = { outer },
        )
        val staticNested = classDeclaration(
            qualifiedName = "demo.JavaTypes.StaticNested",
            classKind = ClassKind.CLASS,
            origin = Origin.JAVA,
            file = sourceFile,
            parent = { outer },
            modifiers = setOf(Modifier.JAVA_STATIC),
        )
        val nestedRecord = classDeclaration(
            qualifiedName = "demo.JavaTypes.NestedRecord",
            classKind = ClassKind.CLASS,
            origin = Origin.JAVA,
            file = sourceFile,
            parent = { outer },
            superTypes = { listOf(typeReference(type(recordBase))) },
        )
        outer = classDeclaration(
            qualifiedName = "demo.JavaTypes",
            classKind = ClassKind.CLASS,
            origin = Origin.JAVA,
            file = sourceFile,
            declarations = { listOf(inner, staticNested, nestedRecord) },
        )
        val javaSealed = classDeclaration(
            qualifiedName = "demo.JavaSealed",
            classKind = ClassKind.CLASS,
            origin = Origin.JAVA,
            file = sourceFile,
            modifiers = setOf(Modifier.SEALED),
        )
        val kotlinSealed = classDeclaration(
            qualifiedName = "demo.KotlinSealed",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN,
            file = file("/workspace/src/main/kotlin/demo/KotlinSealed.kt"),
            modifiers = setOf(Modifier.SEALED),
        )

        val workspace = listOf(outer, javaSealed, kotlinSealed).toLsiWorkspace(
            resolver(classesByName = mapOf("java.lang.Record" to recordBase)),
            frontendOptions,
            fileScopes = listOfNotNull(
                outer.containingFile,
                javaSealed.containingFile,
                kotlinSealed.containingFile,
            ).distinct().toKspLsiFileScopePlan().validScopes,
        )

        assertTrue(
            assertIs<LsiTypeDeclaration>(workspace[LsiSymbolId.type("demo.JavaTypes.Inner")])
                .requiresEnclosingInstance,
        )
        assertFalse(
            assertIs<LsiTypeDeclaration>(workspace[LsiSymbolId.type("demo.JavaTypes.StaticNested")])
                .requiresEnclosingInstance,
        )
        assertFalse(
            assertIs<LsiTypeDeclaration>(workspace[LsiSymbolId.type("demo.JavaTypes.NestedRecord")])
                .requiresEnclosingInstance,
        )
        assertEquals(
            LsiTypeDeclarationKind.RECORD,
            assertIs<LsiTypeDeclaration>(workspace[LsiSymbolId.type("demo.JavaTypes.NestedRecord")]).kind,
        )
        assertFalse(
            assertIs<LsiTypeDeclaration>(workspace[LsiSymbolId.type("demo.JavaSealed")])
                .abstractDeclaration,
        )
        assertTrue(
            assertIs<LsiTypeDeclaration>(workspace[LsiSymbolId.type("demo.KotlinSealed")])
                .abstractDeclaration,
        )
    }

    @Test
    fun `freezes kotlin constructors and parameters`() {
        val sourceFile = file("/workspace/src/main/kotlin/demo/Service.kt")
        val stringDeclaration = classDeclaration(
            qualifiedName = "kotlin.String",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val constructorAnnotationType = classDeclaration(
            qualifiedName = "demo.ConstructorMarker",
            classKind = ClassKind.ANNOTATION_CLASS,
            file = sourceFile,
        )
        val parameterAnnotationType = classDeclaration(
            qualifiedName = "demo.ParameterMarker",
            classKind = ClassKind.ANNOTATION_CLASS,
            file = sourceFile,
        )
        lateinit var service: KSClassDeclaration
        lateinit var constructor: KSFunctionDeclaration
        val nameParameter = valueParameter(
            name = "name",
            parent = { constructor },
            type = typeReference(type(stringDeclaration)),
            annotations = sequenceOf(annotation(parameterAnnotationType, emptyList())),
            file = sourceFile,
        )
        constructor = function(
            name = "<init>",
            parent = { service },
            parameters = listOf(nameParameter),
            annotations = sequenceOf(annotation(constructorAnnotationType, emptyList())),
            modifiers = setOf(Modifier.PRIVATE),
            file = sourceFile,
        )
        service = classDeclaration(
            qualifiedName = "demo.Service",
            classKind = ClassKind.CLASS,
            file = sourceFile,
            declarations = { listOf(constructor) },
        )

        val workspace = listOf(service).toLsiWorkspace(
            resolver = resolver(),
            frontendOptions = frontendOptions,
            fileScopes = listOfNotNull(service.containingFile).toKspLsiFileScopePlan().validScopes,
        )

        val serviceId = LsiSymbolId.type("demo.Service")
        val frozen = workspace.declarationsOfType<LsiConstructor>().single()
        assertEquals(LsiSymbolId.constructor(serviceId, listOf("type:java.lang.String")), frozen.id)
        assertEquals(serviceId, frozen.ownerId)
        assertEquals(site.addzero.lsi.model.LsiVisibility.PRIVATE, frozen.visibility)
        assertEquals(LsiAnnotationUseSiteTarget.CONSTRUCTOR, frozen.annotations.single().useSiteTarget)
        assertEquals(LsiSymbolId.type("demo.ParameterMarker"), frozen.parameters.single().annotations.single().type)
        assertEquals(frozen.id, frozen.parameters.single().callableId)
        assertTrue(assertIs<LsiTypeDeclaration>(workspace[serviceId]).memberIds.contains(frozen.id))
    }

    @Test
    fun `freezes kotlin properties annotations generics and overrides`() {
        val sourceFile = file("/workspace/src/main/kotlin/demo/Models.kt")
        val stringDeclaration = classDeclaration(
            qualifiedName = "kotlin.String",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val numberDeclaration = classDeclaration(
            qualifiedName = "kotlin.Number",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val defaultAnnotationType = classDeclaration(
            qualifiedName = DEFAULT_ANNOTATION,
            classKind = ClassKind.ANNOTATION_CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val sampleAnnotationType = classDeclaration(
            qualifiedName = "demo.Sample",
            classKind = ClassKind.ANNOTATION_CLASS,
            file = sourceFile,
        )
        val nestedAnnotationType = classDeclaration(
            qualifiedName = "demo.Nested",
            classKind = ClassKind.ANNOTATION_CLASS,
            file = sourceFile,
        )
        lateinit var modeDeclaration: KSClassDeclaration
        val firstModeEntry = classDeclaration(
            qualifiedName = "demo.Mode.FIRST",
            classKind = ClassKind.ENUM_ENTRY,
            file = sourceFile,
            parent = { modeDeclaration },
        )
        modeDeclaration = classDeclaration(
            qualifiedName = "demo.Mode",
            classKind = ClassKind.ENUM_CLASS,
            file = sourceFile,
            declarations = { listOf(firstModeEntry) },
        )

        val stringType = type(stringDeclaration)
        val numberType = type(numberDeclaration)
        lateinit var parentDeclaration: KSClassDeclaration
        val parentTypeParameter = typeParameter(
            name = "T",
            parent = { parentDeclaration },
            bounds = { listOf(typeReference(numberType)) },
        )
        val parentTypeParameterType = type(parentTypeParameter)
        val parentDefault = annotation(
            type = defaultAnnotationType,
            arguments = listOf(valueArgument("value", "0")),
        )
        lateinit var parentProperty: KSPropertyDeclaration
        parentProperty = property(
            name = "status",
            parent = { parentDeclaration },
            type = typeReference(parentTypeParameterType),
            annotations = sequenceOf(parentDefault),
            file = sourceFile,
            line = 12,
        )
        parentDeclaration = classDeclaration(
            qualifiedName = "demo.Parent",
            classKind = ClassKind.INTERFACE,
            file = sourceFile,
            typeParameters = { listOf(parentTypeParameter) },
            declarations = { listOf(parentProperty) },
            line = 8,
        )
        lateinit var middleDeclaration: KSClassDeclaration
        val middleTypeParameter = typeParameter(
            name = "M",
            parent = { middleDeclaration },
            bounds = { emptyList() },
        )
        val middleTypeParameterType = type(middleTypeParameter)
        val parentMiddleType = type(
            declaration = parentDeclaration,
            arguments = listOf(typeArgument(middleTypeParameterType)),
        )
        middleDeclaration = classDeclaration(
            qualifiedName = "demo.Middle",
            classKind = ClassKind.INTERFACE,
            file = sourceFile,
            typeParameters = { listOf(middleTypeParameter) },
            superTypes = { listOf(typeReference(parentMiddleType)) },
            line = 16,
        )

        val nestedValue = annotation(
            type = nestedAnnotationType,
            arguments = listOf(valueArgument("value", "nested")),
        )
        val enabledArgument = valueArgument("enabled", true)
        val byteArgument = valueArgument("byteValue", 1.toByte())
        val shortArgument = valueArgument("shortValue", 2.toShort())
        val modeArgument = valueArgument("mode", type(firstModeEntry))
        val classArgument = valueArgument("type", stringType)
        val nestedArgument = valueArgument("nested", nestedValue)
        val numbersArgument = valueArgument("numbers", listOf(1, 2))
        val textArgument = valueArgument("text", "explicit")
        val defaultTextArgument = valueArgument("text", "default")
        val sampleAnnotation = annotation(
            type = sampleAnnotationType,
            arguments = listOf(
                enabledArgument,
                byteArgument,
                shortArgument,
                modeArgument,
                classArgument,
                nestedArgument,
                numbersArgument,
                textArgument,
            ),
            defaultArguments = listOf(
                enabledArgument,
                byteArgument,
                shortArgument,
                modeArgument,
                classArgument,
                nestedArgument,
                numbersArgument,
                defaultTextArgument,
            ),
        )
        val childDefault = annotation(
            type = defaultAnnotationType,
            arguments = listOf(valueArgument("value", "1")),
            useSiteTarget = AnnotationUseSiteTarget.GET,
        )
        lateinit var childDeclaration: KSClassDeclaration
        val childProperty = property(
            name = "status",
            parent = { childDeclaration },
            type = typeReference(stringType),
            annotations = sequenceOf(childDefault, sampleAnnotation),
            modifiers = setOf(Modifier.OVERRIDE),
            overridee = { parentProperty },
            file = sourceFile,
            line = 21,
        )
        val middleStringType = type(
            declaration = middleDeclaration,
            arguments = listOf(typeArgument(stringType)),
        )
        childDeclaration = classDeclaration(
            qualifiedName = "demo.Child",
            classKind = ClassKind.INTERFACE,
            file = sourceFile,
            declarations = { listOf(childProperty) },
            superTypes = { listOf(typeReference(middleStringType)) },
            documentation = "子类型文档。",
            line = 18,
        )
        val resolver = resolver { overrider, overridee, owner ->
            overrider === childProperty && overridee === parentProperty && owner === childDeclaration
        }

        val workspace = listOf(parentDeclaration, middleDeclaration, childDeclaration, modeDeclaration)
            .toLsiWorkspace(
                resolver = resolver,
                frontendOptions = frontendOptions,
                fileScopes = listOfNotNull(
                    parentDeclaration.containingFile,
                    middleDeclaration.containingFile,
                    childDeclaration.containingFile,
                    modeDeclaration.containingFile,
                ).distinct().toKspLsiFileScopePlan().validScopes,
            )

        val parentId = LsiSymbolId.type("demo.Parent")
        val middleId = LsiSymbolId.type("demo.Middle")
        val childId = LsiSymbolId.type("demo.Child")
        val parent = assertIs<LsiTypeDeclaration>(workspace[parentId])
        val middle = assertIs<LsiTypeDeclaration>(workspace[middleId])
        val child = assertIs<LsiTypeDeclaration>(workspace[childId])
        assertEquals("子类型文档。", child.documentation)
        assertEquals(LsiLanguage.KOTLIN, child.origin.source?.language)
        assertTrue(child.origin.source?.path?.endsWith("src/main/kotlin/demo/Models.kt") == true)
        assertEquals(18, assertNotNull(child.location).start.line)

        val parentTypeParameterSnapshot = parent.typeParameters.single()
        assertEquals(
            LsiSymbolId.type("java.lang.Number"),
            assertIs<LsiDeclaredType>(parentTypeParameterSnapshot.upperBounds.single()).declarationId,
        )
        val childSuperType = assertIs<LsiDeclaredType>(child.superTypes.single())
        assertEquals(middleId, childSuperType.declarationId)
        assertEquals(LsiVariance.INVARIANT, childSuperType.arguments.single().variance)
        assertEquals(
            LsiSymbolId.type("java.lang.String"),
            assertIs<LsiDeclaredType>(childSuperType.arguments.single().type).declarationId,
        )
        val middleSuperType = assertIs<LsiDeclaredType>(middle.superTypes.single())
        assertEquals(parentId, middleSuperType.declarationId)
        assertEquals(
            middle.typeParameters.single().id,
            assertIs<LsiTypeParameterRef>(middleSuperType.arguments.single().type).parameterId,
        )

        val parentStatus = workspace.requireProperty(parentId, "status")
        assertEquals(
            parentTypeParameterSnapshot.id,
            assertIs<LsiTypeParameterRef>(parentStatus.type).parameterId,
        )
        val childStatus = workspace.requireProperty(childId, "status")
        assertEquals(LsiSymbolId.type("java.lang.String"), assertIs<LsiDeclaredType>(childStatus.type).declarationId)
        assertEquals(listOf(parentStatus.id to 2), childStatus.overrides.map { it.declarationId to it.distance })

        val childDefaultSnapshot = childStatus.annotation(DEFAULT_ANNOTATION)
        assertEquals(LsiAnnotationUseSiteTarget.GETTER, childDefaultSnapshot.useSiteTarget)
        assertEquals(
            LsiAnnotationValue.StringValue("1"),
            childDefaultSnapshot.arguments.getValue("value").value,
        )
        assertEquals(
            LsiAnnotationArgumentOrigin.EXPLICIT,
            childDefaultSnapshot.arguments.getValue("value").origin,
        )

        val sample = childStatus.annotation("demo.Sample")
        assertEquals(LsiAnnotationValue.BooleanValue(true), sample.arguments.getValue("enabled").value)
        assertEquals(LsiAnnotationValue.ByteValue(1), sample.arguments.getValue("byteValue").value)
        assertEquals(LsiAnnotationValue.ShortValue(2), sample.arguments.getValue("shortValue").value)
        assertEquals(
            LsiAnnotationArgumentOrigin.DEFAULT,
            sample.arguments.getValue("enabled").origin,
        )
        assertEquals(
            LsiAnnotationArgumentOrigin.EXPLICIT,
            sample.arguments.getValue("text").origin,
        )
        assertEquals(
            LsiAnnotationValue.StringValue("explicit"),
            sample.arguments.getValue("text").value,
        )
        assertEquals(
            LsiAnnotationValue.EnumValue(LsiSymbolId.type("demo.Mode"), "FIRST"),
            sample.arguments.getValue("mode").value,
        )
        assertEquals(
            LsiSymbolId.type("java.lang.String"),
            assertIs<LsiDeclaredType>(
                assertIs<LsiAnnotationValue.ClassValue>(sample.arguments.getValue("type").value).type,
            ).declarationId,
        )
        assertEquals(
            LsiAnnotationValue.StringValue("nested"),
            assertIs<LsiAnnotationValue.NestedAnnotationValue>(
                sample.arguments.getValue("nested").value,
            ).annotation.arguments.getValue("value").value,
        )
        assertEquals(
            listOf(LsiAnnotationValue.IntValue(1), LsiAnnotationValue.IntValue(2)),
            assertIs<LsiAnnotationValue.ArrayValue>(sample.arguments.getValue("numbers").value).elements,
        )
    }

    @Test
    fun `freezes external mapped superclass and annotation semantic closure`() {
        val sourceFile = file("/workspace/src/main/kotlin/demo/LocalModel.kt")
        val stringDeclaration = classDeclaration(
            qualifiedName = "kotlin.String",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val libraryFile = file("/jdk/java/time/Instant.java")
        lateinit var instantDeclaration: KSClassDeclaration
        val epochSecond = property(
            name = "epochSecond",
            parent = { instantDeclaration },
            type = typeReference(type(stringDeclaration)),
            annotations = emptySequence(),
            file = libraryFile,
            line = 1,
        )
        instantDeclaration = classDeclaration(
            qualifiedName = "java.time.Instant",
            classKind = ClassKind.CLASS,
            origin = Origin.JAVA_LIB,
            file = libraryFile,
            declarations = { listOf(epochSecond) },
        )
        val mappedSuperclass = classDeclaration(
            qualifiedName = MAPPED_SUPERCLASS_ANNOTATION,
            classKind = ClassKind.ANNOTATION_CLASS,
            origin = Origin.JAVA_LIB,
            file = null,
        )
        val entity = classDeclaration(
            qualifiedName = ENTITY_ANNOTATION,
            classKind = ClassKind.ANNOTATION_CLASS,
            origin = Origin.JAVA_LIB,
            file = null,
        )
        val embeddable = classDeclaration(
            qualifiedName = EMBEDDABLE_ANNOTATION,
            classKind = ClassKind.ANNOTATION_CLASS,
            origin = Origin.JAVA_LIB,
            file = null,
        )
        val semanticMarker = classDeclaration(
            qualifiedName = "external.SemanticMarker",
            classKind = ClassKind.ANNOTATION_CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val validationRule = classDeclaration(
            qualifiedName = "external.ValidationRule",
            classKind = ClassKind.ANNOTATION_CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
            annotations = sequenceOf(annotation(semanticMarker, emptyList())),
        )
        lateinit var externalBase: KSClassDeclaration
        val externalTypeParameter = typeParameter(
            name = "T",
            parent = { externalBase },
            bounds = { emptyList() },
        )
        val externalValue = property(
            name = "value",
            parent = { externalBase },
            type = typeReference(type(externalTypeParameter)),
            annotations = sequenceOf(annotation(validationRule, emptyList())),
            file = sourceFile,
            line = 1,
        )
        val externalCreatedAt = property(
            name = "createdAt",
            parent = { externalBase },
            type = typeReference(type(instantDeclaration)),
            annotations = emptySequence(),
            file = sourceFile,
            line = 1,
        )
        externalBase = classDeclaration(
            qualifiedName = "external.ExternalBase",
            classKind = ClassKind.INTERFACE,
            origin = Origin.KOTLIN_LIB,
            file = null,
            typeParameters = { listOf(externalTypeParameter) },
            declarations = { listOf(externalValue, externalCreatedAt) },
            annotations = sequenceOf(annotation(mappedSuperclass, emptyList())),
        )
        lateinit var externalMiddle: KSClassDeclaration
        val middleTypeParameter = typeParameter(
            name = "M",
            parent = { externalMiddle },
            bounds = { emptyList() },
        )
        val middleIgnored = property(
            name = "ignored",
            parent = { externalMiddle },
            type = typeReference(type(stringDeclaration)),
            annotations = emptySequence(),
            file = sourceFile,
            line = 1,
        )
        val baseMiddleType = type(
            declaration = externalBase,
            arguments = listOf(typeArgument(type(middleTypeParameter))),
        )
        externalMiddle = classDeclaration(
            qualifiedName = "external.ExternalMiddle",
            classKind = ClassKind.INTERFACE,
            origin = Origin.KOTLIN_LIB,
            file = null,
            typeParameters = { listOf(middleTypeParameter) },
            declarations = { listOf(middleIgnored) },
            superTypes = { listOf(typeReference(baseMiddleType)) },
        )
        lateinit var externalEmbeddable: KSClassDeclaration
        val embeddableLabel = property(
            name = "label",
            parent = { externalEmbeddable },
            type = typeReference(type(stringDeclaration)),
            annotations = emptySequence(),
            file = sourceFile,
            line = 1,
        )
        externalEmbeddable = classDeclaration(
            qualifiedName = "external.ExternalValue",
            classKind = ClassKind.INTERFACE,
            origin = Origin.KOTLIN_LIB,
            file = null,
            declarations = { listOf(embeddableLabel) },
            annotations = sequenceOf(annotation(embeddable, emptyList())),
        )
        lateinit var objectDeclaration: KSClassDeclaration
        val objectClassProperty = property(
            name = "class",
            parent = { objectDeclaration },
            type = typeReference(type(stringDeclaration)),
            annotations = emptySequence(),
            file = sourceFile,
            line = 1,
        )
        objectDeclaration = classDeclaration(
            qualifiedName = "java.lang.Object",
            classKind = ClassKind.CLASS,
            origin = Origin.JAVA_LIB,
            file = null,
            declarations = { listOf(objectClassProperty) },
            superTypes = { listOf(typeReference(type(objectDeclaration))) },
        )
        val middleStringType = type(
            declaration = externalMiddle,
            arguments = listOf(typeArgument(type(stringDeclaration))),
        )
        lateinit var localModel: KSClassDeclaration
        val payload = property(
            name = "payload",
            parent = { localModel },
            type = typeReference(type(externalEmbeddable)),
            annotations = emptySequence(),
            file = sourceFile,
            line = 1,
        )
        localModel = classDeclaration(
            qualifiedName = "demo.LocalModel",
            classKind = ClassKind.CLASS,
            file = sourceFile,
            declarations = { listOf(payload) },
            superTypes = {
                listOf(
                    typeReference(type(objectDeclaration)),
                    typeReference(middleStringType),
                )
            },
            annotations = sequenceOf(annotation(entity, emptyList())),
        )
        val classesByName = listOf(
            stringDeclaration,
            instantDeclaration,
            mappedSuperclass,
            entity,
            embeddable,
            semanticMarker,
            validationRule,
            externalBase,
            externalMiddle,
            externalEmbeddable,
            objectDeclaration,
        ).associateBy { declaration -> requireNotNull(declaration.qualifiedName).asString() } +
            ("java.lang.String" to stringDeclaration)

        val workspace = listOf(localModel).toLsiWorkspace(
            resolver(classesByName = classesByName),
            frontendOptions,
            fileScopes = listOfNotNull(localModel.containingFile).toKspLsiFileScopePlan().validScopes,
        )

        val externalBaseId = LsiSymbolId.type("external.ExternalBase")
        val externalSnapshot = assertIs<LsiTypeDeclaration>(workspace[externalBaseId])
        assertTrue(externalSnapshot.annotations.any { annotation ->
            annotation.type == LsiSymbolId.type(MAPPED_SUPERCLASS_ANNOTATION)
        })
        assertEquals(
            externalSnapshot.typeParameters.single().id,
            assertIs<LsiTypeParameterRef>(workspace.requireProperty(externalBaseId, "value").type).parameterId,
        )
        val validationSnapshot = assertIs<LsiTypeDeclaration>(
            workspace[LsiSymbolId.type("external.ValidationRule")],
        )
        assertTrue(validationSnapshot.annotations.any { annotation ->
            annotation.type == LsiSymbolId.type("external.SemanticMarker")
        })
        assertIs<LsiTypeDeclaration>(workspace[LsiSymbolId.type("external.SemanticMarker")])
        val externalMiddleId = LsiSymbolId.type("external.ExternalMiddle")
        assertTrue(assertIs<LsiTypeDeclaration>(workspace[externalMiddleId]).memberIds.isEmpty())
        assertTrue(workspace.declarationsOfType<LsiProperty>().none { property ->
            property.ownerId == externalMiddleId
        })
        val externalEmbeddableId = LsiSymbolId.type("external.ExternalValue")
        val externalEmbeddableSnapshot = assertIs<LsiTypeDeclaration>(workspace[externalEmbeddableId])
        assertTrue(externalEmbeddableSnapshot.annotations.any { annotation ->
            annotation.type == LsiSymbolId.type(EMBEDDABLE_ANNOTATION)
        })
        assertEquals("label", workspace.requireProperty(externalEmbeddableId, "label").name)
        val instantId = LsiSymbolId.type("java.time.Instant")
        val instantSnapshot = assertIs<LsiTypeDeclaration>(workspace[instantId])
        assertTrue(instantSnapshot.memberIds.isEmpty())
        assertEquals(null, instantSnapshot.origin.source)
        assertTrue(workspace.declarationsOfType<LsiProperty>().none { property ->
            property.ownerId == instantId
        })
        assertNotNull(workspace.typeHierarchyEntry(instantId))
        val objectId = LsiSymbolId.type("java.lang.Object")
        val objectSnapshot = assertIs<LsiTypeDeclaration>(workspace[objectId])
        assertTrue(objectSnapshot.memberIds.isEmpty())
        assertTrue(objectSnapshot.superTypes.isEmpty())
        assertTrue(workspace.declarationsOfType<LsiProperty>().none { property ->
            property.ownerId == objectId && property.name == "class"
        })
    }

    @Test
    fun `freezes suspend extension function semantics`() {
        val sourceFile = file("/workspace/src/main/kotlin/demo/Extensions.kt")
        val stringDeclaration = classDeclaration(
            qualifiedName = "kotlin.String",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        lateinit var service: KSClassDeclaration
        val extension = function(
            name = "load",
            parent = { service },
            parameters = emptyList(),
            modifiers = setOf(Modifier.OPEN, Modifier.SUSPEND),
            extensionReceiver = typeReference(type(stringDeclaration)),
            file = sourceFile,
        )
        service = classDeclaration(
            qualifiedName = "demo.Service",
            classKind = ClassKind.CLASS,
            file = sourceFile,
            declarations = { listOf(extension) },
        )

        val function = listOf(service)
            .toLsiWorkspace(
                resolver = resolver(),
                frontendOptions = frontendOptions,
                fileScopes = listOfNotNull(service.containingFile).toKspLsiFileScopePlan().validScopes,
            )
            .declarationsOfType<LsiFunction>()
            .single()

        assertTrue(function.suspending)
        assertEquals(
            LsiSymbolId.type("java.lang.String"),
            assertIs<LsiDeclaredType>(function.receiverType).declarationId,
        )
    }

    @Test
    fun `uses erased callable signatures for generic and vararg overloads`() {
        val sourceFile = file("/workspace/src/main/kotlin/demo/Factory.kt")
        lateinit var factory: KSClassDeclaration
        lateinit var fixedFunction: KSFunctionDeclaration
        val fixedTypeParameter = typeParameter(
            name = "E",
            parent = { fixedFunction },
            bounds = { emptyList() },
        )
        fixedFunction = function(
            name = "of",
            parent = { factory },
            parameters = listOf(
                valueParameter(
                    name = "first",
                    parent = { fixedFunction },
                    type = typeReference(type(fixedTypeParameter)),
                    file = sourceFile,
                ),
                valueParameter(
                    name = "second",
                    parent = { fixedFunction },
                    type = typeReference(type(fixedTypeParameter)),
                    file = sourceFile,
                ),
            ),
            typeParameters = { listOf(fixedTypeParameter) },
            file = sourceFile,
        )

        lateinit var varargFunction: KSFunctionDeclaration
        val varargTypeParameter = typeParameter(
            name = "E",
            parent = { varargFunction },
            bounds = { emptyList() },
        )
        varargFunction = function(
            name = "of",
            parent = { factory },
            parameters = listOf(
                valueParameter(
                    name = "first",
                    parent = { varargFunction },
                    type = typeReference(type(varargTypeParameter)),
                    file = sourceFile,
                ),
                valueParameter(
                    name = "rest",
                    parent = { varargFunction },
                    type = typeReference(type(varargTypeParameter)),
                    file = sourceFile,
                    vararg = true,
                ),
            ),
            typeParameters = { listOf(varargTypeParameter) },
            file = sourceFile,
        )
        factory = classDeclaration(
            qualifiedName = "demo.Factory",
            classKind = ClassKind.CLASS,
            file = sourceFile,
            declarations = { listOf(fixedFunction, varargFunction) },
        )

        val ownerId = LsiSymbolId.type("demo.Factory")
        val functions = listOf(factory)
            .toLsiWorkspace(
                resolver = resolver(),
                frontendOptions = frontendOptions,
                fileScopes = listOfNotNull(factory.containingFile).toKspLsiFileScopePlan().validScopes,
            )
            .declarationsOfType<LsiFunction>()

        assertEquals(
            setOf(
                LsiSymbolId.function(
                    ownerId,
                    "of",
                    listOf(
                        "parameter:method:of:0:type:java.lang.Object",
                        "parameter:method:of:0:type:java.lang.Object",
                    ),
                ),
                LsiSymbolId.function(
                    ownerId,
                    "of",
                    listOf(
                        "parameter:method:of:0:type:java.lang.Object",
                        "array:parameter:method:of:0:type:java.lang.Object",
                    ),
                ),
            ),
            functions.mapTo(linkedSetOf(), LsiFunction::id),
        )
        assertTrue(functions.single { function -> function.parameters.last().vararg }.parameters.last().vararg)
    }

    @Test
    fun `uses generic upper-bound erasure in callable ids`() {
        val sourceFile = file("/workspace/src/main/kotlin/demo/BoundedFactory.kt")
        val numberDeclaration = classDeclaration(
            qualifiedName = "kotlin.Number",
            classKind = ClassKind.CLASS,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        val charSequenceDeclaration = classDeclaration(
            qualifiedName = "kotlin.CharSequence",
            classKind = ClassKind.INTERFACE,
            origin = Origin.KOTLIN_LIB,
            file = null,
        )
        lateinit var factory: KSClassDeclaration
        lateinit var numberFunction: KSFunctionDeclaration
        val numberTypeParameter = typeParameter(
            name = "T",
            parent = { numberFunction },
            bounds = { listOf(typeReference(type(numberDeclaration))) },
        )
        numberFunction = function(
            name = "convert",
            parent = { factory },
            parameters = listOf(
                valueParameter(
                    name = "value",
                    parent = { numberFunction },
                    type = typeReference(type(numberTypeParameter)),
                    file = sourceFile,
                ),
            ),
            typeParameters = { listOf(numberTypeParameter) },
            file = sourceFile,
        )

        lateinit var textFunction: KSFunctionDeclaration
        val textTypeParameter = typeParameter(
            name = "T",
            parent = { textFunction },
            bounds = { listOf(typeReference(type(charSequenceDeclaration))) },
        )
        textFunction = function(
            name = "convert",
            parent = { factory },
            parameters = listOf(
                valueParameter(
                    name = "value",
                    parent = { textFunction },
                    type = typeReference(type(textTypeParameter)),
                    file = sourceFile,
                ),
            ),
            typeParameters = { listOf(textTypeParameter) },
            file = sourceFile,
        )
        factory = classDeclaration(
            qualifiedName = "demo.BoundedFactory",
            classKind = ClassKind.CLASS,
            file = sourceFile,
            declarations = { listOf(numberFunction, textFunction) },
        )

        val ownerId = LsiSymbolId.type("demo.BoundedFactory")
        val ids = listOf(factory)
            .toLsiWorkspace(
                resolver = resolver(),
                frontendOptions = frontendOptions,
                fileScopes = listOfNotNull(factory.containingFile).toKspLsiFileScopePlan().validScopes,
            )
            .declarationsOfType<LsiFunction>()
            .mapTo(linkedSetOf(), LsiFunction::id)

        assertEquals(
            setOf(
                LsiSymbolId.function(
                    ownerId,
                    "convert",
                    listOf("parameter:method:convert:0:type:java.lang.Number"),
                ),
                LsiSymbolId.function(
                    ownerId,
                    "convert",
                    listOf("parameter:method:convert:0:type:java.lang.CharSequence"),
                ),
            ),
            ids,
        )
    }

    @Test
    fun `rejects symbols that are invalid in current round before reading declarations`() {
        var declarationsRead = false
        val invalidType = classDeclaration(
            qualifiedName = "demo.Invalid",
            classKind = ClassKind.INTERFACE,
            file = file("/workspace/src/main/kotlin/demo/Invalid.kt"),
            declarations = {
                declarationsRead = true
                emptyList()
            },
            valid = false,
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            listOf(invalidType).toLsiWorkspace(
                resolver = resolver(),
                frontendOptions = frontendOptions,
                fileScopes = listOfNotNull(invalidType.containingFile).toKspLsiFileScopePlan().validScopes,
            )
        }

        assertTrue(exception.message.orEmpty().contains("current round"))
        assertFalse(declarationsRead)
    }

    private fun LsiWorkspace.requireProperty(ownerId: LsiSymbolId, name: String): LsiProperty {
        return declarationsOfType<LsiProperty>()
            .single { property -> property.ownerId == ownerId && property.name == name }
    }

    private fun LsiProperty.annotation(qualifiedName: String): site.addzero.lsi.model.LsiAnnotation {
        return annotations.single { annotation -> annotation.type == LsiSymbolId.type(qualifiedName) }
    }

    private fun assertSourceTypeUseAnnotation(type: site.addzero.lsi.model.LsiTypeRef) {
        val annotation = type.annotations.single()
        assertEquals(LsiSymbolId.type("demo.TypeUse"), annotation.type)
        assertEquals(
            LsiSymbolId.type("demo.Marker"),
            assertIs<LsiDeclaredType>(
                assertIs<LsiAnnotationValue.ClassValue>(
                    annotation.arguments.getValue("type").value,
                ).type,
            ).declarationId,
        )
    }

    private fun resolver(
        classesByName: Map<String, KSClassDeclaration> = emptyMap(),
        overrides: (KSDeclaration, KSDeclaration, KSClassDeclaration) -> Boolean = { _, _, _ -> false },
    ): Resolver {
        return proxy("Resolver") { method, arguments ->
            when (method.name) {
                "overrides" -> if (arguments.size == 3) {
                    overrides(
                        arguments[0] as KSDeclaration,
                        arguments[1] as KSDeclaration,
                        arguments[2] as KSClassDeclaration,
                    )
                } else {
                    false
                }
                "getJvmCheckedException" -> emptySequence<KSType>()
                "getKSNameFromString" -> name(arguments[0] as String)
                "getClassDeclarationByName" -> {
                    val qualifiedName = (arguments[0] as KSName).asString()
                    classesByName[qualifiedName]
                }
                "createKSTypeReferenceFromKSType" -> typeReference(arguments[0] as KSType)
                "getTypeArgument" -> typeArgument(
                    type = (arguments[0] as KSTypeReference).resolve(),
                    variance = arguments[1] as Variance,
                )
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun file(
        path: String,
        packageName: String = "demo",
        annotations: Sequence<KSAnnotation> = emptySequence(),
    ): KSFile {
        return proxy("KSFile($path)") { method, _ ->
            when (method.name) {
                "getFilePath" -> path
                "getFileName" -> path.substringAfterLast('/')
                "getPackageName" -> name(packageName)
                "getDeclarations" -> emptySequence<KSDeclaration>()
                "getAnnotations" -> annotations
                "getOrigin" -> Origin.KOTLIN
                "getLocation" -> FileLocation(path, 1)
                "accept" -> true
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun classDeclaration(
        qualifiedName: String,
        classKind: ClassKind,
        origin: Origin = Origin.KOTLIN,
        file: KSFile?,
        parent: () -> KSDeclaration? = { null },
        typeParameters: () -> List<KSTypeParameter> = { emptyList() },
        declarations: () -> List<KSDeclaration> = { emptyList() },
        superTypes: () -> List<KSTypeReference> = { emptyList() },
        annotations: Sequence<KSAnnotation> = emptySequence(),
        modifiers: Set<Modifier> = emptySet(),
        documentation: String? = null,
        line: Int = 1,
        valid: Boolean = true,
    ): KSClassDeclaration {
        lateinit var declaration: KSClassDeclaration
        declaration = proxy("KSClassDeclaration($qualifiedName)") { method, _ ->
            when (method.name) {
                "getSimpleName" -> name(qualifiedName.substringAfterLast('.'))
                "getQualifiedName" -> name(qualifiedName)
                "getPackageName" -> name(qualifiedName.substringBeforeLast('.', ""))
                "getClassKind" -> classKind
                "getOrigin" -> origin
                "getContainingFile" -> file
                "getParentDeclaration", "getParent" -> parent()
                "getTypeParameters" -> typeParameters()
                "getDeclarations" -> declarations().asSequence()
                "getSuperTypes" -> superTypes().asSequence()
                "getAnnotations" -> annotations
                "getModifiers" -> modifiers
                "getDocString" -> documentation
                "getLocation" -> file?.let { FileLocation(it.filePath, line) }
                "asType", "asStarProjectedType" -> type(declaration)
                "accept" -> valid
                "isCompanionObject" -> false
                else -> defaultValue(method.returnType)
            }
        }
        return declaration
    }

    private fun unresolvedAnnotationDeclaration(name: String): KSClassDeclaration {
        return proxy("KSClassDeclaration(kotlin.$name)") { method, _ ->
            when (method.name) {
                "getSimpleName" -> name(name)
                "getQualifiedName" -> null
                "getPackageName" -> name("kotlin")
                "getClassKind" -> ClassKind.ANNOTATION_CLASS
                "getOrigin" -> Origin.KOTLIN_LIB
                "getContainingFile", "getParentDeclaration", "getParent", "getDocString" -> null
                "getTypeParameters" -> emptyList<KSTypeParameter>()
                "getDeclarations" -> emptySequence<KSDeclaration>()
                "getSuperTypes" -> emptySequence<KSTypeReference>()
                "getAnnotations" -> emptySequence<KSAnnotation>()
                "getModifiers" -> emptySet<Modifier>()
                "getLocation" -> com.google.devtools.ksp.symbol.NonExistLocation
                "accept" -> true
                "isCompanionObject" -> false
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun property(
        name: String,
        parent: () -> KSClassDeclaration,
        type: KSTypeReference,
        annotations: Sequence<KSAnnotation>,
        modifiers: Set<Modifier> = emptySet(),
        overridee: () -> KSPropertyDeclaration? = { null },
        origin: Origin = Origin.KOTLIN,
        mutable: Boolean = false,
        documentation: String? = null,
        file: KSFile?,
        line: Int,
    ): KSPropertyDeclaration {
        lateinit var declaration: KSPropertyDeclaration
        declaration = proxy("KSPropertyDeclaration($name)") { method, _ ->
            when (method.name) {
                "getSimpleName", "getQualifiedName" -> name(name)
                "getPackageName" -> name("demo")
                "getParentDeclaration", "getParent" -> parent()
                "getContainingFile" -> file
                "getTypeParameters" -> emptyList<KSTypeParameter>()
                "getType" -> type
                "getAnnotations" -> annotations
                "getModifiers" -> modifiers
                "getOrigin" -> origin
                "getLocation" -> file?.let { sourceFile -> FileLocation(sourceFile.filePath, line) }
                    ?: com.google.devtools.ksp.symbol.NonExistLocation
                "getDocString" -> documentation
                "getGetter", "getSetter", "getExtensionReceiver" -> null
                "isMutable" -> mutable
                "getHasBackingField", "isDelegated" -> false
                "findOverridee" -> overridee()
                "accept" -> true
                else -> defaultValue(method.returnType)
            }
        }
        return declaration
    }

    private fun function(
        name: String,
        parent: () -> KSClassDeclaration,
        parameters: List<KSValueParameter>,
        typeParameters: () -> List<KSTypeParameter> = { emptyList() },
        annotations: Sequence<KSAnnotation> = emptySequence(),
        modifiers: Set<Modifier> = emptySet(),
        functionKind: FunctionKind = FunctionKind.MEMBER,
        extensionReceiver: KSTypeReference? = null,
        returnType: KSTypeReference? = null,
        origin: Origin = Origin.KOTLIN,
        file: KSFile,
    ): KSFunctionDeclaration {
        return proxy("KSFunctionDeclaration($name)") { method, _ ->
            when (method.name) {
                "getSimpleName", "getQualifiedName" -> name(name)
                "getPackageName" -> name("demo")
                "getParentDeclaration", "getParent" -> parent()
                "getContainingFile" -> file
                "getParameters" -> parameters
                "getTypeParameters" -> typeParameters()
                "getAnnotations" -> annotations
                "getModifiers" -> modifiers
                "getFunctionKind" -> functionKind
                "getOrigin" -> origin
                "getLocation" -> FileLocation(file.filePath, 1)
                "getDocString" -> null
                "getReturnType" -> returnType
                "getExtensionReceiver" -> extensionReceiver
                "accept" -> true
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun valueParameter(
        name: String,
        parent: () -> KSFunctionDeclaration,
        type: KSTypeReference,
        annotations: Sequence<KSAnnotation> = emptySequence(),
        file: KSFile,
        vararg: Boolean = false,
    ): KSValueParameter {
        return proxy("KSValueParameter($name)") { method, _ ->
            when (method.name) {
                "getName" -> name(name)
                "getParent", "getParentDeclaration" -> parent()
                "getType" -> type
                "getAnnotations" -> annotations
                "getOrigin" -> Origin.KOTLIN
                "getLocation" -> FileLocation(file.filePath, 1)
                "isVararg" -> vararg
                "getHasDefault", "isNoInline", "isCrossInline", "isVal", "isVar" -> false
                "accept" -> true
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun typeParameter(
        name: String,
        parent: () -> KSDeclaration,
        bounds: () -> List<KSTypeReference>,
    ): KSTypeParameter {
        return proxy("KSTypeParameter($name)") { method, _ ->
            when (method.name) {
                "getName", "getSimpleName" -> name(name)
                "getQualifiedName" -> null
                "getPackageName" -> name("")
                "getParentDeclaration", "getParent" -> parent()
                "getTypeParameters" -> emptyList<KSTypeParameter>()
                "getBounds" -> bounds().asSequence()
                "getVariance" -> Variance.INVARIANT
                "getAnnotations" -> emptySequence<KSAnnotation>()
                "getModifiers" -> emptySet<Modifier>()
                "getOrigin" -> Origin.KOTLIN
                "getLocation" -> parent().location
                "isReified" -> false
                "accept" -> true
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun type(
        declaration: KSDeclaration,
        arguments: List<KSTypeArgument> = emptyList(),
        nullability: Nullability = Nullability.NOT_NULL,
        error: Boolean = false,
        markedNullable: Boolean = nullability == Nullability.NULLABLE,
    ): KSType {
        lateinit var type: KSType
        type = proxy("KSType(${declaration.simpleName.asString()})") { method, methodArguments ->
            when (method.name) {
                "getDeclaration" -> declaration
                "getArguments" -> arguments
                "getNullability" -> nullability
                "getAnnotations" -> emptySequence<KSAnnotation>()
                "isMarkedNullable" -> markedNullable
                "isError" -> error
                "isFunctionType", "isSuspendFunctionType" -> false
                "makeNullable" -> type(declaration, arguments, Nullability.NULLABLE, error, true)
                "makeNotNullable" -> type(declaration, arguments, Nullability.NOT_NULL, error, false)
                "replace" -> {
                    @Suppress("UNCHECKED_CAST")
                    val replacementArguments = methodArguments[0] as List<KSTypeArgument>
                    type(declaration, replacementArguments, nullability, error, markedNullable)
                }
                "starProjection" -> type
                else -> defaultValue(method.returnType)
            }
        }
        return type
    }

    private fun typeReference(
        type: KSType,
        annotations: Sequence<KSAnnotation> = emptySequence(),
        sourceArguments: List<KSTypeArgument>? = null,
    ): KSTypeReference {
        return proxy("KSTypeReference($type)") { method, _ ->
            when (method.name) {
                "resolve" -> type
                "getElement" -> sourceArguments?.let(::referenceElement)
                "getAnnotations" -> annotations
                "getModifiers" -> emptySet<Modifier>()
                "getOrigin" -> Origin.KOTLIN
                "getLocation" -> type.declaration.location
                "getParent" -> type.declaration
                "accept" -> true
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun typeArgument(
        type: KSType,
        variance: Variance = Variance.INVARIANT,
    ): KSTypeArgument {
        return typeArgument(typeReference(type), variance)
    }

    private fun typeArgument(
        reference: KSTypeReference,
        variance: Variance = Variance.INVARIANT,
    ): KSTypeArgument {
        return proxy("KSTypeArgument($reference)") { method, _ ->
            when (method.name) {
                "getType" -> reference
                "getVariance" -> variance
                "getAnnotations" -> emptySequence<KSAnnotation>()
                "getOrigin" -> Origin.KOTLIN
                "getLocation" -> reference.location
                "getParent" -> reference.parent
                "accept" -> true
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun referenceElement(arguments: List<KSTypeArgument>): KSReferenceElement {
        return proxy("KSReferenceElement") { method, _ ->
            when (method.name) {
                "getTypeArguments" -> arguments
                "getOrigin" -> Origin.KOTLIN
                "accept" -> true
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun annotation(
        type: KSClassDeclaration,
        arguments: List<KSValueArgument>,
        defaultArguments: List<KSValueArgument> = emptyList(),
        useSiteTarget: AnnotationUseSiteTarget? = null,
        annotationType: KSTypeReference? = null,
        origin: Origin = Origin.KOTLIN,
    ): KSAnnotation {
        val resolvedAnnotationType = annotationType ?: typeReference(type(type))
        return proxy("KSAnnotation(${type.qualifiedName?.asString()})") { method, _ ->
            when (method.name) {
                "getAnnotationType" -> resolvedAnnotationType
                "getArguments" -> arguments
                "getDefaultArguments" -> defaultArguments
                "getShortName" -> type.simpleName
                "getUseSiteTarget" -> useSiteTarget
                "getOrigin" -> origin
                "getLocation" -> type.location
                "getParent" -> type.parent
                "accept" -> true
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun valueArgument(name: String, value: Any): KSValueArgument {
        return proxy("KSValueArgument($name)") { method, _ ->
            when (method.name) {
                "getName" -> name(name)
                "getValue" -> value
                "isSpread" -> false
                "getAnnotations" -> emptySequence<KSAnnotation>()
                "getOrigin" -> Origin.KOTLIN
                "getLocation" -> com.google.devtools.ksp.symbol.NonExistLocation
                "accept" -> true
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun name(value: String): KSName {
        return proxy("KSName($value)") { method, _ ->
            when (method.name) {
                "asString" -> value
                "getQualifier" -> value.substringBeforeLast('.', "")
                "getShortName" -> value.substringAfterLast('.')
                else -> defaultValue(method.returnType)
            }
        }
    }

    private inline fun <reified T> proxy(
        label: String,
        noinline handler: (Method, Array<out Any?>) -> Any?,
    ): T {
        lateinit var instance: Any
        instance = Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { _, method, arguments ->
            val safeArguments = arguments ?: emptyArray()
            when (method.name) {
                "equals" -> instance === safeArguments.firstOrNull()
                "hashCode" -> System.identityHashCode(instance)
                "toString" -> label
                else -> handler(method, safeArguments)
            }
        }
        @Suppress("UNCHECKED_CAST")
        return instance as T
    }

    private fun defaultValue(returnType: Class<*>): Any? {
        return when {
            returnType == Boolean::class.javaPrimitiveType -> false
            returnType == Int::class.javaPrimitiveType -> 0
            returnType == Long::class.javaPrimitiveType -> 0L
            returnType == Float::class.javaPrimitiveType -> 0F
            returnType == Double::class.javaPrimitiveType -> 0.0
            returnType == Char::class.javaPrimitiveType -> '\u0000'
            Sequence::class.java.isAssignableFrom(returnType) -> emptySequence<Any>()
            List::class.java.isAssignableFrom(returnType) -> emptyList<Any>()
            Set::class.java.isAssignableFrom(returnType) -> emptySet<Any>()
            else -> null
        }
    }

    private fun testLsiFrontendOptions(keepIsPrefix: Boolean = false): LsiFrontendOptions {
        val managedTypeAnnotationTypeIds = setOf(
            LsiSymbolId.type(ENTITY_ANNOTATION),
            LsiSymbolId.type(MAPPED_SUPERCLASS_ANNOTATION),
            LsiSymbolId.type(EMBEDDABLE_ANNOTATION),
        )
        return LsiFrontendOptions(
            keepJavaBooleanGetterIsPrefix = keepIsPrefix,
            fullExternalDeclarationAnnotationTypeIds = managedTypeAnnotationTypeIds,
            documentationConvention = LsiFrontendDocumentationConvention(
                annotationTypeId = LsiSymbolId.type(DESCRIPTION_ANNOTATION),
                generatedPeer = LsiGeneratedPeerDocumentationConvention(
                    ownerAnnotationTypeIds = managedTypeAnnotationTypeIds,
                    typeSuffix = "Peer",
                ),
            ),
        )
    }

    private companion object {
        const val DESCRIPTION_ANNOTATION = "test.lsi.Description"
        const val DEFAULT_ANNOTATION = "test.lsi.Default"
        const val ENTITY_ANNOTATION = "test.lsi.Entity"
        const val MAPPED_SUPERCLASS_ANNOTATION = "test.lsi.MappedSuperclass"
        const val EMBEDDABLE_ANNOTATION = "test.lsi.Embeddable"
    }
}
