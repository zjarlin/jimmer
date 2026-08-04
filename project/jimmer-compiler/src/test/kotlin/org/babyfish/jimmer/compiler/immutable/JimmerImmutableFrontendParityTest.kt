package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.jimmer.ApplicationDefaultStrategy
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaDependency
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutableDefault
import site.addzero.lsi.jimmer.ImmutablePrecompileException
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.InheritanceStrategy
import site.addzero.lsi.jimmer.JoinedTableDissociateAction
import site.addzero.lsi.jimmer.TransientResolver
import site.addzero.lsi.jimmer.fingerprint
import site.addzero.lsi.jimmer.jimmerTypeSignature
import site.addzero.lsi.jimmer.normalizedSnapshot
import site.addzero.lsi.jimmer.toImmutableSchema
import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import org.babyfish.jimmer.compiler.lsi.apt.toLsiWorkspace
import org.babyfish.jimmer.compiler.lsi.ksp.toLsiWorkspace
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableFrontendParityTest {

    @Test
    fun `binary draft documentation produces identical immutable fingerprints`() {
        val apt = compileApt(
            source = """
                package demo;

                interface Consumer {
                    demo.binary.BinaryBook book();
                }
            """.trimIndent(),
            libraries = listOf(compileJavaDocumentationLibrary()),
        )
        val ksp = compileKsp(
            source = """
                package demo

                interface Consumer {
                    val book: demo.binary.BinaryBook
                }
            """.trimIndent(),
            libraries = listOf(compileKotlinDocumentationLibrary()),
        )

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        val aptBook = aptSchema.types.single { type -> type.qualifiedName == "demo.binary.BinaryBook" }
        val kspBook = kspSchema.types.single { type -> type.qualifiedName == "demo.binary.BinaryBook" }
        assertEquals("binary type", aptBook.documentation)
        assertEquals("binary property", aptBook.props.single { prop -> prop.name == "name" }.documentation)
        assertEquals(aptBook.documentation, kspBook.documentation)
        assertEquals(
            aptBook.props.single { prop -> prop.name == "name" }.documentation,
            kspBook.props.single { prop -> prop.name == "name" }.documentation,
        )
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
    }

    @Test
    fun `real apt and ksp frontends produce identical inheritance metadata`() {
        val apt = compileApt(VALID_JAVA_SOURCE)
        val ksp = compileKsp(VALID_KOTLIN_SOURCE)

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
        assertEquals(
            assertNotNull(apt.draftCodegenSchema).normalizedSnapshot(),
            assertNotNull(ksp.draftCodegenSchema).normalizedSnapshot(),
        )

        val root = aptSchema.types.single { type -> type.qualifiedName == "demo.Asset" }
        assertEquals(InheritanceStrategy.JOINED, root.inheritanceStrategy)
        assertEquals(JoinedTableDissociateAction.LAX, root.joinedTableDissociateAction)
        assertEquals(LsiSymbolId.type("demo.Asset"), root.inheritanceRootTypeId)
        assertFalse(root.instantiable)
        assertEquals(LsiSymbolId.property(root.id, "kind"), root.discriminatorPropId)
        assertEquals(LsiSymbolId.property(root.id, "id"), root.idPropId)

        val derived = aptSchema.types.single { type -> type.qualifiedName == "demo.Book" }
        assertEquals(root.id, derived.inheritanceRootTypeId)
        assertEquals("BOOK", derived.discriminatorValue)
        assertTrue(derived.instantiable)
        assertEquals(LsiSymbolId.property(derived.id, "kind"), derived.discriminatorPropId)
        assertEquals(LsiSymbolId.property(derived.id, "id"), derived.idPropId)
        assertTrue(derived.props.any { prop ->
            prop.name == "createdBy" && prop.inherited
        })
        val catalogBook = aptSchema.types.single { type -> type.qualifiedName == "demo.CatalogBook" }
        val storeProp = catalogBook.props.single { prop -> prop.name == "store" }
        val storeIdProp = catalogBook.props.single { prop -> prop.name == "storeId" }
        assertEquals(
            ImmutableView.Id(
                basePropId = storeProp.id,
                targetIdPropId = LsiSymbolId.property(LsiSymbolId.type("demo.Store"), "id"),
            ),
            storeIdProp.view,
        )
        val authorsProp = catalogBook.props.single { prop -> prop.name == "authors" }
        assertEquals(
            ImmutableView.ManyToMany(
                basePropId = LsiSymbolId.property(catalogBook.id, "links"),
                deeperPropId = LsiSymbolId.property(LsiSymbolId.type("demo.BookAuthor"), "author"),
            ),
            authorsProp.view,
        )
        val authorIdsProp = catalogBook.props.single { prop -> prop.name == "authorIds" }
        assertEquals(
            ImmutableView.Id(
                basePropId = authorsProp.id,
                targetIdPropId = LsiSymbolId.property(LsiSymbolId.type("demo.Author"), "id"),
            ),
            authorIdsProp.view,
        )
    }

    @Test
    fun `real apt and ksp frontends freeze identical identity slots`() {
        val apt = compileApt(IDENTITY_JAVA_SOURCE)
        val ksp = compileKsp(IDENTITY_KOTLIN_SOURCE)

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())

        val record = aptSchema.types.single { type -> type.qualifiedName == "demo.IdentityRecord" }
        assertEquals(LsiSymbolId.property(record.id, "id"), record.idPropId)
        assertEquals(LsiSymbolId.property(record.id, "version"), record.versionPropId)
        assertEquals(LsiSymbolId.property(record.id, "deleted"), record.logicalDeletedPropId)
        assertEquals(
            ImmutableDefault.Application(
                annotationValue = null,
                strategy = ApplicationDefaultStrategy.LOGICAL_DELETED,
            ),
            record.props.single { prop -> prop.name == "deleted" }.defaultContract,
        )
        val timed = aptSchema.types.single { type -> type.qualifiedName == "demo.TimedRecord" }
        assertEquals(LsiSymbolId.property(timed.id, "deletedAt"), timed.logicalDeletedPropId)
        assertTrue(timed.props.single { prop -> prop.name == "deletedAt" }.nullable)
        assertEquals(
            ImmutableDefault.Application(
                annotationValue = null,
                strategy = ApplicationDefaultStrategy.LOGICAL_DELETED,
            ),
            timed.props.single { prop -> prop.name == "deletedAt" }.defaultContract,
        )
        val stateful = aptSchema.types.single { type -> type.qualifiedName == "demo.StatefulRecord" }
        assertEquals(LsiSymbolId.property(stateful.id, "state"), stateful.logicalDeletedPropId)
        assertTrue(stateful.props.single { prop -> prop.name == "state" }.nullable)
        assertEquals(
            ImmutableDefault.Application(
                annotationValue = "ALIVE",
                strategy = ApplicationDefaultStrategy.DECLARED_VALUE,
            ),
            stateful.props.single { prop -> prop.name == "state" }.defaultContract,
        )
    }

    @Test
    fun `real apt frontend rejects boxed logical-deleted primitives`() {
        val result = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.LogicalDeleted;

                @Entity
                interface BoxedDeletedRecord {
                    @Id
                    long id();

                    @LogicalDeleted
                    Boolean deleted();
                }
            """.trimIndent()
        )

        assertNull(result.schema)
        assertTrue(
            result.diagnostic.orEmpty().contains("primitive Boolean or Int"),
            result.diagnostic,
        )
    }

    @Test
    fun `real apt and ksp frontends reject entity without id identically`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.sql.Entity;

                @Entity
                interface MissingIdRecord {
                    String name();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity

                @Entity
                interface MissingIdRecord {
                    val name: String
                }
            """.trimIndent()
        )

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertTrue(apt.diagnostic.orEmpty().contains("must have exactly one"))
    }

    @Test
    fun `real apt and ksp frontends accept concrete immutable helper functions`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                interface FunctionRecord {
                    @Id
                    long id();

                    default int normalize(int value) {
                        return value;
                    }

                    default void touch() {}

                    default <T> T echo(T value) throws Exception {
                        return value;
                    }

                    static int twice(int value) {
                        return value * 2;
                    }
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface FunctionRecord {
                    @Id
                    val id: Long

                    fun normalize(value: Int): Int = value

                    fun touch() {}

                    @Throws(Exception::class)
                    fun <T> echo(value: T): T = value
                }
            """.trimIndent()
        )

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        assertEquals(assertNotNull(apt.schema).normalizedSnapshot(), assertNotNull(ksp.schema).normalizedSnapshot())
    }

    @Test
    fun `real apt default formula and ksp calculated property remain properties`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.Formula;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                interface FormulaRecord {
                    @Id
                    long id();

                    String firstName();

                    String lastName();

                    @Formula(dependencies = {"firstName", "lastName"})
                    default String fullName() {
                        return firstName() + " " + lastName();
                    }
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.Formula
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface FormulaRecord {
                    @Id
                    val id: Long

                    val firstName: String

                    val lastName: String

                    @Formula(dependencies = ["firstName", "lastName"])
                    val fullName: String
                        get() = "${'$'}firstName ${'$'}lastName"
                }
            """.trimIndent()
        )

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(
            FormulaKind.LANGUAGE,
            aptSchema.types.single().props.single { prop -> prop.name == "fullName" }.formulaKind,
        )
    }

    @Test
    fun `real apt and ksp frontends reject abstract immutable functions identically`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                interface FunctionRecord {
                    @Id
                    long id();

                    int normalize(int value);
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface FunctionRecord {
                    @Id
                    val id: Long

                    fun normalize(value: Int): Int
                }
            """.trimIndent()
        )

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertTrue(apt.diagnostic.orEmpty().contains("cannot declare abstract function 'normalize'"))
    }

    @Test
    fun `real apt and ksp frontends reject jimmer annotations on concrete functions identically`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.client.ApiIgnore;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                interface FunctionRecord {
                    @Id
                    long id();

                    @ApiIgnore
                    default int normalize(int value) {
                        return value;
                    }
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.client.ApiIgnore
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface FunctionRecord {
                    @Id
                    val id: Long

                    @ApiIgnore
                    fun normalize(value: Int): Int = value
                }
            """.trimIndent()
        )

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertTrue(
            apt.diagnostic.orEmpty().contains(
                "Jimmer annotation @org.babyfish.jimmer.client.ApiIgnore"
            )
        )
    }

    @Test
    fun `real apt and ksp frontends produce identical overridden property annotations`() {
        val apt = compileApt(OVERRIDDEN_PROPERTY_JAVA_SOURCE)
        val ksp = compileKsp(OVERRIDDEN_PROPERTY_KOTLIN_SOURCE)

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
        val aptDraftSchema = assertNotNull(apt.draftCodegenSchema)
        val kspDraftSchema = assertNotNull(ksp.draftCodegenSchema)
        assertEquals(
            aptDraftSchema.normalizedSnapshot(),
            kspDraftSchema.normalizedSnapshot(),
        )

        val aptStatus = aptSchema.types
            .single { type -> type.qualifiedName == "demo.OverrideEntity" }
            .props
            .single { prop -> prop.name == "status" }
        val kspStatus = kspSchema.types
            .single { type -> type.qualifiedName == "demo.OverrideEntity" }
            .props
            .single { prop -> prop.name == "status" }
        assertEquals(aptStatus.type, kspStatus.type)
        assertTrue(aptStatus.overridden)
        assertEquals("1", aptStatus.annotationString(DEFAULT, "value"))
        assertEquals(
            ImmutableDefault.Application(
                annotationValue = "1",
                strategy = ApplicationDefaultStrategy.DECLARED_VALUE,
            ),
            aptStatus.defaultContract,
        )
        assertEquals("BASE_STATUS", aptStatus.annotationString(COLUMN, "name"))
        assertEquals("child", aptStatus.annotationString(LsiSymbolId.type("demo.Marker"), "value"))
        assertTrue(
            aptStatus.annotations.any { annotation ->
                annotation.type == LsiSymbolId.type("demo.ParentMarker")
            }
        )
        assertEquals(1, aptStatus.annotations.count { annotation -> annotation.type == DEFAULT })
        assertEquals(1, aptStatus.annotations.count { annotation -> annotation.type == COLUMN })
        assertEquals(
            1,
            aptStatus.annotations.count { annotation ->
                annotation.type == LsiSymbolId.type("demo.Marker")
            },
        )
        assertFalse(aptStatus.annotations.any { annotation -> annotation.type == JAVA_OVERRIDE })

        val draftType = aptDraftSchema.typesById.getValue(LsiSymbolId.type("demo.OverrideEntity"))
        val statusPlan = draftType.propsById.getValue(aptStatus.id)
        val idPlan = draftType.propsById.getValue(
            aptSchema.typesById.getValue(draftType.typeId).idPropId!!,
        )
        assertEquals(0, statusPlan.slotIndex)
        assertEquals(JimmerImmutableDraftPropRole.REDEFINED, statusPlan.role)
        assertTrue(statusPlan.genericSourceTarget)
        assertEquals(JimmerImmutableDraftValueState.VALUE_ONLY, statusPlan.valueState)
        assertEquals(1, idPlan.slotIndex)
        assertEquals(JimmerImmutableDraftPropRole.DECLARED, idPlan.role)
        assertEquals(listOf(statusPlan.propId), draftType.runtimeRedefinedPropIds)
        assertEquals(listOf(idPlan.propId), draftType.runtimeDeclaredPropIds)
    }

    @Test
    fun `real apt and ksp frontends validate every direct mapped superclass override`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;
                import org.jetbrains.annotations.Nullable;

                @MappedSuperclass
                interface AlignedBase {
                    String value();
                }

                @MappedSuperclass
                interface NullableBase<T> {
                    @Nullable
                    T value();
                }

                @Entity
                interface MultiBaseEntity extends AlignedBase, NullableBase<String> {
                    @Id
                    long id();

                    @Override
                    String value();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.MappedSuperclass

                @MappedSuperclass
                interface AlignedBase {
                    val value: String
                }

                @MappedSuperclass
                interface NullableBase<T : Any> {
                    val value: T?
                }

                @Entity
                interface MultiBaseEntity : AlignedBase, NullableBase<String> {
                    @Id
                    val id: Long

                    override val value: String
                }
            """.trimIndent()
        )

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertTrue(apt.diagnostic.orEmpty().contains("nullability"))
    }

    @Test
    fun `real apt and ksp frontends reject an indirect override hidden by a direct override`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface AlignedBase {
                    String value();
                }

                @MappedSuperclass
                interface RootBase {
                    String value();
                }

                @MappedSuperclass
                interface MiddleBase extends RootBase {}

                @Entity
                interface MultiPathEntity extends AlignedBase, MiddleBase {
                    @Id
                    long id();

                    @Override
                    String value();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.MappedSuperclass

                @MappedSuperclass
                interface AlignedBase {
                    val value: String
                }

                @MappedSuperclass
                interface RootBase {
                    val value: String
                }

                @MappedSuperclass
                interface MiddleBase : RootBase

                @Entity
                interface MultiPathEntity : AlignedBase, MiddleBase {
                    @Id
                    val id: Long

                    override val value: String
                }
            """.trimIndent()
        )

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertTrue(apt.diagnostic.orEmpty().contains("mapped superclass of an entity"))
    }

    @Test
    fun `real apt and ksp frontends reject invalid property overrides identically`() {
        assertOverrideRejectionParity(
            label = "mapped superclass override",
            javaSource = """
                package demo;

                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface RootBase {
                    String name();
                }

                @MappedSuperclass
                interface InvalidBase extends RootBase {
                    @Override
                    String name();
                }
            """.trimIndent(),
            kotlinSource = """
                package demo

                import org.babyfish.jimmer.sql.MappedSuperclass

                @MappedSuperclass
                interface RootBase {
                    val name: String
                }

                @MappedSuperclass
                interface InvalidBase : RootBase {
                    override val name: String
                }
            """.trimIndent(),
            expected = "mapped superclass of an entity",
        )
        assertOverrideRejectionParity(
            label = "entity override",
            javaSource = """
                package demo;

                import org.babyfish.jimmer.sql.Discriminator;
                import org.babyfish.jimmer.sql.DiscriminatorValue;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.Inheritance;
                import org.babyfish.jimmer.sql.InheritanceType;

                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                interface RootEntity {
                    @Id
                    long id();

                    @Discriminator
                    String type();

                    String name();
                }

                @Entity
                @DiscriminatorValue("DERIVED")
                interface DerivedEntity extends RootEntity {
                    @Override
                    String name();
                }
            """.trimIndent(),
            kotlinSource = """
                package demo

                import org.babyfish.jimmer.sql.Discriminator
                import org.babyfish.jimmer.sql.DiscriminatorValue
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.Inheritance
                import org.babyfish.jimmer.sql.InheritanceType

                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                interface RootEntity {
                    @Id
                    val id: Long

                    @Discriminator
                    val type: String

                    val name: String
                }

                @Entity
                @DiscriminatorValue("DERIVED")
                interface DerivedEntity : RootEntity {
                    override val name: String
                }
            """.trimIndent(),
            expected = "mapped superclass of an entity",
        )
        assertOverrideRejectionParity(
            label = "generic resolved type change",
            javaSource = """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface GenericBase<T extends CharSequence> {
                    T value();
                }

                @Entity
                interface GenericMismatchEntity extends GenericBase<CharSequence> {
                    @Id
                    long id();

                    @Override
                    String value();
                }
            """.trimIndent(),
            kotlinSource = """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.MappedSuperclass

                @MappedSuperclass
                interface GenericBase<T : CharSequence> {
                    val value: T
                }

                @Entity
                interface GenericMismatchEntity : GenericBase<CharSequence> {
                    @Id
                    val id: Long

                    override val value: String
                }
            """.trimIndent(),
            expected = "resolved type",
        )
        assertOverrideRejectionParity(
            label = "list scalar category change",
            javaSource = """
                package demo;

                import java.util.List;
                import org.babyfish.jimmer.Scalar;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface ListBase {
                    List<String> tags();
                }

                @Entity
                interface ListMismatchEntity extends ListBase {
                    @Id
                    long id();

                    @Override
                    @Scalar
                    List<String> tags();
                }
            """.trimIndent(),
            kotlinSource = """
                package demo

                import org.babyfish.jimmer.Scalar
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.MappedSuperclass

                @MappedSuperclass
                interface ListBase {
                    val tags: List<String>
                }

                @Entity
                interface ListMismatchEntity : ListBase {
                    @Id
                    val id: Long

                    @Scalar
                    override val tags: List<String>
                }
            """.trimIndent(),
            expected = "list category",
        )
        assertOverrideRejectionParity(
            label = "primary mapping category change",
            javaSource = """
                package demo;

                import org.babyfish.jimmer.Formula;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface ScalarBase {
                    String name();
                }

                @Entity
                interface FormulaMismatchEntity extends ScalarBase {
                    @Id
                    long id();

                    @Override
                    @Formula(sql = "NAME")
                    String name();
                }
            """.trimIndent(),
            kotlinSource = """
                package demo

                import org.babyfish.jimmer.Formula
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.MappedSuperclass

                @MappedSuperclass
                interface ScalarBase {
                    val name: String
                }

                @Entity
                interface FormulaMismatchEntity : ScalarBase {
                    @Id
                    val id: Long

                    @Formula(sql = "NAME")
                    override val name: String
                }
            """.trimIndent(),
            expected = "primary mapping annotation",
        )
        assertOverrideRejectionParity(
            label = "formula kind change",
            javaSource = """
                package demo;

                import org.babyfish.jimmer.Formula;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface SqlFormulaBase {
                    @Formula(sql = "NAME")
                    String name();
                }

                @Entity
                interface LanguageFormulaEntity extends SqlFormulaBase {
                    @Id
                    long id();

                    @Override
                    @Formula(dependencies = "id")
                    default String name() {
                        return "";
                    }
                }
            """.trimIndent(),
            kotlinSource = """
                package demo

                import org.babyfish.jimmer.Formula
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.MappedSuperclass

                @MappedSuperclass
                interface SqlFormulaBase {
                    @Formula(sql = "NAME")
                    val name: String
                }

                @Entity
                interface LanguageFormulaEntity : SqlFormulaBase {
                    @Id
                    val id: Long

                    @Formula(dependencies = ["id"])
                    override val name: String
                        get() = ""
                }
            """.trimIndent(),
            expected = "formula kind",
        )
    }

    @Test
    fun `generic mapped superclass draft property is rebound only by concrete entity`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface GenericCodeBase<T extends CharSequence> {
                    T getCode();
                }

                @MappedSuperclass
                interface StringCodeBase extends GenericCodeBase<String> {
                    String getLabel();
                }

                @Entity
                interface GenericCodeEntity extends StringCodeBase {
                    @Id
                    long getId();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.MappedSuperclass

                @MappedSuperclass
                interface GenericCodeBase<T : CharSequence> {
                    val code: T
                }

                @MappedSuperclass
                interface StringCodeBase : GenericCodeBase<String> {
                    val label: String
                }

                @Entity
                interface GenericCodeEntity : StringCodeBase {
                    @Id
                    val id: Long
                }
            """.trimIndent()
        )

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptDraftSchema = assertNotNull(apt.draftCodegenSchema)
        val kspDraftSchema = assertNotNull(ksp.draftCodegenSchema)
        assertEquals(aptDraftSchema.normalizedSnapshot(), kspDraftSchema.normalizedSnapshot())

        val genericPlan = aptDraftSchema.typesById.getValue(LsiSymbolId.type("demo.GenericCodeBase"))
        val genericCode = genericPlan.propsBySlot.single { prop -> prop.name == "code" }
        assertEquals(
            LsiDeclaredType(LsiSymbolId.type("java.lang.Object")),
            genericCode.runtimeProp.metadataElementType,
        )

        val mappedPlan = aptDraftSchema.typesById.getValue(LsiSymbolId.type("demo.StringCodeBase"))
        val mappedCode = mappedPlan.propsBySlot.single { prop -> prop.name == "code" }
        val mappedLabel = mappedPlan.propsBySlot.single { prop -> prop.name == "label" }
        assertFalse(mappedCode.genericSourceTarget)
        assertEquals(
            LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
            mappedCode.runtimeProp.metadataElementType,
        )
        assertEquals(listOf(mappedLabel.propId), mappedPlan.kotlinDraftPropIds)
        assertNull(mappedCode.metadataSlotIndex)
        assertNull(mappedLabel.metadataSlotIndex)

        val entityPlan = aptDraftSchema.typesById.getValue(LsiSymbolId.type("demo.GenericCodeEntity"))
        val entityCode = entityPlan.propsBySlot.single { prop -> prop.name == "code" }
        val entityLabel = entityPlan.propsBySlot.single { prop -> prop.name == "label" }
        val entityId = entityPlan.propsBySlot.single { prop -> prop.name == "id" }
        assertTrue(entityCode.genericSourceTarget)
        assertEquals(listOf(entityId.propId, entityCode.propId), entityPlan.kotlinDraftPropIds)
        assertEquals(0, entityCode.slotIndex)
        assertEquals(1, entityLabel.slotIndex)
        assertEquals(2, entityId.slotIndex)
        assertEquals(0, entityCode.metadataSlotIndex)
        assertEquals(1, entityLabel.metadataSlotIndex)
        assertEquals(2, entityId.metadataSlotIndex)
    }

    @Test
    fun `draft plan preserves legacy java accessor identifiers without polluting semantic parity`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.jetbrains.annotations.Nullable;

                @Entity
                interface AccessorEntity {
                    @Id
                    long getId();

                    @Nullable
                    Boolean isActive();

                    String getURL();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface AccessorEntity {
                    @Id
                    val id: Long

                    val active: Boolean?

                    val URL: String
                }
            """.trimIndent()
        )

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptDraftSchema = assertNotNull(apt.draftCodegenSchema)
        val kspDraftSchema = assertNotNull(ksp.draftCodegenSchema)
        assertEquals(aptDraftSchema.normalizedSnapshot(), kspDraftSchema.normalizedSnapshot())

        val typeId = LsiSymbolId.type("demo.AccessorEntity")
        val aptPlan = aptDraftSchema.typesById.getValue(typeId)
        val kspPlan = kspDraftSchema.typesById.getValue(typeId)
        val aptActive = aptPlan.propsById.getValue(LsiSymbolId.property(typeId, "active"))
        val kspActive = kspPlan.propsById.getValue(LsiSymbolId.property(typeId, "active"))
        assertEquals("isActive", aptActive.codegenName)
        assertEquals("setIsActive", aptActive.javaSetterName)
        assertEquals("getIsActive", aptActive.javaBeanGetterName)
        assertEquals("active", kspActive.codegenName)

        val aptUrl = aptPlan.propsById.getValue(LsiSymbolId.property(typeId, "URL"))
        val kspUrl = kspPlan.propsById.getValue(LsiSymbolId.property(typeId, "URL"))
        assertEquals("uRL", aptUrl.codegenName)
        assertEquals("SLOT_U_RL", aptUrl.slotName)
        assertEquals("URL", kspUrl.codegenName)
    }

    @Test
    fun `real apt and ksp frontends freeze identical immutable defaults`() {
        val apt = compileApt(
            """
                package demo;

                import java.time.Instant;
                import org.babyfish.jimmer.sql.DatabaseDefault;
                import org.babyfish.jimmer.sql.Default;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.Key;
                import org.babyfish.jimmer.sql.Version;

                @Entity
                interface DefaultRecord {
                    @Id
                    long id();

                    @Default("client-value")
                    String applicationValue();

                    @DatabaseDefault("CURRENT_TIMESTAMP")
                    Instant databaseValue();

                    @DatabaseDefault
                    String emptyDatabaseValue();

                    @Version
                    int version();

                    @Key
                    @Default("key-value")
                    String businessKey();
                }

                @Entity
                interface ExplicitVersionRecord {
                    @Id
                    long id();

                    @Version
                    @Default("")
                    int version();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import java.time.Instant
                import org.babyfish.jimmer.sql.DatabaseDefault
                import org.babyfish.jimmer.sql.Default
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.Key
                import org.babyfish.jimmer.sql.Version

                @Entity
                interface DefaultRecord {
                    @Id
                    val id: Long

                    @Default("client-value")
                    val applicationValue: String

                    @DatabaseDefault("CURRENT_TIMESTAMP")
                    val databaseValue: Instant

                    @DatabaseDefault
                    val emptyDatabaseValue: String

                    @Version
                    val version: Int

                    @Key
                    @Default("key-value")
                    val businessKey: String
                }

                @Entity
                interface ExplicitVersionRecord {
                    @Id
                    val id: Long

                    @Version
                    @Default("")
                    val version: Int
                }
            """.trimIndent()
        )

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        val defaults = aptSchema.types
            .single { type -> type.qualifiedName == "demo.DefaultRecord" }
            .props
            .associate { prop -> prop.name to prop.defaultContract }
        assertEquals(
            ImmutableDefault.Application(
                annotationValue = "client-value",
                strategy = ApplicationDefaultStrategy.DECLARED_VALUE,
            ),
            defaults.getValue("applicationValue"),
        )
        assertEquals(
            ImmutableDefault.Database("CURRENT_TIMESTAMP"),
            defaults.getValue("databaseValue"),
        )
        assertEquals(ImmutableDefault.Database(null), defaults.getValue("emptyDatabaseValue"))
        assertEquals(
            ImmutableDefault.Application(
                annotationValue = null,
                strategy = ApplicationDefaultStrategy.VERSION_ZERO,
            ),
            defaults.getValue("version"),
        )
        assertEquals(
            ImmutableDefault.Application(
                annotationValue = "",
                strategy = ApplicationDefaultStrategy.DECLARED_VALUE,
            ),
            aptSchema.types
                .single { type -> type.qualifiedName == "demo.ExplicitVersionRecord" }
                .props
                .single { prop -> prop.name == "version" }
                .defaultContract,
        )
    }

    @Test
    fun `real apt and ksp frontends reject inherited default conflicts identically`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.sql.DatabaseDefault;
                import org.babyfish.jimmer.sql.Default;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface DefaultBase {
                    @DatabaseDefault
                    String status();
                }

                @Entity
                interface DefaultEntity extends DefaultBase {
                    @Id
                    long id();

                    @Override
                    @Default("1")
                    String status();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.DatabaseDefault
                import org.babyfish.jimmer.sql.Default
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.MappedSuperclass

                @MappedSuperclass
                interface DefaultBase {
                    @DatabaseDefault
                    val status: String
                }

                @Entity
                interface DefaultEntity : DefaultBase {
                    @Id
                    val id: Long

                    @Default("1")
                    override val status: String
                }
            """.trimIndent()
        )

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertTrue(apt.diagnostic.orEmpty().contains("cannot be decorated by both"))
    }

    @Test
    fun `real apt and ksp frontends reject database defaults on repeatable keys identically`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.sql.DatabaseDefault;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.Key;

                @Entity
                interface InvalidDatabaseKey {
                    @Id
                    long id();

                    @Key(group = "first")
                    @Key(group = "second")
                    @DatabaseDefault
                    String code();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.DatabaseDefault
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.Key

                @Entity
                interface InvalidDatabaseKey {
                    @Id
                    val id: Long

                    @Key(group = "first")
                    @Key(group = "second")
                    @DatabaseDefault
                    val code: String
                }
            """.trimIndent()
        )

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertTrue(apt.diagnostic.orEmpty().contains("cannot be id, key, version"))
    }

    @Test
    fun `real apt and ksp frontends agree on non-list collection scalar semantics`() {
        val apt = compileApt(SCALAR_COLLECTION_JAVA_SOURCE)
        val ksp = compileKsp(SCALAR_COLLECTION_KOTLIN_SOURCE)

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertFalse(aptSchema.types.single().props.single { prop -> prop.name == "values" }.list)

        val invalidApt = compileApt(INVALID_COLLECTION_JAVA_SOURCE)
        val invalidKsp = compileKsp(INVALID_COLLECTION_KOTLIN_SOURCE)
        assertNull(invalidApt.schema)
        assertNull(invalidKsp.schema)
        assertEquals(invalidApt.diagnostic, invalidKsp.diagnostic)
        assertTrue(invalidApt.diagnostic.orEmpty().contains("must use java.util.List"))
    }

    @Test
    fun `real ksp preserves mutable list identity and requires scalar semantics`() {
        val invalid = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface MutableListEntity {
                    @Id
                    val id: Long

                    val values: MutableList<String>
                }
            """.trimIndent()
        )

        assertNull(invalid.schema)
        assertTrue(invalid.diagnostic.orEmpty().contains("must use java.util.List"))

        val scalar = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.Scalar
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface MutableListEntity {
                    @Id
                    val id: Long

                    @Scalar
                    val values: MutableList<String>
                }
            """.trimIndent()
        )

        assertNull(scalar.diagnostic)
        val values = assertNotNull(scalar.schema)
            .types
            .single()
            .props
            .single { prop -> prop.name == "values" }
        assertFalse(values.list)
        assertEquals(
            LsiSymbolId.type("kotlin.collections.MutableList"),
            assertIs<LsiDeclaredType>(values.type).declarationId,
        )
    }

    @Test
    fun `real apt and ksp preserve primitive array boxing semantics`() {
        val apt = compileApt(
            """
                package demo;

                import java.util.List;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                interface ArrayEntity {
                    @Id
                    long id();

                    int[] primitiveValues();

                    Integer[] boxedValues();

                    String[] textValues();

                    List<Integer> numbers();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface ArrayEntity {
                    @Id
                    val id: Long

                    val primitiveValues: IntArray

                    val boxedValues: Array<Int>

                    val textValues: Array<String>

                    val numbers: List<Int>
                }
            """.trimIndent()
        )

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        val aptProps = aptSchema.types.single().props.associateBy(ImmutableProp::name)
        val kspProps = kspSchema.types.single().props.associateBy(ImmutableProp::name)
        val aptPrimitiveElement = assertIs<LsiPrimitiveType>(
            assertIs<LsiArrayType>(aptProps.getValue("primitiveValues").type).elementType,
        )
        val kspPrimitiveElement = assertIs<LsiPrimitiveType>(
            assertIs<LsiArrayType>(kspProps.getValue("primitiveValues").type).elementType,
        )
        val aptBoxedElement = assertIs<LsiPrimitiveType>(
            assertIs<LsiArrayType>(aptProps.getValue("boxedValues").type).elementType,
        )
        val kspBoxedElement = assertIs<LsiPrimitiveType>(
            assertIs<LsiArrayType>(kspProps.getValue("boxedValues").type).elementType,
        )
        val aptListElement = assertIs<LsiPrimitiveType>(
            assertIs<LsiDeclaredType>(aptProps.getValue("numbers").type).arguments.single().type,
        )
        val kspListElement = assertIs<LsiPrimitiveType>(
            assertIs<LsiDeclaredType>(kspProps.getValue("numbers").type).arguments.single().type,
        )

        assertFalse(aptPrimitiveElement.boxed)
        assertFalse(kspPrimitiveElement.boxed)
        assertTrue(aptBoxedElement.boxed)
        assertTrue(kspBoxedElement.boxed)
        assertTrue(aptListElement.boxed)
        assertTrue(kspListElement.boxed)
        assertEquals(
            "array:primitive:int!!",
            aptProps.getValue("primitiveValues").type.jimmerTypeSignature(),
        )
        assertEquals(
            "array:primitive:int:boxed!!",
            aptProps.getValue("boxedValues").type.jimmerTypeSignature(),
        )
    }

    @Test
    fun `real apt raw list and ksp star list fail with identical shape diagnostic`() {
        val apt = compileApt(
            """
                package demo;

                import java.util.List;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                interface InvalidListShape {
                    @Id
                    long id();

                    List values();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface InvalidListShape {
                    @Id
                    val id: Long

                    val values: List<*>
                }
            """.trimIndent()
        )

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertTrue(
            apt.diagnostic.orEmpty().contains("exactly one invariant, non-star element type"),
            apt.diagnostic,
        )
    }

    @Test
    fun `real apt and ksp reject nested list elements identically`() {
        val apt = compileApt(
            """
                package demo;

                import java.util.List;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                interface InvalidListElement {
                    @Id
                    long id();

                    List<List<String>> values();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface InvalidListElement {
                    @Id
                    val id: Long

                    val values: List<List<String>>
                }
            """.trimIndent()
        )

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertTrue(
            apt.diagnostic.orEmpty().contains("non-parameterized declared type"),
            apt.diagnostic,
        )
    }

    @Test
    fun `real apt and ksp reject projected list elements identically`() {
        val apt = compileApt(
            """
                package demo;

                import java.util.List;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                interface InvalidListProjection {
                    @Id
                    long id();

                    List<? extends String> values();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface InvalidListProjection {
                    @Id
                    val id: Long

                    val values: List<out String>
                }
            """.trimIndent()
        )

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertTrue(
            apt.diagnostic.orEmpty().contains("exactly one invariant, non-star element type"),
            apt.diagnostic,
        )
    }

    @Test
    fun `real apt and ksp reject array list elements identically`() {
        val apt = compileApt(
            """
                package demo;

                import java.util.List;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                interface InvalidArrayListElement {
                    @Id
                    long id();

                    List<String[]> values();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface InvalidArrayListElement {
                    @Id
                    val id: Long

                    val values: List<Array<String>>
                }
            """.trimIndent()
        )

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertTrue(
            apt.diagnostic.orEmpty().contains("non-parameterized declared type"),
            apt.diagnostic,
        )
    }

    @Test
    fun `real apt and ksp language formulas bypass list shape validation`() {
        val apt = compileApt(
            """
                package demo;

                import java.util.Collections;
                import java.util.List;
                import org.babyfish.jimmer.Formula;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                interface FormulaListEntity {
                    @Id
                    long id();

                    String source();

                    @Formula(dependencies = "source")
                    default List<List<String>> values() {
                        return Collections.emptyList();
                    }
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.Formula
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface FormulaListEntity {
                    @Id
                    val id: Long

                    val source: String

                    @Formula(dependencies = ["source"])
                    val values: List<List<String>>
                        get() = emptyList()
                }
            """.trimIndent()
        )

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        val values = aptSchema.types.single().props.single { prop -> prop.name == "values" }
        assertFalse(values.list)
        assertEquals(FormulaKind.LANGUAGE, values.formulaKind)
    }

    @Test
    fun `real apt and ksp frontends agree on association cardinality`() {
        val apt = compileApt(ASSOCIATION_JAVA_SOURCE)
        val ksp = compileKsp(ASSOCIATION_KOTLIN_SOURCE)

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
        val ownerProps = aptSchema.types
            .single { type -> type.qualifiedName == "demo.Owner" }
            .props
            .associateBy(ImmutableProp::name)
        val targets = ownerProps.getValue("targets")
        assertEquals(AssociationKind.ONE_TO_MANY, targets.associationKind)
        assertEquals(AssociationStorageKind.NONE, targets.associationStorage)
        assertTrue(targets.list)
        assertTrue(targets.reverse)
        val targetOwner = aptSchema.types
            .single { type -> type.qualifiedName == "demo.Target" }
            .props
            .single { prop -> prop.name == "owner" }
        assertEquals(AssociationKind.MANY_TO_ONE, targetOwner.associationKind)
        assertEquals(AssociationStorageKind.COLUMN, targetOwner.associationStorage)
        assertFalse(targetOwner.list)
        assertEquals(targetOwner.id, targets.mappedBy?.ownerPropId)
        assertEquals(targetOwner.id, aptSchema.ownerPropIdByInversePropId.getValue(targets.id))
        assertEquals(listOf(targets.id), aptSchema.inversePropIdsByOwnerPropId.getValue(targetOwner.id))

        val invalidApt = compileApt(INVALID_ASSOCIATION_JAVA_SOURCE)
        val invalidKsp = compileKsp(INVALID_ASSOCIATION_KOTLIN_SOURCE)
        assertNull(invalidApt.schema)
        assertNull(invalidKsp.schema)
        assertEquals(invalidApt.diagnostic, invalidKsp.diagnostic)
        assertEquals(
            "Immutable list association property 'type:demo.Owner/property:targets' must be decorated by " +
                "@type:org.babyfish.jimmer.sql.OneToMany, " +
                "@type:org.babyfish.jimmer.sql.ManyToMany or " +
                "@type:org.babyfish.jimmer.sql.ManyToManyView",
            invalidApt.diagnostic,
        )
    }

    @Test
    fun `real apt and ksp reject empty one-to-many mappedBy`() {
        val apt = compileApt(
            """
                package demo;

                import java.util.List;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.ManyToOne;
                import org.babyfish.jimmer.sql.OneToMany;

                @Entity
                interface EmptyMappedByOwner {
                    @Id long id();

                    @OneToMany(mappedBy = "")
                    List<EmptyMappedByTarget> targets();
                }

                @Entity
                interface EmptyMappedByTarget {
                    @Id long id();

                    @ManyToOne
                    EmptyMappedByOwner owner();
                }
            """.trimIndent(),
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.ManyToOne
                import org.babyfish.jimmer.sql.OneToMany

                @Entity
                interface EmptyMappedByOwner {
                    @Id val id: Long

                    @OneToMany(mappedBy = "")
                    val targets: List<EmptyMappedByTarget>
                }

                @Entity
                interface EmptyMappedByTarget {
                    @Id val id: Long

                    @ManyToOne
                    val owner: EmptyMappedByOwner
                }
            """.trimIndent(),
        )

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertTrue(apt.diagnostic.orEmpty().contains("must declare a non-empty mappedBy"))
    }

    @Test
    fun `real apt and ksp resolve generic mappedBy to owner specific property ids`() {
        val apt = compileApt(
            """
                package demo;

                import java.util.List;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;
                import org.babyfish.jimmer.sql.ManyToOne;
                import org.babyfish.jimmer.sql.OneToMany;

                @MappedSuperclass
                interface Base<T extends Base<T>> {
                    @ManyToOne
                    T parent();

                    @OneToMany(mappedBy = "parent")
                    List<T> children();
                }

                @Entity
                interface Node extends Base<Node> {
                    @Id
                    long id();
                }
            """.trimIndent(),
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.MappedSuperclass
                import org.babyfish.jimmer.sql.ManyToOne
                import org.babyfish.jimmer.sql.OneToMany

                @MappedSuperclass
                interface Base<T : Base<T>> {
                    @ManyToOne
                    val parent: T

                    @OneToMany(mappedBy = "parent")
                    val children: List<T>
                }

                @Entity
                interface Node : Base<Node> {
                    @Id
                    val id: Long
                }
            """.trimIndent(),
        )

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
        assertEquals(
            assertNotNull(apt.draftCodegenSchema).normalizedSnapshot(),
            assertNotNull(ksp.draftCodegenSchema).normalizedSnapshot(),
        )
        val aptBaseChildren = aptSchema.types.single { type -> type.qualifiedName == "demo.Base" }
            .props.single { prop -> prop.name == "children" }
        val aptNode = aptSchema.types.single { type -> type.qualifiedName == "demo.Node" }
            .props.associateBy(ImmutableProp::name)
        assertNull(aptBaseChildren.mappedBy?.ownerPropId)
        assertEquals(AssociationStorageKind.NONE, aptBaseChildren.associationStorage)
        assertEquals(
            LsiSymbolId.property(LsiSymbolId.type("demo.Node"), "parent"),
            aptNode.getValue("children").mappedBy?.ownerPropId,
        )
        assertEquals(
            listOf(aptNode.getValue("children").id),
            aptSchema.inversePropIdsByOwnerPropId.getValue(aptNode.getValue("parent").id),
        )
    }

    @Test
    fun `real apt and ksp agree on middle table and JoinSql association storage`() {
        val apt = compileApt(
            """
                package demo;

                import java.util.List;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.JoinSql;
                import org.babyfish.jimmer.sql.ManyToMany;

                @Entity
                interface Book {
                    @Id long id();

                    @ManyToMany
                    List<Author> authors();

                    @ManyToMany
                    @JoinSql("%alias.ID = %target_alias.ID")
                    List<Tag> tags();
                }

                @Entity
                interface Author {
                    @Id long id();

                    @ManyToMany(mappedBy = "authors")
                    List<Book> books();
                }

                @Entity
                interface Tag {
                    @Id long id();

                    @ManyToMany(mappedBy = "tags")
                    List<Book> books();
                }
            """.trimIndent(),
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.JoinSql
                import org.babyfish.jimmer.sql.ManyToMany

                @Entity
                interface Book {
                    @Id val id: Long

                    @ManyToMany
                    val authors: List<Author>

                    @ManyToMany
                    @JoinSql("%alias.ID = %target_alias.ID")
                    val tags: List<Tag>
                }

                @Entity
                interface Author {
                    @Id val id: Long

                    @ManyToMany(mappedBy = "authors")
                    val books: List<Book>
                }

                @Entity
                interface Tag {
                    @Id val id: Long

                    @ManyToMany(mappedBy = "tags")
                    val books: List<Book>
                }
            """.trimIndent(),
        )

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
        val bookProps = aptSchema.types.single { type -> type.qualifiedName == "demo.Book" }
            .props.associateBy(ImmutableProp::name)
        val authorBooks = aptSchema.types.single { type -> type.qualifiedName == "demo.Author" }
            .props.single { prop -> prop.name == "books" }
        val tagBooks = aptSchema.types.single { type -> type.qualifiedName == "demo.Tag" }
            .props.single { prop -> prop.name == "books" }
        assertEquals(AssociationStorageKind.MIDDLE_TABLE, bookProps.getValue("authors").associationStorage)
        assertEquals(AssociationStorageKind.NONE, bookProps.getValue("tags").associationStorage)
        assertEquals(AssociationStorageKind.NONE, authorBooks.associationStorage)
        assertEquals(AssociationStorageKind.NONE, tagBooks.associationStorage)
        assertEquals(bookProps.getValue("authors").id, authorBooks.mappedBy?.ownerPropId)
        assertEquals(bookProps.getValue("tags").id, tagBooks.mappedBy?.ownerPropId)
    }

    @Test
    fun `real apt and ksp frontends produce identical formula dependency paths`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.Formula;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.ManyToOne;

                @Entity
                interface Department {
                    @Id
                    long id();

                    String name();
                }

                @Entity
                interface Employee {
                    @Id
                    long id();

                    String firstName();

                    @ManyToOne
                    Department department();

                    @Formula(dependencies = {"firstName", "department.name"})
                    default String displayName() {
                        return firstName();
                    }

                    @Formula(sql = "FIRST_NAME")
                    String storedDisplayName();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.Formula
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.ManyToOne

                @Entity
                interface Department {
                    @Id
                    val id: Long

                    val name: String
                }

                @Entity
                interface Employee {
                    @Id
                    val id: Long

                    val firstName: String

                    @ManyToOne
                    val department: Department

                    @Formula(dependencies = ["firstName", "department.name"])
                    val displayName: String
                        get() = firstName

                    @Formula(sql = "FIRST_NAME")
                    val storedDisplayName: String
                }
            """.trimIndent()
        )

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
        assertEquals(
            assertNotNull(apt.draftCodegenSchema).normalizedSnapshot(),
            assertNotNull(ksp.draftCodegenSchema).normalizedSnapshot(),
        )

        val employeeTypeId = LsiSymbolId.type("demo.Employee")
        val departmentTypeId = LsiSymbolId.type("demo.Department")
        val displayName = aptSchema.typesById.getValue(employeeTypeId)
            .props
            .single { prop -> prop.name == "displayName" }
        assertEquals(
            listOf(
                FormulaDependency(
                    listOf(LsiSymbolId.property(employeeTypeId, "firstName"))
                ),
                FormulaDependency(
                    listOf(
                        LsiSymbolId.property(employeeTypeId, "department"),
                        LsiSymbolId.property(departmentTypeId, "name"),
                    )
                ),
            ),
            displayName.formulaDependencies,
        )
        assertEquals(
            FormulaKind.SQL,
            aptSchema.typesById.getValue(employeeTypeId)
                .props
                .single { prop -> prop.name == "storedDisplayName" }
                .formulaKind,
        )
    }

    @Test
    fun `real apt and ksp frontends produce identical transient resolver metadata`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.Transient;

                class EmployeeResolver {}

                @Entity
                interface Employee {
                    @Id
                    long id();

                    String name();

                    @Transient
                    String scratch();

                    @Transient(EmployeeResolver.class)
                    String typeValue();

                    @Transient(ref = "employeeResolver")
                    String referenceValue();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.Transient

                class EmployeeResolver

                @Entity
                interface Employee {
                    @Id
                    val id: Long

                    val name: String

                    @Transient
                    val scratch: String

                    @Transient(EmployeeResolver::class)
                    val typeValue: String

                    @Transient(ref = "employeeResolver")
                    val referenceValue: String
                }
            """.trimIndent()
        )

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())

        val props = aptSchema.types.single { type -> type.qualifiedName == "demo.Employee" }
            .props
            .associateBy(ImmutableProp::name)
        assertFalse(props.getValue("id").fetchable)
        assertTrue(props.getValue("name").fetchable)
        assertFalse(props.getValue("scratch").fetchable)
        assertNull(props.getValue("scratch").transientResolver)
        assertEquals(
            TransientResolver.Type(LsiSymbolId.type("demo.EmployeeResolver")),
            props.getValue("typeValue").transientResolver,
        )
        assertTrue(props.getValue("typeValue").fetchable)
        assertEquals(
            TransientResolver.Reference("employeeResolver"),
            props.getValue("referenceValue").transientResolver,
        )
        assertTrue(props.getValue("referenceValue").fetchable)
    }

    @Test
    fun `real apt and ksp frontends reject conflicting transient resolvers`() {
        val apt = compileApt(
            """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Transient;

                class EmployeeResolver {}

                @Entity
                interface Employee {
                    @Transient(value = EmployeeResolver.class, ref = "employeeResolver")
                    String value();
                }
            """.trimIndent()
        )
        val ksp = compileKsp(
            """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Transient

                class EmployeeResolver

                @Entity
                interface Employee {
                    @Transient(value = EmployeeResolver::class, ref = "employeeResolver")
                    val value: String
                }
            """.trimIndent()
        )

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertEquals(
            "Immutable transient property 'type:demo.Employee/property:value' cannot specify both " +
                "resolver type and resolver reference",
            apt.diagnostic,
        )
    }

    @Test
    fun `real apt and ksp frontends reject non-embeddable immutable target`() {
        val apt = compileApt(INVALID_IMMUTABLE_TARGET_JAVA_SOURCE)
        val ksp = compileKsp(INVALID_IMMUTABLE_TARGET_KOTLIN_SOURCE)

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertEquals(
            "Immutable property 'type:demo.Owner/property:value' target type 'type:demo.Value' " +
                "is immutable but not embeddable",
            apt.diagnostic,
        )
    }

    @Test
    fun `real apt and ksp frontends report identical invalid discriminator diagnostic`() {
        val apt = compileApt(INVALID_JAVA_SOURCE)
        val ksp = compileKsp(INVALID_KOTLIN_SOURCE)

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertEquals(
            "Immutable property 'type:demo.ModelBase/property:kind' decorated by " +
                "@type:org.babyfish.jimmer.sql.Discriminator must be a scalar string or enum property",
            apt.diagnostic,
        )
    }

    @Test
    fun `real apt and ksp frontends report identical invalid id view diagnostic`() {
        val apt = compileApt(INVALID_VIEW_JAVA_SOURCE)
        val ksp = compileKsp(INVALID_VIEW_KOTLIN_SOURCE)

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertEquals(
            "Immutable view property 'type:demo.Book/property:storeId' list category does not match " +
                "id-view base property 'store'",
            apt.diagnostic,
        )
    }

    @Test
    fun `real apt and ksp frontends report identical primary mapping conflict`() {
        val apt = compileApt(INVALID_PRIMARY_MAPPING_JAVA_SOURCE)
        val ksp = compileKsp(INVALID_PRIMARY_MAPPING_KOTLIN_SOURCE)

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertEquals(
            "Immutable property 'type:demo.Book/property:storeId' cannot declare multiple primary mapping " +
                "annotations: @type:org.babyfish.jimmer.sql.IdView, " +
                "@type:org.babyfish.jimmer.sql.Transient",
            apt.diagnostic,
        )
    }

    @Test
    fun `real apt and ksp frontends report identical id view element nullability`() {
        val apt = compileApt(INVALID_VIEW_ELEMENT_NULLABILITY_JAVA_SOURCE)
        val ksp = compileKsp(INVALID_VIEW_ELEMENT_NULLABILITY_KOTLIN_SOURCE)

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertEquals(
            "Immutable view property 'type:demo.Book/property:authorIds' type does not match id 'id' " +
                "of association target 'demo.Author'",
            apt.diagnostic,
        )
    }

    private fun compileApt(
        source: String,
        libraries: List<File> = emptyList(),
    ): FrontendResult {
        val projectDir = createTempDirectory(prefix = "jimmer-immutable-apt-parity").toFile()
        val sourceFile = projectDir.resolve("src/main/java/demo/Models.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(source)
        }
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val capture = FrontendCapture()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT frontend parity tests require a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, listOf(generatedDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf(
                    "-proc:only",
                    "-classpath",
                    (libraries + runtimeClasspath())
                        .distinct()
                        .joinToString(File.pathSeparator, transform = File::getAbsolutePath),
                ),
                null,
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(ImmutableSnapshotAptProcessor(capture)))
            task.call()
        }
        check(capture.completed) {
            "APT frontend did not freeze an LSI workspace:\n" +
                diagnostics.diagnostics.joinToString("\n") { diagnostic -> diagnostic.getMessage(null) }
        }
        val frontendResult = capture.result()
        if (frontendResult.diagnostic == null) {
            assertTrue(success, diagnostics.diagnostics.joinToString("\n"))
        } else {
            assertFalse(success, diagnostics.diagnostics.joinToString("\n"))
            assertTrue(diagnostics.diagnostics.any { diagnostic ->
                diagnostic.kind == Diagnostic.Kind.ERROR &&
                    diagnostic.getMessage(null) == frontendResult.diagnostic
            })
        }
        return frontendResult
    }

    private fun assertOverrideRejectionParity(
        label: String,
        javaSource: String,
        kotlinSource: String,
        expected: String,
    ) {
        val apt = compileApt(javaSource)
        val ksp = compileKsp(kotlinSource)

        assertNull(apt.schema, "$label produced an APT schema")
        assertNull(ksp.schema, "$label produced a KSP schema")
        assertEquals(apt.diagnostic, ksp.diagnostic, label)
        assertTrue(
            apt.diagnostic.orEmpty().contains(expected),
            "$label: ${apt.diagnostic}",
        )
    }

    private fun compileKsp(
        source: String,
        libraries: List<File> = emptyList(),
    ): FrontendResult {
        val projectDir = createTempDirectory(prefix = "jimmer-immutable-ksp-parity").toFile()
        val sourceFile = projectDir.resolve("src/main/kotlin/demo/Models.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(source)
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val capture = FrontendCapture()
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "immutable-frontend-parity"
            sourceRoots = listOf(sourceFile)
            this.libraries = (libraries + runtimeClasspath()).distinct()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
            resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val exitCode = KotlinSymbolProcessing(
            configuration,
            listOf(ImmutableSnapshotKspProvider(capture)),
            logger,
        ).execute()
        val frontendResult = capture.result()
        if (frontendResult.diagnostic == null) {
            assertEquals(KotlinSymbolProcessing.ExitCode.OK, exitCode, logger.messages.joinToString("\n"))
        } else {
            assertEquals(
                KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR,
                exitCode,
                logger.messages.joinToString("\n"),
            )
            assertTrue(logger.errors.contains(frontendResult.diagnostic))
        }
        return frontendResult
    }

    private fun compileJavaDocumentationLibrary(): File {
        val projectDir = createTempDirectory(prefix = "jimmer-immutable-java-doc-library").toFile()
        val sourceDir = projectDir.resolve("src/main/java/demo/binary")
        val bookSource = sourceDir.resolve("BinaryBook.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    package demo.binary;

                    import org.babyfish.jimmer.sql.Entity;
                    import org.babyfish.jimmer.sql.Id;

                    @Entity
                    public interface BinaryBook {
                        @Id
                        long id();

                        String name();
                    }
                """.trimIndent()
            )
        }
        val draftSource = sourceDir.resolve("BinaryBookDraft.java").also { file ->
            file.writeText(
                """
                    package demo.binary;

                    import org.babyfish.jimmer.client.Description;

                    @Description("binary type")
                    public interface BinaryBookDraft {
                        @Description("binary property")
                        BinaryBookDraft setName(String name);
                    }
                """.trimIndent()
            )
        }
        val output = projectDir.resolve("build/classes").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("Immutable documentation parity requires a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(output))
            compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf(
                    "-proc:none",
                    "-classpath",
                    runtimeClasspath().joinToString(File.pathSeparator, transform = File::getAbsolutePath),
                ),
                null,
                fileManager.getJavaFileObjects(bookSource, draftSource),
            ).call()
        }
        assertTrue(
            success,
            diagnostics.diagnostics.joinToString("\n") { diagnostic -> diagnostic.getMessage(null) },
        )
        return output
    }

    private fun compileKotlinDocumentationLibrary(): File {
        val projectDir = createTempDirectory(prefix = "jimmer-immutable-kotlin-doc-library").toFile()
        val source = projectDir.resolve("src/main/kotlin/demo/binary/BinaryBook.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    package demo.binary

                    import org.babyfish.jimmer.client.Description
                    import org.babyfish.jimmer.sql.Entity
                    import org.babyfish.jimmer.sql.Id

                    @Entity
                    interface BinaryBook {
                        @get:Id
                        val id: Long

                        val name: String
                    }

                    @Description("binary type")
                    interface BinaryBookDraft {
                        @Description("binary property")
                        val name: String
                    }
                """.trimIndent()
            )
        }
        val output = projectDir.resolve("build/classes").apply(File::mkdirs)
        val messages = ByteArrayOutputStream()
        val exitCode = PrintStream(messages, true, StandardCharsets.UTF_8).use { stream ->
            K2JVMCompiler().exec(
                stream,
                "-no-stdlib",
                "-no-reflect",
                "-classpath",
                runtimeClasspath().joinToString(File.pathSeparator, transform = File::getAbsolutePath),
                "-d",
                output.absolutePath,
                source.absolutePath,
            )
        }
        assertEquals(ExitCode.OK, exitCode, messages.toString(StandardCharsets.UTF_8))
        return output
    }

    private class ImmutableSnapshotAptProcessor(
        private val capture: FrontendCapture,
    ) : AbstractProcessor() {

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            if (roundEnvironment.processingOver() || capture.completed) {
                return false
            }
            val workspace = roundEnvironment.toLsiWorkspace(
                processingEnv,
                LsiFrontendOptions.from(emptyMap()),
            )
            capture.freeze(workspace)
            capture.diagnostic?.let { diagnostic ->
                processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, diagnostic)
            }
            return false
        }
    }

    private class ImmutableSnapshotKspProvider(
        private val capture: FrontendCapture,
    ) : SymbolProcessorProvider {

        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    if (capture.completed) {
                        return emptyList()
                    }
                    val workspace = resolver.toLsiWorkspace(LsiFrontendOptions.from(emptyMap()))
                    capture.freeze(workspace)
                    capture.diagnostic?.let { diagnostic ->
                        environment.logger.error(diagnostic)
                    }
                    return emptyList()
                }
            }
        }
    }

    private class FrontendCapture {
        var schema: ImmutableSchema? = null
            private set

        var draftCodegenSchema: JimmerImmutableDraftCodegenSchema? = null
            private set

        var diagnostic: String? = null
            private set

        var completed: Boolean = false
            private set

        fun freeze(workspace: LsiWorkspace) {
            try {
                schema = workspace.toImmutableSchema()
                draftCodegenSchema = JimmerImmutableDraftCodegenPrecompiler().compile(
                    schema = requireNotNull(schema),
                    workspace = workspace,
                    options = JimmerImmutableDraftCodegenOptions.DEFAULT,
                )
            } catch (exception: ImmutablePrecompileException) {
                diagnostic = exception.message
            }
            completed = true
        }

        fun result(): FrontendResult {
            check(completed) { "Frontend did not freeze an LSI workspace" }
            return FrontendResult(schema, draftCodegenSchema, diagnostic)
        }
    }

    private class CapturingKspLogger : KSPLogger {
        val messages = mutableListOf<String>()

        val errors = mutableListOf<String>()

        override fun logging(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun info(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun warn(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun error(message: String, symbol: KSNode?) {
            messages += message
            errors += message
        }

        override fun exception(exception: Throwable) {
            throw exception
        }
    }

    private data class FrontendResult(
        val schema: ImmutableSchema?,
        val draftCodegenSchema: JimmerImmutableDraftCodegenSchema?,
        val diagnostic: String?,
    )

    private fun ImmutableProp.annotationString(
        annotationType: LsiSymbolId,
        argumentName: String,
    ): String? {
        val annotation = annotations.singleOrNull { item -> item.type == annotationType } ?: return null
        return (annotation.arguments[argumentName]?.value as? LsiAnnotationValue.StringValue)?.value
    }

    private companion object {
        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map(::File)
                .filter(File::exists)
        }

        val VALID_JAVA_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.Discriminator;
            import org.babyfish.jimmer.sql.DiscriminatorValue;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.Inheritance;
            import org.babyfish.jimmer.sql.InheritanceType;
            import org.babyfish.jimmer.sql.IdView;
            import org.babyfish.jimmer.sql.JoinedTableDissociateAction;
            import org.babyfish.jimmer.sql.MappedSuperclass;
            import org.babyfish.jimmer.sql.ManyToOne;
            import org.babyfish.jimmer.sql.ManyToManyView;
            import org.babyfish.jimmer.sql.OneToMany;
            import java.util.List;

            @MappedSuperclass
            interface ModelBase<T> {
                @Id
                String id();

                @Discriminator
                String kind();

                T createdBy();
            }

            @Entity
            @Inheritance(
                strategy = InheritanceType.JOINED,
                joinedTableDissociateAction = JoinedTableDissociateAction.LAX
            )
            interface Asset extends ModelBase<String> {
                String name();
            }

            @Entity
            @DiscriminatorValue("BOOK")
            interface Book extends Asset {
                String isbn();
            }

            @Entity
            interface Store {
                @Id
                long id();
            }

            @Entity
            interface Author {
                @Id
                long id();
            }

            @Entity
            interface BookAuthor {
                @Id
                long id();

                @ManyToOne
                Author author();

                @ManyToOne
                CatalogBook book();
            }

            @Entity
            interface CatalogBook {
                @Id
                long id();

                @ManyToOne
                Store store();

                @IdView
                long storeId();

                @OneToMany(mappedBy = "book")
                List<BookAuthor> links();

                @ManyToManyView(prop = "links")
                List<Author> authors();

                @IdView("authors")
                List<Long> authorIds();
            }
        """.trimIndent()

        val VALID_KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Discriminator
            import org.babyfish.jimmer.sql.DiscriminatorValue
            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.Inheritance
            import org.babyfish.jimmer.sql.InheritanceType
            import org.babyfish.jimmer.sql.IdView
            import org.babyfish.jimmer.sql.JoinedTableDissociateAction
            import org.babyfish.jimmer.sql.MappedSuperclass
            import org.babyfish.jimmer.sql.ManyToOne
            import org.babyfish.jimmer.sql.ManyToManyView
            import org.babyfish.jimmer.sql.OneToMany

            @MappedSuperclass
            interface ModelBase<T : Any> {
                @Id
                val id: String

                @Discriminator
                val kind: String

                val createdBy: T
            }

            @Entity
            @Inheritance(
                strategy = InheritanceType.JOINED,
                joinedTableDissociateAction = JoinedTableDissociateAction.LAX,
            )
            interface Asset : ModelBase<String> {
                val name: String
            }

            @Entity
            @DiscriminatorValue("BOOK")
            interface Book : Asset {
                val isbn: String
            }

            @Entity
            interface Store {
                @Id
                val id: Long
            }

            @Entity
            interface Author {
                @Id
                val id: Long
            }

            @Entity
            interface BookAuthor {
                @Id
                val id: Long

                @ManyToOne
                val author: Author

                @ManyToOne
                val book: CatalogBook
            }

            @Entity
            interface CatalogBook {
                @Id
                val id: Long

                @ManyToOne
                val store: Store

                @IdView
                val storeId: Long

                @OneToMany(mappedBy = "book")
                val links: List<BookAuthor>

                @ManyToManyView(prop = "links")
                val authors: List<Author>

                @IdView("authors")
                val authorIds: List<Long>
            }
        """.trimIndent()

        val IDENTITY_JAVA_SOURCE = """
            package demo;

            import java.time.Instant;
            import org.babyfish.jimmer.sql.Default;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.LogicalDeleted;
            import org.babyfish.jimmer.sql.MappedSuperclass;
            import org.babyfish.jimmer.sql.Version;
            import org.jetbrains.annotations.Nullable;

            enum DeleteState {
                ALIVE,
                DELETED
            }

            @MappedSuperclass
            interface IdentityBase {
                @Id
                long id();

                @Version
                int version();

                @LogicalDeleted
                boolean deleted();
            }

            @Entity
            interface IdentityRecord extends IdentityBase {}

            @Entity
            interface TimedRecord {
                @Id
                long id();

                @LogicalDeleted
                @Nullable
                Instant deletedAt();
            }

            @Entity
            interface StatefulRecord {
                @Id
                long id();

                @Default("ALIVE")
                @LogicalDeleted("DELETED")
                @Nullable
                DeleteState state();
            }
        """.trimIndent()

        val IDENTITY_KOTLIN_SOURCE = """
            package demo

            import java.time.Instant
            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Default
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.LogicalDeleted
            import org.babyfish.jimmer.sql.MappedSuperclass
            import org.babyfish.jimmer.sql.Version

            enum class DeleteState {
                ALIVE,
                DELETED,
            }

            @MappedSuperclass
            interface IdentityBase {
                @Id
                val id: Long

                @Version
                val version: Int

                @LogicalDeleted
                val deleted: Boolean
            }

            @Entity
            interface IdentityRecord : IdentityBase

            @Entity
            interface TimedRecord {
                @Id
                val id: Long

                @LogicalDeleted
                val deletedAt: Instant?
            }

            @Entity
            interface StatefulRecord {
                @Id
                val id: Long

                @Default("ALIVE")
                @LogicalDeleted("DELETED")
                val state: DeleteState?
            }
        """.trimIndent()

        val OVERRIDDEN_PROPERTY_JAVA_SOURCE = """
            package demo;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            import org.babyfish.jimmer.sql.Column;
            import org.babyfish.jimmer.sql.Default;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.MappedSuperclass;

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.METHOD)
            @interface Marker {
                String value();
            }

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.METHOD)
            @interface ParentMarker {}

            @MappedSuperclass
            interface GenericStatusBase<T extends CharSequence> {
                @Marker("parent")
                @ParentMarker
                @Default("0")
                @Column(name = "BASE_STATUS")
                T getStatus();
            }

            @Entity
            interface OverrideEntity extends GenericStatusBase<String> {
                @Id
                long getId();

                @Override
                @Marker("child")
                @Default("1")
                String getStatus();
            }
        """.trimIndent()

        val OVERRIDDEN_PROPERTY_KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Column
            import org.babyfish.jimmer.sql.Default
            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.MappedSuperclass

            @Retention(AnnotationRetention.RUNTIME)
            @Target(AnnotationTarget.PROPERTY_GETTER)
            annotation class Marker(val value: String)

            @Retention(AnnotationRetention.RUNTIME)
            @Target(AnnotationTarget.PROPERTY_GETTER)
            annotation class ParentMarker

            @MappedSuperclass
            interface GenericStatusBase<T : CharSequence> {
                @get:Marker("parent")
                @get:ParentMarker
                @Default("0")
                @Column(name = "BASE_STATUS")
                val status: T
            }

            @Entity
            interface OverrideEntity : GenericStatusBase<String> {
                @Id
                val id: Long

                @get:Marker("child")
                @Default("1")
                override val status: String
            }
        """.trimIndent()

        val SCALAR_COLLECTION_JAVA_SOURCE = """
            package demo;

            import java.util.List;
            import java.util.Set;
            import org.babyfish.jimmer.Scalar;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;

            @Entity
            interface ScalarCollectionEntity {
                @Id
                long id();

                @Scalar
                Set<String> getValues();

                @Scalar
                List<List<String>> getNestedValues();
            }
        """.trimIndent()

        val SCALAR_COLLECTION_KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.Scalar
            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id

            @Entity
            interface ScalarCollectionEntity {
                @Id
                val id: Long

                @Scalar
                val values: Set<String>

                @Scalar
                val nestedValues: List<List<String>>
            }
        """.trimIndent()

        val INVALID_COLLECTION_JAVA_SOURCE = SCALAR_COLLECTION_JAVA_SOURCE.replace("@Scalar\n", "")
        val INVALID_COLLECTION_KOTLIN_SOURCE = SCALAR_COLLECTION_KOTLIN_SOURCE.replace("@Scalar\n", "")

        val ASSOCIATION_JAVA_SOURCE = """
            package demo;

            import java.util.List;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.ManyToOne;
            import org.babyfish.jimmer.sql.OneToMany;

            @Entity
            interface Owner {
                @Id
                long id();

                @OneToMany(mappedBy = "owner")
                List<Target> targets();
            }

            @Entity
            interface Target {
                @Id
                long id();

                @ManyToOne
                Owner owner();
            }
        """.trimIndent()

        val ASSOCIATION_KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.ManyToOne
            import org.babyfish.jimmer.sql.OneToMany

            @Entity
            interface Owner {
                @Id
                val id: Long

                @OneToMany(mappedBy = "owner")
                val targets: List<Target>
            }

            @Entity
            interface Target {
                @Id
                val id: Long

                @ManyToOne
                val owner: Owner
            }
        """.trimIndent()

        val INVALID_ASSOCIATION_JAVA_SOURCE = ASSOCIATION_JAVA_SOURCE
            .replace("@OneToMany(mappedBy = \"owner\")", "@ManyToOne")

        val INVALID_ASSOCIATION_KOTLIN_SOURCE = ASSOCIATION_KOTLIN_SOURCE
            .replace("@OneToMany(mappedBy = \"owner\")", "@ManyToOne")

        val INVALID_IMMUTABLE_TARGET_JAVA_SOURCE = """
            package demo;

            import org.babyfish.jimmer.Immutable;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;

            @Immutable
            interface Value {}

            @Entity
            interface Owner {
                @Id
                long id();

                Value value();
            }
        """.trimIndent()

        val INVALID_IMMUTABLE_TARGET_KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.Immutable
            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id

            @Immutable
            interface Value

            @Entity
            interface Owner {
                @Id
                val id: Long

                val value: Value
            }
        """.trimIndent()

        val INVALID_JAVA_SOURCE = VALID_JAVA_SOURCE
            .replace("String kind();", "int kind();")

        val INVALID_KOTLIN_SOURCE = VALID_KOTLIN_SOURCE
            .replace("val kind: String", "val kind: Int")

        val INVALID_VIEW_JAVA_SOURCE = """
            package demo;

            import java.util.List;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.IdView;
            import org.babyfish.jimmer.sql.ManyToOne;

            @Entity
            interface Store {
                @Id
                long id();
            }

            @Entity
            interface Book {
                @Id
                long id();

                @ManyToOne
                Store store();

                @IdView("store")
                List<Long> storeId();
            }
        """.trimIndent()

        val INVALID_VIEW_KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.IdView
            import org.babyfish.jimmer.sql.ManyToOne

            @Entity
            interface Store {
                @Id
                val id: Long
            }

            @Entity
            interface Book {
                @Id
                val id: Long

                @ManyToOne
                val store: Store

                @IdView("store")
                val storeId: List<Long>
            }
        """.trimIndent()

        val INVALID_PRIMARY_MAPPING_JAVA_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.IdView;
            import org.babyfish.jimmer.sql.ManyToOne;
            import org.babyfish.jimmer.sql.Transient;

            @Entity
            interface Store {
                @Id
                long id();
            }

            @Entity
            interface Book {
                @ManyToOne
                Store store();

                @IdView
                @Transient
                long storeId();
            }
        """.trimIndent()

        val INVALID_PRIMARY_MAPPING_KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.IdView
            import org.babyfish.jimmer.sql.ManyToOne
            import org.babyfish.jimmer.sql.Transient

            @Entity
            interface Store {
                @Id
                val id: Long
            }

            @Entity
            interface Book {
                @ManyToOne
                val store: Store

                @Transient
                @IdView
                val storeId: Long
            }
        """.trimIndent()

        val INVALID_VIEW_ELEMENT_NULLABILITY_JAVA_SOURCE = """
            package demo;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            import java.util.List;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.IdView;
            import org.babyfish.jimmer.sql.ManyToMany;

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE_USE)
            @interface Nullable {}

            @Entity
            interface Author {
                @Id
                long id();
            }

            @Entity
            interface Book {
                @Id
                long id();

                @ManyToMany
                List<Author> authors();

                @IdView("authors")
                List<@Nullable Long> authorIds();
            }
        """.trimIndent()

        val INVALID_VIEW_ELEMENT_NULLABILITY_KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.IdView
            import org.babyfish.jimmer.sql.ManyToMany

            @Entity
            interface Author {
                @Id
                val id: Long
            }

            @Entity
            interface Book {
                @Id
                val id: Long

                @ManyToMany
                val authors: List<Author>

                @IdView("authors")
                val authorIds: List<Long?>
            }
        """.trimIndent()

        val DEFAULT = LsiSymbolId.type("org.babyfish.jimmer.sql.Default")
        val COLUMN = LsiSymbolId.type("org.babyfish.jimmer.sql.Column")
        val JAVA_OVERRIDE = LsiSymbolId.type("java.lang.Override")
    }
}
