package site.addzero.lsi.file

import java.io.File

/**
 * 覆盖来源：project/jimmer-ksp/.../JimmerProcessor.generatedJimmerResourceFileProvider 的 `guessResourceFile(...)`
 * 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../JimmerModuleGenerator.generate 的资源文件探测
 * 迁移说明：Jimmer 生成资源路径推断改为挂到中立 file 包，避免最外层 KSP 处理器继续依赖 `org.babyfish.jimmer.ksp.util`
 */
fun guessResourceFile(file: File?, name: String): File? =
    guessResourceDir(file)?.let { File(it, name) }

private fun guessResourceDir(file: File?): File? {
    return tryGetResourceDir(file) ?: guessResourceDir(file?.parentFile ?: return null)
}

private fun tryGetResourceDir(file: File?): File? =
    file
        ?.takeIf(File::isDirectory)
        ?.let { File(it, "generated") }
        ?.takeIf(File::isDirectory)
        ?.let { File(it, "ksp") }
        ?.takeIf(File::isDirectory)
        ?.let {
            File(it, "main").takeIf(File::isDirectory)
                ?: File(it, "test").takeIf(File::isDirectory)
                ?: File(it, "debug").takeIf(File::isDirectory)
                ?: File(it, "release").takeIf(File::isDirectory)
        }
        ?.let { File(it, "resources") }
        ?.let { File(it, "META-INF") }
        ?.let { File(it, "jimmer") }
