# lsi-poet-kotlinpoet

`lsi-poet-kotlinpoet` 是 `lsi-poet` 到 KotlinPoet 的边界适配器。中立 artifact renderer 只接收 LSI Poet 模型并返回 `GeneratedArtifact`；具体类型与注解 renderer 分别返回 KotlinPoet `TypeSpec` 和 `AnnotationSpec`，用于把 LSI 结构组合进已有 KotlinPoet 声明。

坐标：`org.babyfish.jimmer:lsi-poet-kotlinpoet`
