# lsi-poet

`lsi-poet` 定义只面向 LSI 的语言无关源码模型。它描述文件、类型、成员、参数、注解和代码占位片段，不依赖 JavaPoet、KotlinPoet、APT 或 KSP。

坐标：`org.babyfish.jimmer:lsi-poet`

Java 和 Kotlin 的源码落地分别由 `lsi-poet-javapoet` 与 `lsi-poet-kotlinpoet` 完成。中立 `LsiPoetRenderer` 只返回 `GeneratedArtifact`；具体后端 renderer 还提供原生类型结构输出，用于把 LSI 类型组合进同一后端的其他声明。
