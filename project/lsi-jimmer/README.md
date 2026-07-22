# LSI Jimmer

Jimmer 的语言无关不可变类型语义模型，以及基于 `LsiWorkspace` 的领域扩展函数。

模块只依赖 `lsi-core`，不得引用编译器 SPI、APT、KSP、JavaPoet 或 KotlinPoet。

公开扩展仅描述 `ImmutableSchema`、`ImmutableType` 与 `ImmutableProp` 的图关系和属性语义；生成目标筛选、产物命名、增量聚合及平台写出由 `jimmer-compiler` 负责。

Maven 坐标：`org.babyfish.jimmer:lsi-jimmer`。
