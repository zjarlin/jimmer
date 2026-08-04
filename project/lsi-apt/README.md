# lsi-apt

`lsi-apt` 将当前 APT 轮次中的 javac 符号立即冻结为不可变 LSI，并负责通过 `Filer` 读取编译输入资源、写出 `GeneratedArtifact`。

模块只依赖 `lsi-core`、JDK APT API 和 Kotlin metadata。它不包含 Jimmer 语义，也不依赖 JavaPoet、KotlinPoet 或 KSP。

Maven 坐标：`org.babyfish.jimmer:lsi-apt`。
