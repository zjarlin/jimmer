# lsi-poet-javapoet

`lsi-poet-javapoet` 是 `lsi-poet` 到 JavaPoet 的边界适配器。中立 artifact renderer 只接收 LSI Poet 模型并返回 `GeneratedArtifact`；具体类型 renderer 返回 JavaPoet `TypeSpec`，用于把 LSI 嵌套类型组合进已有 JavaPoet 声明。

坐标：`org.babyfish.jimmer:lsi-poet-javapoet`
