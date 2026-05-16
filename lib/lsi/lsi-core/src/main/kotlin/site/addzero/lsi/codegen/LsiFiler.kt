package site.addzero.lsi.codegen

import site.addzero.lsi.poet.LsiFileSpec
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

interface LsiFiler {
    /**
     * 创建源文件
     */
    fun createSourceFile(qualifiedName: String, content: String)

    /**
     * 通过 LSI 中间态创建源文件。
     *
     * 迁移说明：
     * - compiler 主链路只交付 `LsiFileSpec`
     * - KotlinPoet / JavaPoet 渲染留在具体 adapter 内部完成
     */
    fun createSourceFile(fileSpec: LsiFileSpec)

    /**
     * 创建资源文件（相对 CLASS_OUTPUT 路径）。
     */
    fun createResourceFile(path: String, content: String)

    /**
     * 覆盖资源文件内容。
     *
     * 默认实现退化为创建资源文件；APT 等支持物理文件覆写的 adapter 可以重写。
     */
    fun overwriteResourceFile(path: String, content: String) {
        createResourceFile(path, content)
    }

    /**
     * 读取已生成资源文件文本；当前后端无法探测时返回 `null`。
     */
    fun readResourceText(path: String): String? = null

    /**
     * 以 properties 语义合并资源文件。
     *
     * 默认实现基于 `readResourceText` + `overwriteResourceFile`；支持更强语义的 adapter 可以重写。
     */
    fun mergePropertiesResourceFile(path: String, generatedContent: String, comment: String) {
        val merged = Properties()
        readResourceText(path)?.let { existingContent ->
            StringReader(existingContent).use { reader ->
                merged.load(reader)
            }
        }
        StringReader(generatedContent).use { reader ->
            val current = Properties()
            current.load(reader)
            for ((key, value) in current) {
                merged[key] = value
            }
        }
        val rendered = StringWriter().also { writer ->
            merged.store(writer, comment)
        }.toString()
        overwriteResourceFile(path, rendered)
    }

}
