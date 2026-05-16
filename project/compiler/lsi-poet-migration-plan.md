# LSI Poet Migration Plan, Phase 2

## Summary

仓库当前状态已经超过旧版“bridge-first, minimal symbol layer only”的阶段：

- `LsiFileSpec`
- `LsiCallableSpec`
- `LsiPropertySpec`
- `LsiTypeSpec`

已经存在，并且 `tuple` / `transactional` 已在生产链路中使用。

因此，Poet 迁移的下一阶段不再讨论“要不要引入 `LsiFileSpec/LsiCallableSpec/LsiPropertySpec`”，而是正式确立：

- `LsiPoet` v1 是 compiler source generation 的 canonical middle-state
- 所有 compiler source generation 主链路最终统一产出 `LsiFileSpec`
- `KotlinPoet` / `JavaPoet` 只允许存在于 `lsi-ksp` / `lsi-apt` adapter 内部负责渲染
- `GeneratedSourceArtifact` 不再作为源码生成主路径；仅保留给资源文件或暂时无法建模为 `LsiFileSpec` 的非源码文本输出

## Current Status

截至本阶段开始时，仓库已经具备以下现实基础：

- `tuple` shared generator 已返回 `LsiFileSpec`
- `transactional` shared generator 已返回 `LsiFileSpec`
- `lsi-core` 已承载 `LsiFileSpec` / `LsiTypeSpec` / `LsiPropertySpec` / `LsiCallableSpec`
- `lsi-ksp` / `lsi-apt` 已具备 `renderKotlinSource()` / `renderJavaSource()` 与 `LsiFiler.createSourceFile(LsiFileSpec)` 的基本能力

因此，Phase 2 的目标不是“引入”这些类型，而是把它们确立为唯一主路径，并推动旧链路迁移。

### Immutable Status Update

`immutable` 已经进入“shared generator 已落地，但 Java-renderable 仍未归一化完成”的阶段。

当前已完成：

- `Fetcher` / `FetcherDsl` / `Props` / `Draft` shared generator 已切到 `LsiFileSpec`
- `Draft` 相关 shared metadata 已不再直接携带 `LsiLambdaTypeName`，改为 `ImmutableDraftBlockMetadata(receiverTypeName, consumerTypeName)` 这类 neutral carrier
- `ProducerGenerator.produce(block)` 已切到消费 `ImmutableDraftBlockMetadata`，不再在 generator 内直接拼装 block carrier
- embeddable `PropExpression` 已切到 shared `EmbeddedPropExpressionGenerator`
- APT 侧 `Table` / `TableEx` / `Remote` 已切到 shared `TableGenerator`
- APT 旧业务生成器中 `PropExpressionGenerator.java` / `TableGenerator.java` 已删除

当前新增验证：

- `immutable-metadata-generator` 已补 `EmbeddedPropExpressionGenerator` / `TableGenerator` 的 Java render shape 测试
- `lsi-apt` Java renderer 已对 raw `LsiCodeBlock` 增加 Kotlin-only 语法 fail-fast，防止把 `?.` / `?:` / `!==` / `when` / `val` 这类片段误当成 Java 可渲染源码
- 已补 `JavaPoetBoundaryGuardTest` 与 `ImmutableJavaRenderabilityAuditTest`，分别固定 adapter 边界约束与 immutable 当前 Builder 链路的 Java render blocker
- `LsiPoet` 已补最小共享 statement 能力：`LsiIfStatement`、`LsiPropertySetStatement`
- `BuilderGenerator` 非空 setter 分支已切到共享 `Lsi*` statement，不再依赖 raw Kotlin `!==` 片段，并已通过 Java/Kotlin render 测试
- `:project:compiler:immutable:immutable-metadata-generator:compileTestKotlin` 已通过

当前仓库级阻塞：

- 完整 `test` 任务仍被现有 jar 打包问题阻塞：
  - `:project:jimmer-core:jar`
  - `:project:jimmer-dto-compiler:jar`
