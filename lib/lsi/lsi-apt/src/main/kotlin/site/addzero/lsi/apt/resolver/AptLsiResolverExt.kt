package site.addzero.lsi.apt.resolver

import site.addzero.lsi.resolver.LsiResolver
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.util.Elements

// 覆盖来源：APT Processor 中 RoundEnvironment -> 统一 LSI 解析器入口
fun RoundEnvironment.toLsiResolver(elements: Elements): LsiResolver =
    AptLsiResolver(this, elements)

// 覆盖来源：APT Processor.init/process 中直接使用 ProcessingEnvironment 的接入形式
fun RoundEnvironment.toLsiResolver(processingEnvironment: ProcessingEnvironment): LsiResolver =
    AptLsiResolver(this, processingEnvironment.elementUtils)
