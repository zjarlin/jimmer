# LSI Jimmer

Jimmer 的语言无关领域语义模型，以及基于 LSI 模型的领域扩展函数。

模块只依赖 `lsi-core`，不得引用编译器 SPI、APT、KSP、JavaPoet 或 KotlinPoet。

当前公开模型包含 `ImmutableSchema`、`ImmutableType`、`ImmutableProp`、Immutable Draft runtime/validation/annotation projection、DTO 图、Error schema、Client schema、ExportDoc schema、Transactional schema、TypedTuple schema，以及 DTO interface、annotation、config contract 和 Kotlin mutability。扩展函数负责图关系、属性语义、Draft 运行时分类、Draft 校验规范化、Draft 方法注解投影、接口解析、注解类型校验、config 实现校验、DTO Kotlin 可变性、Error、Client、ExportDoc、Transactional 与 TypedTuple 解析和来源闭包；有效注解统一冻结为结构化 `LsiAnnotation`，只保存显式参数，声明默认值继续由注解类型负责。生成目标筛选、产物命名、增量聚合及平台写出由 `jimmer-compiler` 负责。

Maven 坐标：`org.babyfish.jimmer:lsi-jimmer`。