- 阻塞原因都是 duplicate `META-INF/*.kotlin_module`
- 因此，当前 immutable 新增测试只完成了编译校验，尚未完成完整 Gradle test 执行

### Immutable Java-Renderable Audit

以下结论针对 `Draft / Producer` 主链，以及它们依赖的 shared helper / nested generator。

| Area | Current Kotlin-only shape | Why Java render blocks | Required action |
| --- | --- | --- | --- |
| `DraftGenerator` | `topLevelCallables` + `receiverType` extension functions + file-level Kotlin `@Suppress` | Java renderer明确拒绝 top-level callables / extension receiver | 把 Kotlin DSL helper 从 shared 主文件边界拆出去；shared source main path 只保留 Java/Kotlin 共通结构 |
| `ImmutableGeneratorMetadataProjector` | `draftBlockType()` 直接产出 `LsiLambdaTypeName(receiverType, Unit)` | Java renderer 对 `LsiLambdaTypeName` fail fast | block 参数需要先收敛成 backend-neutral callback carrier，不能继续把 Kotlin lambda type 写进 shared metadata |
| `ProducerGenerator` | `produce(block)` 使用 `LsiLambdaTypeName`；`typeInitializer()` 大量 raw Kotlin 代码：`::class`、`listOf(...)`、builder lambda | 即使去掉 lambda type，raw code 仍是 Kotlin 语义 | 先把 `produce` block 参数 neutralize，再把 `typeInitializer` 拆成 neutral AST/helper，不能继续 raw Kotlin 直写 |
| `BuilderGenerator` | 非空 setter 分支此前依赖 raw Kotlin `!==` 与属性赋值语法 | 该问题已通过 `LsiIfStatement` + `LsiPropertySetStatement` 收敛；当前代表性 case 可 Java render | 继续复用这套 statement 能力推进其他 generator，不再把 Builder 作为本轮主 blocker |
| `AssociatedIdGenerator` | `LsiPropertySpec.setterStatements` + Kotlin null-check snippet | Java renderer 当前对 `setterStatements` 明确 fail fast | shared 侧要么改成显式 setter callable，要么继续视为 Kotlin-only 并在 APT 侧禁止接入 |
| `CaseAppender` / `ImplementorGenerator` | `when (...) { ... -> }`、`val`、Kotlin case label 拼装 | 当前输出是 Kotlin `when` 代码块，不是 Java `switch` | dispatch 逻辑需要独立做 control-flow neutralization，不能继续把 Kotlin `when` 文本塞进 `LsiCodeBlock` |
| `ImplGenerator` | getter use-site + getterStatements 之外，还包含大量 raw Kotlin：`as`、`until`、Kotlin `when`/local var style | Java renderer不会翻译 raw Kotlin 代码；虽然 getter use-site 可降级，但 raw code 仍阻塞 | 先拆 dispatch/hash/equals/toString 这类 raw block，逐步替换成 neutral statements |
| `DraftImplGenerator` | `setterStatements`、`this@DraftImpl`、`===`、`!!`、`error()`、`mutableListOf()`、Kotlin `apply(block)` 风格调用 | 同时命中 Java renderer 的 fail-fast 和 raw Kotlin 语义阻塞 | DraftImpl 是 immutable Java-renderable 归一化的最大 blocker，必须单独成 wave |
| `ValidationGenerator` | 校验代码块仍以 Kotlin 语法模板拼接 | shared 校验逻辑没有 neutral AST | 要把 validator/message/pattern 逻辑拆成 neutral helper，至少不能再直接输出 Kotlin 片段 |
| `PropsGenerator` / `FetcherGenerator` | `memberImports`、`topLevelCallables`、extension receiver、`LsiLambdaTypeName` | Java renderer在 file boundary 直接拒绝 | 这两条链当前应视为 Kotlin-only DSL surface，不应被误判为“APT 已可共用” |

额外结论：

