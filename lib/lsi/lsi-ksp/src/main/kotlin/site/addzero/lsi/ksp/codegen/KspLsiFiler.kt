package site.addzero.lsi.ksp.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import site.addzero.lsi.codegen.LsiFiler
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.renderKotlinSource
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class KspLsiFiler(
    private val codeGenerator: CodeGenerator,
    private val dependencies: Dependencies = Dependencies(false),
    private val extensionName: String = "kt"
) : LsiFiler {

    // 覆盖来源：KSP 各 generator 中 codeGenerator.createNewFile(...) 的统一写文件入口
    // 典型位置：
    // - project/compiler/immutable/jimmer-ksp-immutable/.../generator/*
    // - project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.kt
    // - project/compiler/client/jimmer-ksp-client/.../ClientProcessor.kt, ExportDocProcessor.kt
    override fun createSourceFile(qualifiedName: String, content: String) {
        val packageName = qualifiedName.substringBeforeLast('.', "")
        val simpleName = qualifiedName.substringAfterLast('.')
        val output = codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = packageName,
            fileName = simpleName,
            extensionName = extensionName
        )
        OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }

    override fun createSourceFile(fileSpec: LsiFileSpec) {
        createSourceFile(fileSpec.qualifiedName, fileSpec.renderKotlinSource())
    }

    override fun createResourceFile(path: String, content: String) {
        val normalized = path.removePrefix("/").replace('\\', '/')
        val packageName = normalized.substringBeforeLast('/', "")
            .replace('/', '.')
        val fileName = normalized.substringAfterLast('/')
        val output = codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = packageName,
            fileName = fileName,
            extensionName = ""
        )
        OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }
}

fun CodeGenerator.toLsiFiler(
    dependencies: Dependencies = Dependencies(false),
    extensionName: String = "kt"
): LsiFiler =
    KspLsiFiler(this, dependencies, extensionName)
