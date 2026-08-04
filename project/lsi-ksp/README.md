# lsi-ksp

`lsi-ksp` 将当前 KSP 轮次中的有效符号立即冻结为不可变 LSI，并负责 KSP 文件作用域、defer 规划、编译输入资源和 `GeneratedArtifact` 写出。

模块只依赖 `lsi-core` 与 KSP API。它不包含 Jimmer 语义，也不依赖 JavaPoet 或 KotlinPoet。

Maven 坐标：`org.babyfish.jimmer:lsi-ksp`。