- `ImmutableProcessor` 当前已经在 APT 路径里直接写 `ImmutableGeneratedArtifactsKt.toGeneratedArtifacts(...)`
- 因此，`compileJava` 只能证明 processor 代码能编过，不能证明 immutable APT shared rendering 已可运行
- immutable APT 之后必须补一条真正执行 `renderJavaSource()` / `AptLsiFiler.createSourceFile(LsiFileSpec)` 的 integration gate

### Immutable APT Cutover Rule

`immutable` APT 旧链的切换条件固定为：

1. 某个 shared generator 只有在代表性 metadata case 上可以稳定通过 `renderJavaSource()` 时，才允许标记为 “APT cut over”
2. 只把 `Table / TableEx / Remote / EmbeddedPropExpression` 视为当前已经完成 APT shared rendering 的子链
3. `Draft / Producer / Builder / Implementor / Impl / DraftImpl / Props / Fetcher` 还不能视为 APT 已完成迁移
4. 下一波 immutable APT 切换入口，必须先完成：
   - `draftBlockType()` 去 `LsiLambdaTypeName`
   - `DraftGenerator` 去 top-level extension callable
   - `DraftImplGenerator` / `AssociatedIdGenerator` 去 `setterStatements`
   - dispatch / validation / producer initializer 的 raw Kotlin neutralization

## Boundary Rules

### Canonical Rule

- `LSI is the only compiler middle-state.`
- `APT` / `KSP` / `JavaPoet` / `KotlinPoet` 都必须向 `LSI` 单向收敛。
- 预编译系统、shared metadata、shared generator、processor orchestration 只能面向 `Lsi*` / `LsiPoet` 编程。
- 所有跨平台转换函数统一命名为 `toLsiClass()` / `toLsiType()` / `toLsiPoet()` / `toLsiXxx()`。
- `Poet rendering is boundary-only.` JavaPoet/KotlinPoet 只能存在于 adapter 内部实现，不得成为 compiler 主链路可见 API。

### Source-Generation Boundary

- 所有源码写入统一优先走 `LsiFiler.createSourceFile(LsiFileSpec)`
- `createSourceFile(qualifiedName, content)` 只留给：
  - 资源型文本
  - 过渡期遗留点
  - 测试夹具
- `renderKotlinSource()` / `renderJavaSource()` 可以保留 public，仅作为 adapter 测试与 golden file 验证入口
- `toKotlinPoet()` / `toJavaPoet()` 只允许保留在 adapter 内部或最小可见性实现中，不允许作为 compiler 业务层 API

### Static Repository Guardrails

固定静态约束如下：

- `lib/lsi/lsi-ksp` / `lib/lsi/lsi-apt` 之外，不允许新增 `toKotlinPoet(` / `toJavaPoet(`
- `lib/lsi/lsi-ksp` / `lib/lsi/lsi-apt` 之外，不允许新增 `com.squareup.kotlinpoet` / `com.squareup.javapoet` import
- shared generator 不允许继续把 `GeneratedSourceArtifact` 作为源码主结果

## LsiPoet v1 Canonical Surface

本轮正式确认以下类型是 `LsiPoet` v1 的 canonical source-generation surface：

- `LsiFileSpec`
- `LsiTypeSpec`
- `LsiPropertySpec`
- `LsiCallableSpec`
- `LsiTypeName`
- `LsiClassName`
- `LsiAnnotationSpec`
- `LsiCodeBlock`
- `LsiLambdaTypeName`

其中：

- `LsiFileSpec` 是源码文件唯一中间态
- `LsiTypeSpec` 是类型结构中间态
- `LsiPropertySpec` / `LsiCallableSpec` 是成员结构中间态
- `LsiTypeName` 及其子类型负责类型语义表达

## Shared Surface Requirements

### Shared Helpers Must Return `Lsi*`

`project/compiler/jimmer-ksp-ext` 及其他 shared helper 必须统一返回 `Lsi*`，不得继续暴露 Poet 原生符号。

