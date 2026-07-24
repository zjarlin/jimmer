# lsi-poet

`lsi-poet` 定义只面向 LSI 的语言无关源码模型。它描述文件、类型、成员、参数、注解和代码占位片段，不依赖 JavaPoet、KotlinPoet、APT 或 KSP。

坐标：`org.babyfish.jimmer:lsi-poet`

Java 和 Kotlin 的源码落地分别由 `lsi-poet-javapoet` 与 `lsi-poet-kotlinpoet` 完成。中立 `LsiPoetRenderer` 只返回 `GeneratedArtifact`；具体后端 renderer 还提供原生类型结构输出，用于把 LSI 类型组合进同一后端的其他声明。

每个 `LsiPoetArtifact` 必须携带文件全部类型引用对应的 `LsiPoetTypeName`。该模型显式保存包名和嵌套类型名，不按字符大小写猜测边界；已冻结声明由 `LsiWorkspace.toLsiPoetTypeNames` 精确解析，尚未进入 workspace 的生成类型由调用方作为 `additional` 显式提供。
