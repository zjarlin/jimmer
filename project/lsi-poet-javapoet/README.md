# lsi-poet-javapoet

`lsi-poet-javapoet` 是 `lsi-poet` 到 JavaPoet 的边界适配器。中立 artifact renderer 只接收 LSI Poet 模型并返回 `GeneratedArtifact`；具体类型与注解 renderer 分别返回 JavaPoet `TypeSpec` 和 `AnnotationSpec`，用于把 LSI 结构组合进已有 JavaPoet 声明。

坐标：`org.babyfish.jimmer:lsi-poet-javapoet`
