package org.babyfish.jimmer.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.client.EnableImplicitApi
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableType
import org.babyfish.jimmer.sql.Embeddable
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.MappedSuperclass
import site.addzero.context.Settings
import kotlin.text.isNotBlank

object Context {
    lateinit var resolver: Resolver
    lateinit var environment: SymbolProcessorEnvironment

   var explicitClientApi: Boolean? =null
     var tupleGenerated = false

    var delayedTupleTypeNames: Collection<String>? = null

     var clientGenerated = false

     var delayedClientTypeNames: Collection<String>? =null



    var serverGenerated = false


    val collectionType: KSType = resolver
        .getClassDeclarationByName("kotlin.collections.Collection")
        ?.asStarProjectedType()
        ?: error("Internal bug")

    val listType: KSType = resolver
        .getClassDeclarationByName("kotlin.collections.List")
        ?.asStarProjectedType()
        ?: error("Internal bug")

    val mapType: KSType = resolver
        .getClassDeclarationByName("kotlin.collections.Map")
        ?.asStarProjectedType()
        ?: error("Internal bug")

    val isHibernateValidatorEnhancement: Boolean =
        environment.options["jimmer.dto.hibernateValidatorEnhancement"] == "true"

    @Deprecated("Please use Settings")
    val isBuddyIgnoreResourceGeneration: Boolean =
        environment.options["jimmer.buddy.ignoreResourceGeneration"]?.trim() == "true"

    @Deprecated("Please use Settings")
    private val includes: Array<String>? =
        environment.options["jimmer.source.includes"]
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                it.trim().split("\\s*,[,;]\\s*").toTypedArray()
            }

    @Deprecated("Please use Settings")
    private val excludes: Array<String>? =
        environment.options["jimmer.source.excludes"]
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                it.trim().split("\\s*[,;]\\s*").toTypedArray()
            }

    private val typeMap: MutableMap<KSClassDeclaration, ImmutableType> = mutableMapOf()

    private var newTypes = typeMap?.values?.toMutableList() ?: mutableListOf()

    fun typeOf(classDeclaration: KSClassDeclaration): ImmutableType =
        typeMap[classDeclaration] ?: ImmutableType(this, classDeclaration).also {
            typeMap[classDeclaration] = it
            newTypes += it
        }

    fun typeAnnotationOf(classDeclaration: KSClassDeclaration): KSAnnotation? {
        var sqlAnnotation: KSAnnotation? = null
        for (ormAnnotationType in ORM_ANNOTATION_TYPES) {
            val anno = classDeclaration.annotation(ormAnnotationType) ?: continue
            if (sqlAnnotation !== null) {
                throw MetaException(
                    classDeclaration,
                    null,
                    "it cannot be decorated by both " +
                            "@${sqlAnnotation.fullName} and ${anno.fullName}"
                )
            }
            sqlAnnotation = anno
        }
        return sqlAnnotation ?: classDeclaration.annotation(Immutable::class)
    }

    fun resolve() {
        while (this.newTypes.isNotEmpty()) {
            val newTypes = this.newTypes
            this.newTypes = mutableListOf()
            for (newType in newTypes) {
                for (step in 0..4) {
                    newType.resolve(this, step)
                }
            }
        }
    }


    private val ORM_ANNOTATION_TYPES = listOf(
        Entity::class,
        MappedSuperclass::class,
        Embeddable::class
    )
}