明确要求：

- `generatedAnnotation*()` 返回 `LsiAnnotationSpec`
- `suppressAllAnnotation()` 返回 `LsiAnnotationSpec`
- `JacksonTypes` 从 `ClassName` carrier 改为 `LsiClassName` carrier
- 可跨模块复用的 `Constants` 中类名常量逐步统一为 `LsiClassName`

### Out of Scope

- `client.meta.TypeName` 不属于 Poet 迁移范围，本轮保持不动
- 静态 grep 或 review 时应把该命名体系从 false positive 中排除

## Missing Surface To Complete

### `LsiLambdaTypeName`

`lsi-core` 需要提供最小 `LsiLambdaTypeName`：

- `receiverType: LsiTypeName? = null`
- `parameterTypes: List<LsiTypeName>`
- `returnType: LsiTypeName`
- `nullable: Boolean = false`

本轮不支持：

- `suspend`
- context receivers
- lambda parameter names
- lambda type annotations

### Adapter Behavior Contracts

Kotlin adapter：

- `LsiLambdaTypeName` 必须完整渲染到 `LambdaTypeName`

Java adapter：

- `LsiLambdaTypeName` 必须 fail fast，错误信息明确标记为 `Kotlin-only LsiPoet node`
- `LsiAnnotationUseSiteTarget != null` 时 fail fast，不允许静默忽略
- `LsiTypeSpecKind.OBJECT` 时 fail fast，不做伪渲染
- `LsiModifier.INTERNAL` 映射为 package-private
- `nullable` 对 Java 引用类型不编码，仅保留现有 boxing/unboxing 语义

## Execution Plan

### Wave 1: Pure Shared Main Path First

第一优先级不是“兼容层清理”，而是“共享主链路纯化”。

优先模块：

- `tuple`
- `transactional`

实施要求：

- APT 侧切换到共享 `LsiFileSpec` generator + `AptLsiFiler`
- 删除或停用 APT 旧 JavaPoet 生成器
- 证明以下路径成立：
  - 同一份 shared generator
  - KSP 走 `renderKotlinSource`
  - APT 走 `renderJavaSource`
  - processor 只做 frontend extraction + orchestration

### Wave 2: Kotlin-Only Old Chain With High ROI

优先迁移：

- `error`

实施要求：

- `ErrorMetadataGenerator` 从直接构造 KotlinPoet `FileSpec/TypeSpec/...` 改为返回 `LsiFileSpec/LsiTypeSpec/...`
- KSP processor 改为只写 `LsiFileSpec`
- 后续若引入 APT 共用路径，不再新建 JavaPoet 版本

### Wave 3: Immutable

实施要求：

- `immutable-metadata-generator` 内 generator-private metadata 的 `TypeName/ClassName/AnnotationSpec` 全部改成 `Lsi*`
- 重点包括：
  - `DraftMetadata`
  - `DraftImplAccessorMetadata`
  - `PropsMetadata`
  - `ImmutableGeneratorMetadataProjector` 中仍直接构造 KotlinPoet `TypeName/LambdaTypeName` 的路径
- 顶层源码生成器最终统一返回 `LsiFileSpec`
- `JimmerModuleMetadataGenerator` 等资源型输出可继续返回 `GeneratedResourceArtifact` 或字符串型 artifact，不强行建模到 `LsiPoet`

### Wave 4: DTO

实施要求：

- DTO 按 “shared metadata -> shared LsiPoet generator -> backend filer” 路线重构
- `DtoGenerator` / `InputBuilderGenerator` / `SerializerGenerator` 中与源码结构直接相关的部分全部下沉为 shared `LsiPoet` generator
- APT 旧 Java 生成器不做就地适配；统一被 shared generator 替换，APT 仅保留 extraction/orchestration

## Public APIs / Interfaces

### Stable v1 Interface Set

