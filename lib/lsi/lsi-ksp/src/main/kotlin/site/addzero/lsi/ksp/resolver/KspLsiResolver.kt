package site.addzero.lsi.ksp.resolver

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.ksp.clazz.toLsiClass
import site.addzero.lsi.resolver.LsiResolver

class KspLsiResolver(
    private val resolver: Resolver
) : LsiResolver {

    // 覆盖来源：ClientProcessor / ExportDocProcessor / TypedTupleProcessor 的 resolver.getAllFiles() 全量扫描
    override fun allClasses(): Sequence<LsiClass> =
        resolver
            .getAllFiles()
            .flatMap { file -> file.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .map { it.toLsiClass(resolver) }

    // 覆盖来源：ImmutableProcessor / ErrorProcessor / TxProcessor 的 resolver.getNewFiles() 增量扫描
    override fun newClasses(): Sequence<LsiClass> =
        resolver
            .getNewFiles()
            .flatMap { file -> file.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .map { it.toLsiClass(resolver) }

    // 覆盖来源：统一替换 resolver.getSymbolsWithAnnotation(...)
    override fun findClassesAnnotatedWith(annotationQualifiedName: String): Sequence<LsiClass> =
        resolver
            .getSymbolsWithAnnotation(annotationQualifiedName, inDepth = false)
            .filterIsInstance<KSClassDeclaration>()
            .map { it.toLsiClass(resolver) }

    // 覆盖来源：DtoProcessor / TypedTupleProcessor delayed lookup 的 getClassDeclarationByName(...)
    override fun findClassByQualifiedName(qualifiedName: String): LsiClass? =
        resolver.getClassDeclarationByName(qualifiedName)?.toLsiClass(resolver)
}

fun Resolver.toLsiResolver(): LsiResolver =
    KspLsiResolver(this)