- `LsiFileSpec`
- `LsiTypeSpec`
- `LsiPropertySpec`
- `LsiCallableSpec`
- `LsiTypeName`
- `LsiLambdaTypeName`

### Filer Rule

`LsiFiler` 作为唯一源码写入边界保留，但约束改为：

- 源码路径必须优先使用 `createSourceFile(LsiFileSpec)`

### Adapter Visibility Rule

- `toKotlinPoet()` / `toJavaPoet()` 只允许作为 adapter 实现细节存在
- compiler 业务模块不允许 import 这些 API
- `LsiAnnotationCompatExt` / `LsiClassCompatExt` / `TypeNameCompatExt` 这类兼容桥接，只允许迁移期由 adapter 模块自己依赖；所有 compiler call site 迁完后删除

## Non-Goals

本轮明确不做的事：

- 不要求一次性消灭仓库里所有 Poet 直接调用
- 不要求立即把所有资源型/文本型输出都强行塞进 `LsiPoet`
- 不以“兼容桥接删光”为本轮第一目标

本轮的非目标不是否定 `LsiFileSpec/LsiCallableSpec/LsiPropertySpec`，而是明确：

- 这些类型已经是 canonical middle-state
- 当前不追求把所有旧链路在一个提交内全部迁完
- 当前优先把 shared pipeline 和 processor orchestration 纯化到 `LsiPoet`

## Test Plan

### Adapter Unit Tests

- `LsiClassName` Kotlin/Java render
- `LsiTypeName` Kotlin/Java render
- `LsiAnnotationSpec` Kotlin/Java render
- `LsiCodeBlock` Kotlin/Java render
- `LsiFileSpec` Kotlin/Java render
- `LsiLambdaTypeName` Kotlin render 正确，Java render fail fast
- Java render 对 `useSiteTarget` / `OBJECT` fail fast

### Cross-Backend Golden Tests

- `tuple`：同一份 metadata 生成 Kotlin 与 Java 源码
- `transactional`：同一份 metadata 生成 Kotlin 与 Java 源码

### Shared-Pipeline Compile Gates

- `:lib:lsi:lsi-core:compileKotlin`
- `:lib:lsi:lsi-ksp:compileKotlin`
- `:lib:lsi:lsi-apt:compileKotlin`
- `:project:compiler:tuple:tuple-metadata-generator:compileKotlin`
- `:project:compiler:transactional:tx-metadata-generator:compileKotlin`
- `:project:compiler:error:error-metadata-generator:compileKotlin`
- `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
- `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
- `:project:jimmer-apt:compileJava`
- `:project:jimmer-ksp:compileKotlin`

### Static Grep Gates

- adapter 模块外无新增 `toKotlinPoet(` / `toJavaPoet(`
- adapter 模块外无新增 `com.squareup.kotlinpoet` / `com.squareup.javapoet`
- source generators 返回 `LsiFileSpec` 或 `List<LsiFileSpec>`，不再返回字符串源码 artifact

### Representative Regression Gates

- immutable: Draft / DraftImpl / Props / Fetcher / Builder
- dto: DTO type / input builder / serializer
- error: exception hierarchy
- tx / tuple: shared generator 双后端输出

允许差异只限：

- import 顺序
- 空行与格式化

不允许差异：

- 类型签名
- 注解语义
- 继承结构
- generated member set

## Assumptions

- 当前仓库状态已经接受 `LsiFileSpec/LsiCallableSpec/LsiPropertySpec/LsiTypeSpec`，本计划不回退这一现实
- APT 侧不再继续扩写新的 JavaPoet 业务生成器；统一迁到 shared Kotlin metadata-generator + `AptLsiFiler` 路线
- `GeneratedSourceArtifact` 对源码输出只是过渡物，不是终态；终态是 `LsiFileSpec`
- `client.meta.TypeName` 不属于 Poet 迁移范围，本轮不处理
- 本轮优先级固定为：
  1. 先共享主链路纯化
  2. 再 APT/KSP 共用
  3. 最后兼容桥接删除
