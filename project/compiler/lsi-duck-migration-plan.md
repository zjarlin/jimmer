# Jimmer APT/KSP -> LSI Migration Plan

## Status

This document now tracks active migration progress.  
Checked items are merged work (currently many are KSP-first).

## Objective

Replace duplicated meta-program extraction logic in:

- `project/jimmer-apt`
- `project/compiler/*ksp*`

with a shared LSI-based intermediate representation, while preserving current generation outputs and round behavior.

## Scope and Constraints

### In Scope

- Identify APT/KSP duck behaviors (same semantic intent, different platform API).
- Define a staged plan to move extraction logic onto LSI IR.
- Define acceptance gates for each stage.

### Out of Scope (for this phase)

- Immediate refactor of production processors.
- Generator template rewrites.
- Behavioral changes in DTO/API/table/error outputs.

### Hard Constraints

- Keep both APT and KSP working during migration.
- Feature parity first, cleanup second.
- Any stage must be rollback-safe by feature flags or adapter boundaries.

### One-Way LSI Rule

- `LSI is the only compiler middle-state.`
- `APT` / `KSP` 平台对象必须单向收敛到 `LSI`，shared metadata、shared generator、processor orchestration 只能面向 `Lsi*`。
- 所有跨平台转换入口统一命名为 `toLsiClass()` / `toLsiType()` / `toLsiPoet()` / `toLsiXxx()`。
- `Poet rendering is boundary-only.` 如果最终要落到 JavaPoet/KotlinPoet，只能在 adapter 内部完成，不得暴露进 compiler 主链路。
- `KS*` / `TypeElement` / `JavaPoet` / `KotlinPoet` 泄漏到 shared 层时，按架构错误处理，不按临时实现细节处理。

## Current Topology Snapshot

### APT Orchestration Entry

- `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/JimmerProcessor.java`
  - Main flow: immutable -> entry -> error -> dto -> tx -> export-doc -> tuple -> client
  - Uses delayed type-name sets to emulate phase barriers.

### KSP Orchestration Entry

- `project/jimmer-ksp/src/main/kotlin/org/babyfish/jimmer/ksp/JimmerProcessor.kt`
  - Uses `ProcessorSpi` graph with `dependsOn` + barrier semantics.
  - Maintains round state (`executed`, `compiled`, deferred symbols).

### Core Metadata Builders

- APT:
  - `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/Context.java`
  - `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/meta/ImmutableType.java`
  - `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/meta/ImmutableProp.java`
- KSP:
  - `project/compiler/jimmer-ksp-ext/src/main/kotlin/site/addzero/context/Context.kt`
  - `project/compiler/jimmer-ksp-ext/src/main/kotlin/site/addzero/lsi/jimmer/meta/ImmutableType.kt`
  - `project/compiler/jimmer-ksp-ext/src/main/kotlin/site/addzero/lsi/jimmer/meta/ImmutableProp.kt`

### Existing LSI Usage (Partial)

- `LsiClass` is already injected into KSP immutable type modeling:
  - `Context.typeOf(lsiClass)`
  - `ImmutableType(ctx, lsiClass)`
- `project/compiler/jimmer-ksp-ext` 旧 `org.babyfish.jimmer.ksp` 命名空间当前已清空，immutable meta 也已迁移到 `site.addzero.lsi.jimmer.meta`。
- APT path is not yet on LSI in processor/runtime flow.

## Duck Behavior Matrix (APT vs KSP)

| # | Duck behavior (same intent) | APT side | KSP side | LSI target capability | Gap status |
|---|---|---|---|---|---|
| 1 | Immutable type annotation classification | `Context.getImmutableAnnotationType` | `Context.typeAnnotationOf` | `LsiClass` annotation classification (`lsi-jimmer`) | Partial |
| 2 | include/exclude source filtering | `Context.include(TypeElement)` | `KSClassDeclaration.include()` | Unified source filter API | Partial（KSP path done） |
| 3 | Immutable type cache and reuse | `Context.getImmutableType` | `Context.typeOf` + queue `resolve()` | Unified type registry on LSI key | Missing |
| 4 | Super type legality checks | `ImmutableType` ctor checks | `ImmutableType.superTypes` checks | LSI-level inheritance validator | Missing |
| 5 | Property enumeration and conflict checks | methods/getters scanning in `ImmutableType` | declared properties scanning in `ImmutableType` | Unified "logical property" abstraction | Missing |
| 6 | Property naming normalization (`isX`/`getX`) | `ImmutableProp` getter-to-prop mapping | Kotlin property name direct use | LSI property naming policy | Missing |
| 7 | Collection/list strict checks | `isCollection` + `isListStrictly` | `collectionType/listType` assignability checks | LSI type-system ops | Missing |
| 8 | Nullable rules and descriptor validation | `PropDescriptor` usage in `ImmutableProp` | same semantic build in KSP `ImmutableProp` | LSI nullability + descriptor adapter | Partial |
| 9 | Recursive/meta annotation lookup | `RecursiveAnnotations.of` | `recursiveAnnotationOf` | LSI recursive annotation query | Missing |
|10| Converter generic extraction | `GenericParser` + `ConverterMetadata` | same pair in KSP ext | LSI generic parser interface | Partial（KSP path done） |
|11| DTO base type extraction | `DtoProcessor.parseDtoTypes` + `AptDtoCompiler` | `DtoProcessor.findDtoTypeMap` + `LsiDtoCompiler`（原 `KspDtoCompiler`） | LSI-based DTO source resolver | Done（APT/KSP） |
|12| Error family scanning | `ErrorProcessor.getErrorFamilies` | `ErrorProcessor.findErrorTypes` | Unified enum+annotation scan | Partial（KSP scan/generate done） |
|13| Transactional target scanning/validation | `TxProcessor.process/validateType` | `TxProcessor.process/isTxType/validateType` | Unified class validation rules | Partial（KSP scan/generate done） |
|14| TypedTuple scan/validate/generate trigger | `TypedTupleProcessor` | `TypedTupleProcessor` | Unified tuple candidate resolver | Partial（KSP done） |
|15| ExportDoc type/doc property extraction | `ExportDocProcessor` | `ExportDocProcessor` | Unified doc extraction over class/field/property | Missing |
|16| Client API schema extraction | `ClientProcessor.handleService/handleMethod/fillType...` | same semantic methods in KSP `ClientProcessor` | LSI TypeRef traversal + annotation resolver | High gap |
|17| Multi-stage orchestration and barriers | APT delayed names in one processor | KSP `dependsOn/barrier` graph | Platform-neutral orchestration contract | Missing |

## LSI Readiness Assessment

### Already Available

- Structural interfaces: `LsiClass`, `LsiField`, `LsiMethod`, `LsiType`, `LsiAnnotation`.
- Basic APT and KSP wrappers:
  - `lib/lsi/lsi-apt/*`
  - `lib/lsi/lsi-ksp/*`
- Jimmer semantic extensions for class/field annotations:
  - `lib/lsi/lsi-jimmer/src/main/kotlin/site/addzero/lsi/jimmer/*`

### Critical Gaps Blocking Full Migration

1. Type-system operations are missing in LSI core (assignability/subtyping/strict-list checks).
2. Diagnostic anchor abstraction is missing (pointing exact declaration/property for errors).
3. Round/file resolver abstraction is not complete (`all/new`, annotation-indexed scans).
4. `LsiFiler` source/resource APIs are now available on APT/KSP, but generator call sites are not fully migrated.
5. `lsi-jimmer` 已切到 `lsi-core`-first，但其中仍混有部分 Jimmer runtime / KotlinPoet 物化语义，后续还需要继续收敛边界。
6. Property target unification is incomplete (APT getter model vs KSP property model).
7. Recursive annotation and use-site target semantics are platform-specific utility code.
8. Orchestration SPI semantics differ (APT staged manual flow vs KSP graph scheduling).

## Target Architecture (Migration End State)

1. `lsi-core` owns:
   - Common symbol model and query APIs.
   - Type-system capability interfaces.
   - Round scan and file generation interfaces.
2. `lsi-apt` and `lsi-ksp` own:
   - Platform adapters only.
   - No domain logic (Jimmer rules should not live in adapters).
3. `compiler` domain modules own:
   - Jimmer-specific semantic validation and generation orchestration.
   - Shared extraction flow using LSI interfaces.
4. APT/KSP entry processors own:
   - Bootstrapping adapters and wiring only.

## Detailed Migration Plan

### Phase 0 - Baseline and Safety Rails

Action items:

- [ ] Build a full module matrix of paired APT/KSP processors and generators.
- [ ] Capture representative golden generated outputs for immutable/dto/client/error/tx/tuple/doc.
- [ ] Document non-negotiable behavioral invariants (naming, nullability, error wording category).
- [ ] Add migration switch flag (default off) at processor bootstrap level.

Exit criteria:

- Reproducible baseline snapshots exist for both APT and KSP.
- Flag can disable any in-progress LSI path instantly.

### Phase 1 - Complete LSI Capabilities Required by Duck Behaviors

Action items:

- [x] Define `LsiTypeOps` (assignable/subtype/isEnum/isCollection/isListStrictly/listElementType).
- [x] Define `LsiResolver` (`allClasses`, `newClasses`, `findClassesAnnotatedWith`, `findClassByQualifiedName`, filter overloads).
- [x] Define `LsiDiagnosticAnchor` for class/field/method/property-level error pinpointing.
- [x] Add KSP implementation for `LsiFiler` parity with APT.
- [x] Move KSP-only naming bridges (`className/draftName/propsName`) out of KSP-only dependency path.
- [x] Refactor `lsi-jimmer` to depend on `lsi-core` abstractions first; platform bridges optional.

Exit criteria:

- Same API surface is available from both APT and KSP adapters for required behaviors 1-10.
- `project/compiler` modules can consume LSI APIs without importing raw `KS*` or `TypeElement` in new code paths.

### Phase 2 - Extract Shared Immutable Meta Reader

Action items:

- [ ] Introduce shared immutable meta reader module in `project/compiler` (IR-only).
- [ ] Port immutable type discovery, inheritance checks, and property semantics into shared reader.
- [ ] Keep current generators unchanged; feed them with adapter-compatible metadata view.
- [ ] Keep old APT and KSP readers behind fallback until parity is proven.

Exit criteria:

- Immutable metadata (type count, prop ids, id/version/logicalDeleted/association classification) matches baseline on both engines.

### Phase 3 - Migrate Processor-Level Scanners (Low-Risk First)

Action items:

- [x] Migrate `ErrorProcessor` scanning to LSI resolver (KSP path).
- [x] Migrate `TxProcessor` scan/validate rules to shared validator (KSP path, `TxLsiValidator`).
- [x] Migrate `TypedTupleProcessor` scan/validate trigger path (KSP path).
- [x] Migrate `ExportDocProcessor` declaration/doc extraction path (KSP path).
- [x] Migrate `ImmutableProcessor` to lifecycle split (`onRound` collect / `onFinish` generate, KSP path).

Exit criteria:

- Generated outputs and error categories match baseline for all migrated processors.

### Phase 4 - Migrate DTO Path

Action items:

- [x] Introduce LSI-backed source type resolver for DTO compiler bootstrap（APT/KSP path）.
- [x] Keep `DtoCompiler` generic contracts intact; only replace source extraction plumbing.
- [x] Unify enum constant and generic argument extraction through adapter interfaces（APT/KSP path）.

Exit criteria:

- DTO compilation and generated serializers/builders match baseline for both APT and KSP.

### Phase 5 - Migrate Client Path (Highest Complexity)

Action items:

- [x] Extract shared schema traversal flow (`handleService`, `handleOperation`, `fillType` style pipeline)（KSP path now LSI-first; APT shared extraction pending).
- [x] Build LSI-based type graph walker that supports generic args, fetch-by semantics, and nullability（KSP path done; cross-engine shared module pending).
- [x] Retain platform-specific fallback for unresolved edge cases behind explicit adapter hooks（KSP hook contract in place; APT hook wiring pending).

Progress (2026-03-08):

- Added `LsiClientApiRules` and migrated KSP `ClientProcessor` service/operation判定到 LSI helper（覆盖 `isApiService/isApiOperation/onRound` 规则入口）。
- Added `LsiClientSchemaTraversal` and migrated KSP `ClientProcessor` 的 `handleService/handleOperation` 遍历流程到 LSI 管线（类型填充与异常提取通过回调注入，便于后续 APT 复用同一遍历语义）。
- Added `LsiClientSchemaTraversalHooks` adapter hook contract for platform fallback (`operationCandidates/isOperationAccepted/onParameterTypeFailure/onReturnTypeFailure`), and wired KSP processor via explicit `KspClientSchemaTraversalHooks`.
- `jimmer-ksp-ext` decoupling slice: `MetaException(LsiClass, ...)` no longer calls `toKSClassDeclaration`; diagnostics now stay LSI-first (`LsiDiagnosticAnchor`) and only carry `KSDeclaration` when original source is KS symbol.
- `jimmer-ksp-ext` decoupling slice: `RecursiveAnnotations.recursiveAnnotationOf(LsiField)` no longer calls `toKSPropertyDeclaration`; `LsiAnnotation` now exposes meta-annotation traversal capability (`annotations`) and KSP adapter provides implementation.
- `jimmer-ksp-ext` decoupling slice: `GenericParser` removed `LsiClass -> toKSClassDeclaration` bridge; parser now traverses `LsiClass.superTypes` + `typeParameterNames` with LSI generic substitution.
- Fixed SPI import regressions after `jimmer-processor-spi` removal (KSP processors + `JimmerProcessor` now resolve `site.addzero.lsi.processor.ProcessorSpi`).
- Compile gate passed:
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`

Progress (2026-03-08, continuation):

- `project/compiler/client/jimmer-ksp-client/.../ClientProcessor.kt` completed source-level decoupling from `KS*` and `kotlinpoet-ksp`; processor core now uses `LsiClass/LsiMethod/LsiField/LsiType` end-to-end.
- `ExportDocProcessor` switched resource output to `ctx.lsiFiler.createResourceFile(...)`, removing direct KSP `CodeGenerator` usage in processor core path.
- `LsiFiler` expanded with resource file contract and platform implementations:
  - `lib/lsi/lsi-core/.../LsiFiler.kt` adds `createResourceFile`.
  - `lib/lsi/lsi-ksp/.../codegen/KspLsiFiler.kt` supports source/resource creation.
  - `lib/lsi/lsi-apt/.../codegen/AptLsiFiler.kt` supports source/resource creation.
- LSI symbol capability补全（supporting Client path):
  - `LsiMethod` adds `isPublic` + `typeParameterCount`.
  - `LsiField` adds `isPublic`.
  - KSP/APT adapters implemented these new members.
  - `LsiAnnotation.getClassArgument(...)` added in KSP ext utilities for LSI annotation argument access.
- `jimmer-ksp-ext` decoupling slice:
  - `immutable/meta/ImmutableType.kt` removed direct `toKSClassDeclaration/toKSPropertyDeclaration/toKSFunctionDeclaration` calls.
  - property/function declaration binding now uses resolver lookup + declaration-name matching inside current class scope.
  - all replacements include source-coverage comments near the migrated call sites for later coverage audit.
- `jimmer-ksp-ext` decoupling slice:
  - `immutable/meta/ImmutableProp.kt` moved transient/converter/formula annotation argument extraction to LSI annotation flow.
  - removed `KSClassDeclaration -> LsiClass` ad-hoc conversions in converter/transient path; now reads `LsiAnnotation.getClassArgument/getListArgument`.
  - `targetLsiClassName` now resolves through `LsiResolver.findClassByQualifiedName(...)` instead of direct KS->LSI conversion helper.
- `jimmer-ksp-ext` decoupling slice:
  - `immutable/meta/ImmutableProp.kt` association/reverse/id-view/many-to-many-view semantic checks now read LSI class/annotation model first (`isJimmerType/isJimmerEntity` + `lsiAnnotation(...)`).
  - `targetType` metadata binding switched to `ctx.typeOf(LsiClass)` path, reducing direct KS declaration handoff in core meta graph assembly.
  - retained KSP->LSI fallback wrapper (`toLsiClass`) only as adapter fallback when resolver cannot locate target by qualified name.
  - `LogicalDeleted`/`Default` initialization-time validation now checks annotation presence via `lsiAnnotation(...)` instead of direct `KSPropertyDeclaration.annotation(...)`.
  - `isList` decision now uses LSI collection semantics (`LsiType.isCollectionType` + raw qualified name guard), removing `Context.collectionType/listType/mapType` dependency from `ImmutableProp`.
  - list/map guard semantics are aligned to APT strict-list rule path (`collection must be immutable list`, map forbidden for associations).
  - `PropDescriptor` target-annotation resolution now derives from `targetLsiClass` Jimmer semantics (`isJimmerEntity/isJimmerMappedSuperclass/isJimmerEmbeddable/isJimmerImmutable`) instead of direct `targetDeclaration.annotation(...)`.
  - `isRecursive` subtype detection switched from KS `asStarProjectedType().isAssignableFrom(...)` to LSI inheritance-graph traversal (`superClasses/interfaces` DFS), eliminating that KS type-assignability dependency in this path.
  - `PropDescriptor` annotation collection + mappedBy detection now reads `LsiAnnotation` (`attributes["mappedBy"]`) instead of direct KS annotation-argument traversal.
  - target-type `@MappedSuperclass` illegality and `isKey` flag detection are now checked through LSI semantics (`isJimmerMappedSuperclass` / LSI annotation lookup).
- `jimmer-ksp-ext` decoupling slice:
  - `immutable/meta/ImmutableProp.kt` removed `targetDeclaration: KSClassDeclaration`; target type extraction now uses `LsiType` (`typeParameters/lsiClass`) as the single source.
  - target class resolving is now pure LSI (`targetResolvedLsiType.lsiClass + lsiResolver.findClassByQualifiedName`), removing `targetDeclaration.toLsiClass(...)` fallback.
  - `PropDescriptor` target type name now comes from LSI target class/type text instead of `KSClassDeclaration.fullName`.
  - `isList` explicit `@Scalar` recursion switched from `KSAnnotation` traversal to `LsiAnnotation.annotations` traversal.
- `jimmer-ksp-ext` cleanup slice:
  - `Context.kt` removed unused `collectionType/listType/mapType` (`KSType`) round caches after `ImmutableProp.isList` completed LSI collection semantics migration.
- `jimmer-ksp-ext` decoupling slice:
  - `immutable/meta/ImmutableType.kt` `sqlAnnotationType` now derives from LSI Jimmer semantics (`isEntity/isMappedSuperclass/isEmbeddable`) instead of `classDeclaration.annotation(...)`.
  - formula validation path (`@Formula` sql/dependencies checks), declared-property `@Id` ordering, and `idPropNameMap` association checks now read LSI annotations first (with KS->LSI one-way fallback merge for compatibility).
  - `immutable/generator/utils.kt` added LSI overload for validation-message extraction; `ImmutableType/ImmutableProp` now consume LSI annotation lists and keep KS declarations only as error anchors.
  - `immutable/meta/ImmutableType.kt` removed `KSFunctionDeclaration`回查绑定；非抽象函数校验改为直接基于 `LsiMethod`，并通过 `MetaException(LsiMethod, ...)` 提供方法级诊断锚点。
  - `immutable/meta/ImmutableType.kt` 属性覆盖与 Formula 抽象分支校验改为直接基于 `LsiField`（含 `MetaException(LsiField, ...)` 锚点）；`@Id` 排序判定调用改为 `propLsiAnnotation(field, ...)`，减少预校验阶段对 `KSPropertyDeclaration` 依赖。
  - `immutable/meta/ImmutableProp.kt` `isKotlinFormula` 抽象判定优先使用 `LsiField.isAbstract`（仅缺失时回落 `KSPropertyDeclaration.isAbstract()`）。
  - `immutable/meta/ImmutableProp.kt` 注解读取统一到 `allLsiAnnotations()`（优先 `LsiField.annotations`），`lsiAnnotation/descriptorLsiAnnotations/validationMessages` 不再直接依赖 `KSAnnotated.annotations(...)` 路径。
  - `immutable/generator/utils.kt` `copyNonJimmerMethodAnnotations` 改为消费 `ImmutableProp.methodAllLsiAnnotations()` + `LsiAnnotation.forFun()`，移除对 `getterAnnotations` 与 `KSDeclaration.forFun()` 的直接依赖。
  - `immutable/meta/ImmutableProp.kt` `recursiveLsiAnnotationOf` 改为纯 LSI 注解树递归（`allLsiAnnotations`），移除对 KS 递归注解工具的依赖；冲突报错优先使用 `MetaException(LsiField, ...)` 锚点。
  - `immutable/meta/ImmutableProp.kt` 构造参数 `lsiField` 升级为非空，属性语义（`isNullable/isCollection/isKotlinFormula/allLsiAnnotations/validationMessages`）改为以 LSI 字段为唯一来源，去除该层对 KS 注解 fallback 的依赖。
  - `immutable/meta/ImmutableType.kt` 类型级 validation message 锚点切换为 `LsiClass`（`parseValidationMessages(..., lsiClass)`），减少类型级校验对 KS class 节点的依赖。
- `jimmer-ksp-ext` diagnostics decoupling slice:
  - `MetaException.kt` added LSI-native constructors for `LsiField` and `LsiMethod` (`FIELD/METHOD` anchors), so method/property validation can report precise diagnostics without forcing LSI->KSP symbol fallback.
- `lsi-core/lsi-ksp` symbol capability补全:
  - `LsiField` 新增 `isAbstract` 语义能力（default false），KSP adapter (`KspLsiField`) 已实现该能力，用于 Immutable Formula 抽象/非抽象分支对齐校验。
  - `LsiField.annotations` 语义统一为“字段相关全部注解”；KSP adapter 将 `property/getter/returnType` 三处注解直接映射到该入口，承接原 `KSAnnotated.annotations(...)` 的多目标聚合行为。
- `jimmer-ksp-ext` utility alignment slice:
  - `utils.kt` `LsiField.annotations(...)` 改为基于 `annotations`，统一上层注解查询行为到 LSI 语义。
  - `utils.kt` `LsiField.annotation(...)` 改为基于 `annotations`，对齐 property/getter/returnType 的单值注解查询语义。
  - `util/RecursiveAnnotations.kt` 的 `LsiField.recursiveAnnotationOf` 递归入口改为 `annotations`，覆盖 getter/returnType 注解递归路径。
  - `utils.kt` 增加 `LsiAnnotation.getEnumListArgument(...)`，用于在 LSI 层解析 `@Target` 等枚举数组属性，承接原 KS 解析能力。
- `jimmer-ksp-ext` DTO bridge cleanup:
  - DTO 编译器（现 `LsiDtoCompiler`，原 `KspDtoCompiler`）的 `isGeneratedValue` 改为 `ImmutableProp.lsiAnnotation(...)` 判定，移除对 `ImmutableProp.annotation(KS)` 的依赖。
- `immutable` generator decoupling slice:
  - `jimmer-ksp-immutable/.../ProducerGenerator.kt` and `.../FetcherDslGenerator.kt` switched `ImmutableProp.annotation(...)` checks (`OneToOne`/`JoinTable`) to `ImmutableProp.lsiAnnotation(...)`, reducing generator-layer KS annotation reads.
  - `jimmer-ksp-ext/.../ImmutableProp.kt` exposes `lsiAnnotation(...)` for cross-module generator consumption and removed unused KS-only `isExplicitScalar(KSAnnotation, ...)` recursion helper.
  - `jimmer-ksp-immutable/.../generator/Validations.kt` now builds validation-constraint map from `LsiAnnotation` (LSI type annotations first, KS->LSI single-direction fallback only).
  - `jimmer-ksp-immutable/.../generator/ValidationGenerator.kt` and `.../DraftImplGenerator.kt` switched validation-annotation handling from `KSAnnotation` to `LsiAnnotation`, reducing generator-layer KSP symbol dependence in validation path.
- `immutable` processor decoupling slice:
  - `jimmer-ksp-immutable/.../ImmutableProcessor.findModelMap` scanning switched from `Context.typeAnnotationOf(KS)` to LSI semantic gate (`toLsiClass(...).isJimmerType`).
  - `ImmutableProcessor.generateJimmerTypes` SQL type筛选、Fetcher触发、包收集实体判定 switched from KS annotation reads to `ImmutableType` semantic flags (`isEntity/isMappedSuperclass/isEmbeddable`).
  - `jimmer-ksp-immutable/.../PropsGenerator.generate` embeddable branch switched from `modelClassDeclaration.annotation(Embeddable)` to `ImmutableType.isEmbeddable`.
  - `ImmutableProcessor` round model / finish model aggregation is now `ImmutableType`-centric (instead of `KSClassDeclaration` collections), and `EntityMetaConsumerSpi` input now comes from `ImmutableType.lsiClass` directly.
- `jimmer-ksp-ext` cleanup slice:
  - `Context.kt` removed obsolete `typeAnnotationOf(KSClassDeclaration)` (KS annotation scan path) after immutable processor scanning switched to LSI semantics.
- `immutable` generator entry decoupling slice:
  - `jimmer-ksp-immutable/.../DraftGenerator.kt` now consumes `List<ImmutableType>` directly, removing generator-internal `ctx.typeOf(KSClassDeclaration)` conversion.
  - `jimmer-ksp-immutable/.../FetcherGenerator.kt` and `.../PropsGenerator.kt` now consume `ImmutableType` directly (constructor level), removing direct `KSClassDeclaration` model dependency in these generator entries.
  - `PropsGenerator.addEmbeddableProp` receiver type now reuses `ImmutableType.className` instead of `KSClassDeclaration.className`.
- `jimmer-ksp-ext` cleanup slice:
  - `Context.kt` removed `typeOf(KSClassDeclaration)` compatibility overload; call sites migrated to `typeOf(LsiClass)` with KS->LSI one-way wrapping.
- `lsi-ksp` annotation argument normalization:
  - `KspLsiAnnotation` now normalizes annotation attributes into LSI-friendly values (`LsiClass`/`LsiType`/nested `LsiAnnotation`), reducing `jimmer-ksp-ext` fallback dependence on raw `KS*` argument values.
  - `jimmer-ksp-ext/utils.kt` removed `LsiClass.toKSClassDeclaration` bridge helper and removed `LsiAnnotation` class-arg fallback that re-converted `KS*` symbols.
- `jimmer-ksp-client` dependency cleanup:
  - removed direct `ksp.symbolProcessing.api` and `kotlinpoet-ksp` usage in this module.
  - keep `kotlinpoet` (required by `converterMetadata.targetTypeName` types in current shared metadata contract).
- `jimmer-ksp-ext` diagnostics decoupling slice:
  - `immutable/meta/ImmutableProp.kt` `isList` map/list guard errors switched from `MetaException(KSPropertyDeclaration, ...)` to `MetaException(LsiField, ...)`.
  - `immutable/meta/ImmutableProp.kt` `PropDescriptor.newBuilder` error callback now reports with `LsiField` anchor instead of `KSPropertyDeclaration`.
  - each replacement site adds coverage comments (`覆盖来源` + `迁移说明`) at call location to support user-side coverage audit.
- `jimmer-ksp-ext` diagnostics decoupling slice (continuation):
  - `immutable/meta/ImmutableProp.kt` all remaining property-level validation errors in this file now anchor to `LsiField` (including init legality checks, transient/converter checks, id-view/many-to-many-view checks, and formula dependency parsing).
  - `createFormulaDependency(...)` now reports with `formulaProp.lsiField` instead of `formulaProp.propDeclaration`.
  - all newly touched call sites include coverage comments (`覆盖来源` + `迁移说明`) describing original Jimmer usage and current LSI-anchor replacement.
- `jimmer-ksp-ext` diagnostics decoupling slice (ImmutableType continuation):
  - `immutable/meta/ImmutableType.kt` constructor-level immutable-annotation conflict/missing checks now report via `MetaException(lsiClass, ...)` instead of `MetaException(classDeclaration, ...)`.
  - replacement site includes coverage comments (`覆盖来源` + `迁移说明`) for audit.
- `jimmer-ksp-ext` diagnostics decoupling slice (ImmutableType + util continuation):
  - `immutable/meta/ImmutableType.kt` class-level validations (`microServiceName`/inheritance/primary super type/super-prop conflict/`idProp` rules) now consistently report via `MetaException(lsiClass, ...)`.
  - `immutable/meta/ImmutableType.kt` auto `@IdView` hint in `idPropNameMap` now reports via `MetaException(prop.lsiField, ...)` instead of `propDeclaration`.
  - `util/RecursiveAnnotations.kt` (`LsiVisitContext`) annotation-conflict diagnostics now report via `MetaException(field, ...)`, removing fallback class-anchor path.
  - all touched replacements include local coverage comments (`覆盖来源` + `迁移说明`) for user-side coverage checks.
- `jimmer-ksp-ext` utility cleanup slice (`utils.kt` + generator utils):
  - `immutable/generator/utils.kt::parseValidationMessages` removed `KSDeclaration` branch; duplicate-annotation diagnostics in generator path now keep only LSI anchors (`LsiClass/LsiField/LsiMethod`).
  - `utils.kt::KSAnnotated.annotation(qualifiedName)` duplicate-target conflict now prefers `KSPropertyDeclaration -> LsiField` one-way adaptation for diagnostics (`MetaException(LsiField, ...)`), with KS anchor fallback for compatibility.
  - `util/RecursiveAnnotations.kt` removed `KSPropertyDeclaration.recursiveAnnotationOf(...)` and its KS traversal context; utility is now pure `LsiField.recursiveAnnotationOf(...)` implementation.
  - each touched location includes local coverage comments (`覆盖来源` + `迁移说明`).
- `jimmer-ksp-error` annotation-reading cleanup slice:
  - `ErrorGenerator.family` annotation lookup switched from `KSClassDeclaration.annotation(ErrorFamily)` to `LsiClass.annotation(ErrorFamily)` (via one-way `KS -> LSI` wrapper).
  - `ErrorGenerator.declaredFieldsOf` switched `@ErrorField` extraction from KS annotation API to `LsiClass.annotations(ErrorField)` + `LsiAnnotation` argument reads (`get/getClassArgument`).
  - `ErrorGenerator` field-name reserved/duplicate diagnostics in this path now anchor to `LsiClass`.
  - each touched location includes local coverage comments (`覆盖来源` + `迁移说明`).
- `ksp context compatibility fix (build unblock)`:
  - `Context.reset` now constructs `lsiResolver/lsiFiler` via one-way adapters (`resolver.toLsiResolver()` + `environment.codeGenerator.toLsiFiler()`), and only uses `KspLsiContext` for round-stable raw KSP objects.
  - `KspLsiClass` reintroduced legacy constructor signature `(Resolver, KSClassDeclaration)` as compatibility shim (internally delegates to pure declaration ctor), avoiding cross-module constructor drift during migration.
- Compile gates passed after above changes:
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-ksp:compileKotlin`
  - `:lib:lsi:lsi-apt:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - `:lib:lsi:lsi-ksp:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin` (latest slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin` (ImmutableProp slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin` + `:lib:lsi:lsi-ksp:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin` (ImmutableProp association/id-view slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin` + `:lib:lsi:lsi-ksp:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin` (ImmutableProp isCollection/isList slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin` + `:lib:lsi:lsi-ksp:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin` (ImmutableProp descriptor/isRecursive slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin` + `:lib:lsi:lsi-ksp:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin` (ImmutableProp descriptor-annotations/isKey slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :lib:lsi:lsi-ksp:compileKotlin` (targetDeclaration removal + Context cache cleanup slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :lib:lsi:lsi-ksp:compileKotlin` (ImmutableType annotation-flow + validation-messages LSI slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :lib:lsi:lsi-ksp:compileKotlin` (immutable generator `lsiAnnotation` slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :lib:lsi:lsi-ksp:compileKotlin` (immutable processor/PropsGenerator LSI semantics slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :lib:lsi:lsi-ksp:compileKotlin` (immutable generator entry `ImmutableType` slice + Context `typeOf(KS)` removal)
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin` (immutable validation pipeline switched to `LsiAnnotation`)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin` (ImmutableType function validation switched to LSI method anchors)
  - `:lib:lsi:lsi-core:compileKotlin :lib:lsi:lsi-ksp:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin` (`LsiField.isAbstract` capability + ImmutableType Formula field-level validation slice)
  - `:lib:lsi:lsi-core:compileKotlin :lib:lsi:lsi-ksp:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:client:jimmer-ksp-client:compileKotlin` (`LsiField.annotations` 聚合 property/getter/returnType 注解语义 slice)
  - `:lib:lsi:lsi-core:compileKotlin :lib:lsi:lsi-ksp:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:client:jimmer-ksp-client:compileKotlin`（`copyNonJimmerMethodAnnotations` LSI化 + DTO compiler GeneratedValue LSI判定 slice）
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:client:jimmer-ksp-client:compileKotlin` (`ImmutableProp` 递归注解纯 LSI化 + `ImmutableType` validation 锚点 LSI化 slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:client:jimmer-ksp-client:compileKotlin` (`ImmutableProp` `lsiField` non-null化 + 属性语义 LSI单源 slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:client:jimmer-ksp-client:compileKotlin` (`ImmutableProp` map/list guard + `PropDescriptor` callback 错误锚点切换到 `LsiField` slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:client:jimmer-ksp-client:compileKotlin` (`ImmutableProp` 剩余 property-level 错误锚点统一切换到 `LsiField` slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:client:jimmer-ksp-client:compileKotlin` (`ImmutableType` 构造期注解冲突/缺失报错锚点切换到 `LsiClass` slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:client:jimmer-ksp-client:compileKotlin` (`ImmutableType` 其余 class-level 规则锚点 + `RecursiveAnnotations` 字段冲突锚点切换到 LSI slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:client:jimmer-ksp-client:compileKotlin` (`utils.kt`/`generator utils` LSI diagnostics cleanup + context compatibility unblock slice)
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:client:jimmer-ksp-client:compileKotlin` (`RecursiveAnnotations` KS 入口下线、纯 LSI 递归注解工具化 slice)
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:client:jimmer-ksp-client:compileKotlin` (`jimmer-ksp-error` 注解读取切换到 LSI 语义 slice)
  - `jimmer-ksp-transactional` codegen decoupling slice:
    - `TxGenerator` 的类/构造器/方法注解复制改为 LSI 注解流（`LsiAnnotation.toAnnotationSpec()`），移除生成层对 `KSAnnotation.toAnnotationSpec` 的直接依赖。
    - `TxGenerator` 参数与返回类型映射改为 `LsiType.toTypeName()`；保留 KS 仅用于当前 LSI 尚未覆盖的可见性/开放性语义（`private/protected/internal/open`）校验。
    - `TxGenerator` 父类类型名改为 `lsiDeclaration.toClassName()`，不再使用 `KSClassDeclaration.toClassName()`。
    - 上述替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-tuple` codegen decoupling slice:
    - `TypedTupleGenerator` 的 `toClassName`/`toTypeName` 路径切换为 LSI（`LsiClass.toClassName` + `KSType -> LsiType -> TypeName`）。
    - `safeToTypeName` 非 error type 分支不再调用 `kotlinpoet-ksp` 扩展。
    - 上述替换点已补 `覆盖来源` + `迁移说明` 注释。
  - KSP compiler module dependency cleanup slice:
    - 移除 `:project:compiler:transactional:jimmer-ksp-transactional`、`:project:compiler:tuple:jimmer-ksp-tuple`、`:project:compiler:error:jimmer-ksp-error` 对 `kotlinpoet-ksp` 的依赖。
    - 统一改为 `kotlinpoet`（保留 KSP 语义在 LSI-KSP 适配层实现）。
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin :project:compiler:tuple:jimmer-ksp-tuple:compileKotlin :project:compiler:error:jimmer-ksp-error:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin`（Tx/Tuple/Error 生成层去 `kotlinpoet-ksp` 直接依赖 slice）
  - `jimmer-ksp-dto` annotation-copy decoupling slice:
    - `DtoGenerator.addTypeAnnotations` 的基类注解复制改为 `KSAnnotation -> LsiAnnotation -> AnnotationSpec` 单向适配。
    - `DtoGenerator.addProp` 中 baseProp 注解扫描/复制改为 `LsiField.annotations` + `LsiAnnotation.toAnnotationSpec`，并通过 `toBuilder().useSiteTarget(...)` 保持 use-site target 行为。
    - `InputBuilderGenerator.addJacksonAnnotations` 的 baseProp 注解扫描/复制改为 `LsiField.annotations` + `LsiAnnotation.toAnnotationSpec`。
    - `DtoGenerator.isCopyableAnnotation` 保留 KS 兼容入口，但内部统一委托到 LSI 注解判定逻辑。
    - `DtoGenerator` 的 `filterClassName/recursionClassName` 类型查找改为 `lsiResolver.findClassByQualifiedName(...)`，并复用 `GenericParser(LsiClass, ...)` 解析泛型约束。
    - `DtoGenerator.allowedTargets` 的注解类型查找改为 `lsiResolver`，Kotlin/Java `@Target` 参数读取改为 `LsiAnnotation.getEnumListArgument(...)`。
    - 以上替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-dto` dependency cleanup slice:
    - 移除 `:project:compiler:dto:jimmer-ksp-dto` 对 `kotlinpoet-ksp` 的依赖，统一使用 `kotlinpoet` + `lsi-ksp` 适配扩展。
    - 为复用 `DocMetadata`（已迁移到 client 模块）补充 dto -> `:project:compiler:client:jimmer-ksp-client` 编译依赖。
  - `jimmer-ksp-ext` runtime context cleanup slice:
    - `Context.detectIsJackson3` 的类存在性探测改为 `resolver.toLsiResolver().findClassByQualifiedName(...)`，减少 KSP 原生 resolver 查询入口。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-immutable` finish-rebuild cleanup slice:
    - `ImmutableProcessor.onFinish` 重建待生成类型时移除 `resolver.getClassDeclarationByName(...).toLsiClass(...)` 回退，仅保留 `lsiResolver.findClassByQualifiedName(...)`。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-tuple` finish-stage lookup cleanup slice:
    - `TypedTupleProcessor` 在 `onRound` 缓存 `KSClassDeclaration`，`onFinish` 直接复用缓存声明生成，移除收尾阶段二次 `getClassDeclarationByName(...)` 回查。
    - `TypedTupleProcessor.collectAndValidate` 的声明查找改为 `onRound` 预构建声明索引（`resolver.getAllFiles().declarations`）读取，移除该路径的 `getClassDeclarationByName(...)` 调用。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-transactional` finish-stage lookup cleanup slice:
    - `TxProcessor` 在 `onRound` 预构建声明索引并缓存候选 `KSClassDeclaration`，`onFinish` 直接复用缓存声明生成，移除收尾阶段 `getClassDeclarationByName(...)` 回查。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-error` finish-stage lookup cleanup slice:
    - `ErrorProcessor` 在 `onRound` 预构建声明索引并缓存待生成声明，`onFinish` 直接复用缓存声明生成，移除收尾阶段 `getClassDeclarationByName(...)` 回查。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-dto` super-interface traversal cleanup slice:
    - `DtoInterfaces.abstractPropNames/collectMembers` 改为纯 LSI 接口模型遍历（`LsiClass/LsiMethod/LsiField`），super interface 查找改为 `lsiResolver.findClassByQualifiedName(...)`。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-transactional` sqlClient-type detection cleanup slice:
    - `TxGenerator.determineSqlClientName` 移除 `resolver.getClassDeclarationByName(KSqlClient)` 依赖，改为属性类型名 + 超类型递归判定 `isKSqlClientType()`。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-immutable` dependency cleanup slice:
    - `:project:compiler:immutable:jimmer-ksp-immutable` 移除 `kotlinpoet-ksp` 依赖，统一改为 `kotlinpoet`（该模块源码当前已无 `kotlinpoet-ksp` import 调用）。
  - `jimmer-ksp-dto` annotationOf lookup cleanup slice:
    - `DtoGenerator.annotationOf` 的 vararg 注解构造参数判定移除 `resolver.getClassDeclarationByName(...)`，改为基于 `resolver.getAllFiles()` 构建声明索引查询。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-ext` immutable declaration lookup cleanup slice:
    - `ImmutableType.classDeclaration` 移除 `resolver.getClassDeclarationByName(...)`，改为基于 `resolver.getAllFiles()` 声明遍历按 `qualifiedName` 定位。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-ext` context import cleanup slice:
    - `Context.kt` 移除未使用的 `com.google.devtools.ksp.getClassDeclarationByName` import，保持 KSP name-lookup API 实调用清零后的代码面一致性。
  - `lsi-core` constructor/vararg 能力补齐 slice:
    - 为 `LsiClass` 增加 `constructors` 抽象能力（默认空），并在 `lsi-ksp`/`lsi-apt` 适配层实现构造器映射。
    - 为 `LsiParameter` 增加 `isVararg` 语义（默认 false），并在 `lsi-ksp`/`lsi-apt`/`lsi-reflection` 适配层对齐实现。
  - `jimmer-ksp-dto` annotationOf vararg LSI 化 slice:
    - `DtoGenerator.annotationOf` 的 vararg 判定不再读取 `KSClassDeclaration.origin/primaryConstructor`，改为 `resolver.toLsiResolver().findClassByQualifiedName(...).constructors.parameters.isVararg`。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-ext` immutable prop init LSI-first slice:
    - `ImmutableProp` 初始化校验中的 `readonly/is-boolean-name/LogicalDeleted/FORBIDDEN_TYPE_NAMES/toString` 路径改为 LSI 字段/类型语义优先，移除该路径重复 `fastResolve` 依赖。
    - `ImmutableProp` 保留 `realDeclaration/typeAlias` 兼容字段，仅作为下游生成器临时兼容读取，来源统一收敛到 `resolvedKsType`。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-ext` immutable type naming LSI-first slice:
    - `ImmutableType` 的 `simpleName/name/packageName/qualifiedName/toString` 命名读取路径由 `KSClassDeclaration` fallback 收敛为 `LsiClass` 命名语义。
    - `ImmutableType.classDeclaration` 查找使用 `lsiQualifiedNameRequired`，避免多处 name fallback 分叉。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-dto` baseDocString LSI 收敛 slice:
    - `DtoGenerator.baseDocString(ImmutableProp)` 改为直接读取 `prop.lsiField`，去除 DTO 侧 `prop.propDeclaration.toLsiField(...)` 回退。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-ext` immutable prop name/comment 收敛 slice:
    - `ImmutableProp.lsiName/lsiComment` 读取收敛为 LSI field 语义，去除 `KSPropertyDeclaration` fallback。
    - `ImmutableProp` 的 type alias 校验改为复用 `resolvedKsType/typeAlias` 统一入口。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-ext` immutable type annotation/same-type 收敛 slice:
    - `ImmutableType.superPropMap` 同名属性类型冲突判定去除 KSP `fastResolve` 回退，统一使用 `LsiType.isSameType`。
    - `ImmutableType.propLsiAnnotations` 注解收集去除 KS 注解补齐回退，统一使用 `LsiField.annotations`。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-immutable` validation generator LSI 锚点收敛 slice:
    - `ValidationGenerator` 中校验异常锚点由 `prop.propDeclaration` 统一切换为 `prop.lsiField`。
    - `ValidationGenerator.isSimpleType` 去除 `ImmutableProp.realDeclaration(KS)` 依赖，改为 `TypeName.reflectionName()` 匹配。
    - `ValidationGenerator.validateBound` 去除 `ImmutableProp.typeAlias(KS)` 读取分支（type alias 已在元模型初始化阶段禁止）。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-immutable` validations source LSI-only slice:
    - `Validations.validationAnnotationMirrorMultiMap` 去除生成层 `propDeclaration.type.annotations` KS->LSI 兜底，统一读取 `lsiType.annotations`。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-ext` immutable prop compatibility field 清理 slice:
    - `ImmutableProp` 删除不再被消费的 `realDeclaration` 兼容字段；`typeAlias` 保留仅用于初始化校验（统一走 `resolvedKsType`）。
    - 替换点已补 `覆盖来源` + `迁移说明` 注释。
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:client:jimmer-ksp-client:compileKotlin`（DTO 注解复制去 `kotlinpoet-ksp` 直接依赖 slice）
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin`（DTO `getClassDeclarationByName` -> `lsiResolver` 差量替换 slice）
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:dto:jimmer-ksp-dto:compileKotlin`（`Context.detectIsJackson3` LSI resolver 化 slice）
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:tuple:jimmer-ksp-tuple:compileKotlin :project:compiler:dto:jimmer-ksp-dto:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin`（Immutable/Tuple 收尾查找去回查 + DTO LSI resolver 差量 slice）
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin :project:compiler:error:jimmer-ksp-error:compileKotlin :project:compiler:tuple:jimmer-ksp-tuple:compileKotlin :project:compiler:dto:jimmer-ksp-dto:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin`（Tx/Error/Tuple 收尾声明回查下沉 + DTO 接口遍历 LSI 化 slice）
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin :project:compiler:error:jimmer-ksp-error:compileKotlin :project:compiler:tuple:jimmer-ksp-tuple:compileKotlin :project:compiler:dto:jimmer-ksp-dto:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin`（Tx sqlClient 类型判定去 `getClassDeclarationByName` + 声明索引收敛复验 gate）
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:dto:jimmer-ksp-dto:compileKotlin :project:compiler:client:jimmer-ksp-client:compileKotlin :project:compiler:transactional:jimmer-ksp-transactional:compileKotlin :project:compiler:tuple:jimmer-ksp-tuple:compileKotlin :project:compiler:error:jimmer-ksp-error:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin`（compiler KSP 子模块去 `kotlinpoet-ksp` 依赖持续收敛 gate）
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin`（DtoGenerator/ImmutableType `getClassDeclarationByName` 实调用清零复验 gate）
  - `:lib:lsi:lsi-core:compileKotlin :lib:lsi:lsi-ksp:compileKotlin :lib:lsi:lsi-apt:compileKotlin :project:compiler:dto:jimmer-ksp-dto:compileKotlin`（LSI constructors/isVararg 补齐 + DtoGenerator vararg 判定 LSI 化 gate）
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`（ImmutableProp 初始化校验 LSI-first 差量收敛 gate）
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:dto:jimmer-ksp-dto:compileKotlin`（ImmutableType 命名读取 LSI-first + ImmutableProp name/comment 收敛 + DTO baseDocString LSI 收敛 gate）
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`（ImmutableType 同名属性类型判等与属性注解收集去 KS 回退 gate）
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:dto:jimmer-ksp-dto:compileKotlin`（ValidationGenerator 锚点/类型判定去 KS 回退 + Validations LSI-only + ImmutableProp compatibility field 清理 gate）
  - `jimmer-ksp-ext` immutable property declaration-chain cleanup slice:
    - `ImmutableProp` 构造参数从 `(KSPropertyDeclaration, LsiField)` 收敛为 `LsiField` 单源；`type alias/value class` 校验改走 `LsiField.isTypeAlias/isValueClassType`，并保留同位置 `覆盖来源 + 迁移说明` 注释。
    - `ImmutableType` 移除 `declaredKsPropertiesByName` 与 `ksPropertyDeclarationOf`；`declaredProperties/redefinedProps` 构建改为直接基于 `declaredLsiFields`，去除该链路 `KSPropertyDeclaration` 依赖。
    - `jimmer-ksp-immutable` `ImplGenerator.addProp` 的 `@Description` 文案来源改为 `ImmutableProp.lsiComment`，不再读取 `prop.propDeclaration.docString`。
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:dto:jimmer-ksp-dto:compileKotlin`（ImmutableProp 构造参数 LSI 单源 + ImmutableType 属性构建去 KS 回查链 + ImplGenerator 注释源切换 gate）
  - `jimmer-ksp-immutable` finish-stage classDeclaration dependence cleanup slice:
    - `ImmutableProcessor.onFinish` 的“文件归并 + 返回声明”路径去除 `ImmutableType.classDeclaration` 读取，改为 `onRound` 缓存 `filePath/qualifiedName/KSClassDeclaration` 索引。
    - `ImmutableProcessor.generateJimmerTypes` 多类型报错输出改为 `ImmutableType.qualifiedName`，`PackageCollector` 入参改为 `packageName/qualifiedName` 字符串语义。
    - `JimmerModuleGenerator` 资源写入输入由 `List<KSDeclaration>` 改为 `List<String>`（实体 qualifiedName），减少生成层 KS 符号依赖。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:dto:jimmer-ksp-dto:compileKotlin`（ImmutableProcessor finish-stage 去 classDeclaration 依赖 + JimmerModuleGenerator 输入 LSI 语义化 gate）
  - `jimmer-ksp-ext + jimmer-ksp-dto` classDeclaration property cleanup slice:
    - `ImmutableType` 删除 `classDeclaration: KSClassDeclaration` 缓存属性，类型命名/注解入口统一收敛到 `LsiClass` 语义。
    - `DtoGenerator.addTypeAnnotations` 的基类注解复制由 `baseType.classDeclaration.annotations(KS)` 切换为 `baseType.lsiClass.annotations(LSI)`。
    - `DtoGenerator` 移除仅用于该桥接路径的 `isCopyableAnnotation(KSAnnotation, ...)` 兼容入口，保留 LSI 注解语义入口。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `:project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:dto:jimmer-ksp-dto:compileKotlin :project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`（ImmutableType classDeclaration 属性下线 + DtoGenerator 基类注解复制 LSI 化 gate）
  - `jimmer-ksp-tuple` processor/generator LSI-first slice:
    - `TypedTupleProcessor` 的 `data class/top-level/super class` 校验由 `KSClassDeclaration` 语义切换为 `LsiClass.isData/isTopLevel/superClasses`。
    - `TypedTupleGenerator` 输入由 `KSClassDeclaration` 切换为 `LsiClass`，属性枚举由 `getDeclaredProperties()` 切换为 `LsiClass.fields` + `declaringClass` 过滤。
    - `TypedTupleGenerator.safeToTypeName` 入口由 `KSTypeReference.resolve()` 切换为 `LsiType`，失败时按 `qualifiedName/presentableText` 降级。
    - `TypedTupleProcessor` 的 `ProcessorSpi` 返回值由 `List<KSClassDeclaration>` 收敛为 `Boolean`，onFinish 直接消费 `LsiClass` 收集结果，处理器层不再缓存/返回 KS 声明。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:dto:jimmer-ksp-dto:compileKotlin`（TypedTuple 校验/生成入口 LSI-first 化 gate）
  - `lib/lsi` constructor capability补齐 slice:
    - `LsiClass` 新增 `primaryConstructor`，用于承接 Kotlin primary constructor 语义，避免事务生成层回落 `KSClassDeclaration.primaryConstructor`。
    - `KspLsiClass` 实现 `primaryConstructor`，继续保持仅允许 `KSP/APT -> LSI` 的单向适配。
  - `jimmer-ksp-transactional` processor/generator LSI-first continuation slice:
    - `TxProcessor` 收集结果由 `KSClassDeclaration` 收敛为 `LsiClass`，`onFinish` 直接消费 LSI 类型生成，移除 KS 声明索引/回查。
    - `TxGenerator` 输入由 `KSClassDeclaration` 切换为 `LsiClass`；源文件输出由 `CodeGenerator.createNewFile(...)` 切换为 `ctx.lsiFiler.createSourceFile(...)`。
    - `TxGenerator.determineSqlClientName` 改为 `LsiClass.fields` + `declaringClass` 过滤 + `LsiType.superTypes` 递归判定 `KSqlClient`。
    - `TxGenerator` 构造器/方法生成改为消费 `LsiClass.primaryConstructor/constructors/methods` 与 `LsiMethod` 的可见性、参数、返回类型语义；生成层不再直接依赖 `KSFunctionDeclaration`。
    - `:project:compiler:transactional:jimmer-ksp-transactional` 移除直接 `implementation(libs.ksp.symbolProcessing.api)` 依赖。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `:lib:lsi:lsi-core:compileKotlin :lib:lsi:lsi-ksp:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`（Tx processor/generator 全链路 LSI-first gate）
  - `:lib:lsi:lsi-apt:compileKotlin :lib:lsi:lsi-reflection:compileKotlin :project:jimmer-ksp:compileKotlin`（`primaryConstructor` 能力补齐后的联编 gate）
  - `lib/lsi` enum-constant capability补齐 slice:
    - 新增 `LsiEnumConstant` 与 `LsiClass.enumConstants`，统一承接 KSP `ENUM_ENTRY` / APT `ENUM_CONSTANT` 的常量级注解语义。
    - `KspLsiClass` / `AptLsiClass` 分别实现 `enumConstants`，枚举常量读取继续保持仅允许 `KSP/APT -> LSI` 的单向适配。
    - `MetaException` 新增 `LsiEnumConstant` 锚点，Error 常量级校验不再依赖 KS enum entry 节点。
  - `jimmer-ksp-error` processor/generator LSI-first slice:
    - `ErrorProcessor` 收集结果由 `KSClassDeclaration` 收敛为 `LsiClass`，`onFinish` 直接消费 LSI 类型生成，移除 KS 声明索引/回查。
    - `ErrorGenerator` 输入由 `KSClassDeclaration` 切换为 `LsiClass`；源文件输出由 `CodeGenerator.createNewFile(...)` 切换为 `ctx.lsiFiler.createSourceFile(...)`。
    - `ErrorGenerator` 的枚举项遍历、`subTypes` 注解装配、伴生工厂方法与嵌套异常子类型生成全部改为消费 `LsiClass.enumConstants` / `LsiEnumConstant`。
    - `ErrorGenerator` 的字段缓存键由 KS 声明对象切换为稳定 LSI key（`qualifiedName` / `owner#constant`），共享字段合并逻辑不再依赖 `parentDeclaration`。
    - `:project:compiler:error:jimmer-ksp-error` 移除直接 `implementation(libs.ksp.symbolProcessing.api)` 依赖。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `:lib:lsi:lsi-core:compileKotlin :lib:lsi:lsi-ksp:compileKotlin :lib:lsi:lsi-apt:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin :project:compiler:error:jimmer-ksp-error:compileKotlin`（Error enum constant LSI 能力 + processor/generator LSI-first gate）
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin :project:jimmer-ksp:compileKotlin`（Error 模块去直接 KSP API 依赖复验 gate）
  - `jimmer-ksp-tuple` output/filer cleanup slice:
    - `TypedTupleGenerator.generate` 的文件输出由 `CodeGenerator.createNewFile(...)` + `Dependencies` 切换为 `ctx.lsiFiler.createSourceFile(...)`。
    - `TypedTupleGenerator` 保持 `LsiClass` / `LsiField` / `LsiType` 生成语义不变，仅收敛 KSP 文件写入入口。
    - `:project:compiler:tuple:jimmer-ksp-tuple` 移除直接 `implementation(libs.ksp.symbolProcessing.api)` 依赖。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin :project:jimmer-ksp:compileKotlin`（Tuple generator 输出 LSI filer 化 + 去直接 KSP API 依赖复验 gate）
  - `jimmer-ksp-immutable` source-output cleanup slice:
    - `DraftGenerator`、`PropsGenerator`、`FetcherGenerator` 的源文件输出由 `CodeGenerator.createNewFile(...)` 切换为 `ctx.lsiFiler.createSourceFile(...)`。
    - `ImmutableProcessor.generateJimmerTypes` 对上述三个生成器的调用改为不再传递 `CodeGenerator/allFiles`，仅保留 `KSFile` 分组语义与 `ImmutableType` 元数据输入。
    - `JimmerModuleGenerator` 的 `entities` 资源输出切换为 `ctx.lsiFiler.createResourceFile("META-INF/jimmer/entities", ...)`，`JimmerModule` 源文件输出切换为 `ctx.lsiFiler.createSourceFile(...)`；保留 `codeGenerator.generatedFile` 仅用于现有资源合并探测。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin :project:jimmer-ksp:compileKotlin`（Immutable generators/JimmerModule 输出入口 LSI filer 化 gate）
  - `jimmer-ksp-immutable` processor/generator de-KSP slice:
    - `ImmutableProcessor` 的扫描入口由 `resolver.getNewFiles() + KSClassDeclaration` 切换为 `ctx.lsiResolver.newClasses()`；分组键由 `KSFile` 收敛为 `packageName + fileName` source key。
    - `ImmutableProcessor` 的类型合法性校验（interface/type-parameters/visibility）改为直接依赖 `LsiClass` 语义；为此补充 `LsiClass.isPrivate/isProtected`，并在 KSP/APT 适配器中对齐实现。
    - `ImmutableProcessor` 的 `onFinish()` 返回值改为 `Unit`，删除仅用于回填返回声明的 KS 声明缓存，避免形成 `LSI -> KS` 反向桥接。
    - `DraftGenerator`、`PropsGenerator`、`FetcherGenerator` 的输入由 `KSFile` 下沉为 `sourcePackageName/sourceFileName` 普通字符串，生成器内部不再 import KSP API。
    - `JimmerModuleGenerator` 的资源合并探测与 `EntityMetaConsumerSpi` 日志输出下沉到 `Context` 胶水（`guessGeneratedJimmerResourceFile` / `logInfo`），当前模块不再直接触碰 `CodeGenerator` / `logger`。
    - `:project:compiler:immutable:jimmer-ksp-immutable` 移除直接 `implementation(libs.ksp.symbolProcessing.api)` 依赖；当前 compiler 目录内已无未注释的直接 KSP API 依赖。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `:lib:lsi:lsi-core:compileKotlin :lib:lsi:lsi-ksp:compileKotlin :lib:lsi:lsi-apt:compileKotlin`（LsiClass visibility 差量补齐 gate，已通过）
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`（当前仍被仓库既有 classpath 坏点阻塞：`project/jimmer-core/...`、`lib/lsi/lsi-jimmer/...`、`project/compiler/jimmer-ksp-ext/...`；本批静态扫描已确认 immutable 模块源码无直接 KSP import）
  - `jimmer-ksp-client` schema traversal stabilization slice:
    - `LsiClientSchemaTraversal` 的 `api/operation/parameter/typeRef` Java-SAM 回调改为显式 `ApiServiceImpl/ApiOperationImpl/ApiParameterImpl/TypeRefImpl` 参数类型，避免 Kotlin 对 `SchemaBuilder` 泛型回调推断漂移。
    - `ClientProcessor.createBuilder()` 恢复旧的 `existingSchema()` 资源回读语义，改为经由 `Context.guessGeneratedJimmerResourceFile("client")` 做 `META-INF/jimmer/client` 合并探测。
    - `ClientProcessor` 中 `prop/typeRef/constant` 等 `SchemaBuilder` 回调同步显式类型化，减少 Java builder DSL 在 K2 下的推断噪音。
    - 当前 `jimmer-ksp-client` 源码已无直接 KSP import；编译仍主要被仓库既有 classpath 坏点拦住（`project/jimmer-core/...` / `lib/lsi/lsi-jimmer/...` / `project/compiler/jimmer-ksp-ext/...`）。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-ext` GenericParser cleanup slice:
    - `GenericParser` 删除未再使用的 `KSClassDeclaration` 兼容构造，仅保留 `LsiClass` 主入口，进一步收紧到 LSI-first 泛型解析 API。
    - 当前 `jimmer-ksp-ext` 残余的 KSP 边界文件收敛为 `Context.kt`、`MetaException.kt`、`KSTypeReferences.kt`、`utils.kt`。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-ext` boundary shrink slice:
    - `KSTypeReferences.kt` 已删除；`KSAnnotation` 的限定名解析收敛到 `utils.kt::qualifiedNameOrEmpty()`，不再保留独立 `KSTypeReference.fastResolve()` 辅助层。
    - `utils.kt::KSAnnotated.annotation(qualifiedName)` 的属性冲突诊断改为经由 `Context.lsiFieldOrNull(...)` 取得字段级 LSI 锚点；`utils.kt` 不再直接透传 `Context.resolver` 做 `KSPropertyDeclaration -> LsiField` 适配。
    - `Context.kt` 新增 `lsiFieldOrNull(...)` 胶水入口，并将 `jackson3/jacksonTypes` 从 object eager 初始化改为 `reset(...)` 后缓存，避免 `Context` 首次装载时触发 `lateinit resolver/environment` 访问。
    - 当前 `jimmer-ksp-ext` 残余的 KSP 边界文件进一步收敛为 `Context.kt`、`MetaException.kt`、`utils.kt`。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `jimmer-ksp-ext` public API cleanup slice:
    - `utils.kt` 已删除全部 KS public overload（`KSAnnotated`/`KSAnnotation`/`KSClassDeclaration` 相关 helper）；当前仅保留面向业务模块暴露的 LSI public API，`jimmer-ksp-ext/utils.kt` 已无直接 KSP import。
    - 随着 `utils.kt` 的 KS 冲突诊断路径被整体移除，`Context.kt::lsiFieldOrNull(...)` 已回收，避免遗留无调用的 KSP->LSI 胶水入口。
    - `MetaException.kt` 首先移除了无调用方的 `childDeclaration` 双锚点兼容构造，为后续彻底 LSI-only 化清空了 KS 兼容面。
    - 当前 compiler 目录内剩余直接 KSP import 文件已进一步收敛为 2 个：`Context.kt`、`MetaException.kt`。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `MetaException` LSI-only slice:
    - `MetaException.kt` 已删除全部 `KSDeclaration` 载荷与 KSP 诊断锚点依赖，当前仅保留 `LsiClass` / `LsiField` / `LsiMethod` / `LsiEnumConstant` 入口；`project/compiler/jimmer-ksp-ext` 内已无该文件的直接 KSP import。
    - `project/jimmer-ksp/JimmerProcessor.kt` 的 `process/finish` 错误上报改为只消费 `MetaException.message`，移除 `MetaException -> KSDeclaration` 的反向依赖。
    - 当前 compiler 目录内剩余直接 KSP import 文件已进一步收敛为 1 个：`project/compiler/jimmer-ksp-ext/.../Context.kt`。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `Context` LSI-only slice:
    - `project/compiler/jimmer-ksp-ext/.../Context.kt` 的 `reset(...)` 已改为只接收 `LsiResolver` / `LsiFiler` / 普通 `options` / `LsiFile` 与日志回调；`Context` 不再直接 import `Resolver`、`SymbolProcessorEnvironment`、`KspLsiContext`、`KspLsiFile`、`toLsiResolver`、`toLsiFiler`。
    - `project/jimmer-ksp/JimmerProcessor.kt` 承接原 `Context.reset(resolver, environment)` 的平台胶水职责：最外层先设置 `KspLsiContext`，再完成 `KSP -> LSI` 单向适配，并以回调方式注入 `Context`。
    - `Context.firstLsiFileOrNull()`、`guessGeneratedJimmerResourceFile(...)`、`logInfo(...)` 均改为消费平台层注入回调；compiler 侧只保留 LSI/普通值语义。
    - 当前 `project/compiler` 目录内已无任何直接 KSP import。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`（client traversal stabilization gate，当前仍被仓库既有 classpath 坏点阻塞，但 `LsiClientSchemaTraversal` 的显式 builder lambda 已补齐）
  - `jimmer-ksp-dto` top-level output cleanup slice:
    - `DtoGenerator.generate` 的顶层 DTO 文件输出由 `CodeGenerator.createNewFile(...)` 切换为 `ctx.lsiFiler.createSourceFile(...)`。
    - 内嵌 DTO 生成流程保持现状，仅收敛顶层文件写入入口，不扩大本批 KSP 语义改动面。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin :project:jimmer-ksp:compileKotlin`（DtoGenerator 顶层输出入口 LSI filer 化 gate）
  - `jimmer-ksp-dto` annotation/converter residual cleanup slice:
    - `DtoProcessor.generateDtoTypes` 不再向 `DtoGenerator.generate(...)` 透传 `KSFile` 列表；DTO 生成入口只传递 DTO 元数据与 LSI 上下文。
    - `DtoGenerator.generate` 的内嵌 DTO 递归生成同步移除伪造的 `emptyList<KSFile>()` 参数，顶层/内嵌统一走 LSI-only 入口。
    - `DtoGenerator.annotationOf` 改为直接使用 `Context.lsiResolver` 判定 vararg 注解构造参数，去除生成器对 `Resolver` 的反向透传。
    - `DtoGenerator.allowedTargets` 的缓存值由 `AnnotationUseSiteTarget` 收敛为 `AnnotationSpec.UseSiteTarget`，生成器内部不再依赖 KSP use-site 枚举。
    - `InputBuilderGenerator.addJacksonAnnotations` 与 DTO converter 列表化路径去除 `Resolver` 透传，统一复用无平台参数的 LSI-first 元数据语义。
    - `Context.firstLsiFileOrNull()` / `firstSourceFilePath` 吸收 `DtoProcessor` 的 KSP 文件扫描胶水；DTO 模块当前不再直接 import KSP API。
    - `:project:compiler:dto:jimmer-ksp-dto` 移除直接 `implementation(libs.ksp.symbolProcessing.api)` 依赖。
    - 当前 compiler 目录内剩余直接 `implementation(libs.ksp.symbolProcessing.api)` 的模块只剩 `:project:compiler:immutable:jimmer-ksp-immutable`。
    - 上述替换点均补充 `覆盖来源` + `迁移说明` 注释。
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin :project:compiler:jimmer-ksp-ext:compileKotlin`（DTO annotation/converter residual cleanup gate，当前被仓库既有坏点阻塞：`project/jimmer-core/.../Schemas.java`、`lib/lsi/lsi-jimmer/.../Constants.kt`、`project/compiler/jimmer-ksp-ext/...`、`project/compiler/client/jimmer-ksp-client/...`）
  - `rg -l "^import com\\.google\\.devtools\\.ksp|^import .*KS[A-Z]|^import .*Resolver\\b|^import .*AnnotationUseSiteTarget|^import .*CodeGenerator|toLsiResolver|getAllFiles\\(|getNewFiles\\(" project/compiler/jimmer-ksp-ext/src/main/kotlin -g '*.kt'`（残余 KSP 边界已从 4 个文件收敛到 3 个：`Context.kt`、`MetaException.kt`、`utils.kt`）
  - `rg -n "fastResolve\\(|Context\\.resolver|toLsiField\\(Context\\.resolver\\)|KSTypeReferences" project/compiler -g '*.kt'`（已确认 `KSTypeReferences.kt` 删除、`utils.kt` 不再直接透传 `Context.resolver`；残余命中仅为迁移注释/`KspLsiContext.resolver` 胶水）
  - `./gradlew --offline :project:compiler:jimmer-ksp-ext:compileKotlin -x :project:jimmer-core:compileJava`（仍被仓库既有坏点阻塞：`lib/lsi/lsi-jimmer/.../Constants.kt` unresolved references；本批未新增新的 `jimmer-ksp-ext` 编译错误）
  - `rg -l "^import com\\.google\\.devtools\\.ksp" project/compiler -g '*.kt'`（当前 compiler 目录剩余直接 KSP import 文件仅 2 个：`project/compiler/jimmer-ksp-ext/.../Context.kt`、`project/compiler/jimmer-ksp-ext/.../MetaException.kt`）
  - `rg -n "childDeclaration|lsiFieldOrNull\\(" project/compiler project/jimmer-ksp -g '*.kt'`（已确认 `MetaException` 双声明兼容与 `Context.lsiFieldOrNull(...)` 无残余调用）
  - `rg -l "^import com\\.google\\.devtools\\.ksp" project/compiler -g '*.kt'`（最新复验：当前 compiler 目录剩余直接 KSP import 文件仅 1 个：`project/compiler/jimmer-ksp-ext/.../Context.kt`）
  - `rg -n "MetaException\\([^\\n]*KS|declaration\\?\\.let \\{|childDeclaration" project/compiler project/jimmer-ksp -g '*.kt'`（已确认仓库内无 `MetaException(KSDeclaration, ...)` 调用，`JimmerProcessor` 也不再回读 `ex.declaration`）
  - `rg -l "^import com\\.google\\.devtools\\.ksp" project/compiler -g '*.kt'`（最新复验：`project/compiler` 目录内直接 KSP import 已清零）
  - `rg -n "Context\\.(resolver|environment)\\b|ctx\\.(resolver|environment)\\b" project -g '*.kt'`（已确认 compiler 业务代码不再回读 `Context` 的平台对象；残余命中仅为注释与 `project/jimmer-ksp` 的最外层 `KspLsiContext` 胶水）
  - `./gradlew --offline :project:compiler:jimmer-ksp-ext:compileKotlin -x :project:jimmer-core:compileJava -x :lib:lsi:lsi-jimmer:compileKotlin`（当前仍被仓库既有 classpath 坏点阻塞：`project/jimmer-core` Java API 未产出导致 `Formula/Entity/Id/Converter` 等符号不可见；本批静态扫描已确认 `Context` 平台依赖收敛完成）
  - `LSI annotation helper cleanup + reverse-bridge removal` slice（2026-03-12）:
    - `lib/lsi/lsi-core` 新增并落地 `LsiAnnotationExt` / `LsiFieldExt` / `LsiMethodExt` 的注解查询差量能力（`fullName/get/getClassArgument/getListArgument/getClassListArgument/getEnumListArgument/annotation(s)`），KSP compiler 侧不再 import 旧 `org.babyfish.jimmer.ksp.get/.../annotation/fullName` helper。
    - `project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.kt` 移除了最后一个 `import org.babyfish.jimmer.ksp.*`，当前该文件只保留 `Context` / `MetaException` 作为 KSP 边界胶水。
    - `project/compiler/jimmer-ksp-ext/.../immutable/generator/utils.kt` 中 `Constraint.validatedBy/message` 参数读取切换为 `LsiAnnotation.getClassListArgument/get`，不再直读 annotation attribute map。
    - `lib/lsi/lsi-ksp` 删除无调用方的 `LSI -> KSP` 反向桥与重复 KotlinPoet shim：`KspLsiAnnotationBridgeExt.kt`、`KspLsiAnnotationKotlinPoetExt.kt`、`KspLsiClassKotlinPoetExt.kt`、`codegen/LsiClassNameKotlinPoetExt.kt`、`KspLsiFieldBridgeExt.kt`、`KspLsiMethodBridgeExt.kt`、`KspLsiTypeBridgeExt.kt`、`KspLsiTypeKotlinPoetExt.kt`。
    - `lib/lsi/lsi-core/.../codegen/TypeNameExt.kt` 新增 `TypeName.isBuiltInType(...)`，`ValidationGenerator` 不再依赖 `jimmer-ksp-ext` 本地 helper；`ErrorGenerator` 的 `enumClassName/ErrorField.type` 装配改为直接复用 `LsiClass.toClassName()`。
    - `project/compiler/jimmer-ksp-ext/.../utils.kt` 现仅保留 `include()` 这一带编译器配置语义的 helper；`fullName/className/nestedClassName/isBuiltInType` 已下线或迁移。
  - `source filter de-ksp-ext` slice（2026-03-12）:
    - 新增 `project/compiler/jimmer-ksp-ext/.../site/addzero/context/LsiSourceFilter.kt`，将 `include/exclude` 源码过滤能力迁移为配置层 `matchesSourceFilters/matchesConfiguredSourceFilters`。
    - `Context.explicitClientApi`、`LsiClientApiRules.isClientApiService`、`DtoProcessor.resolveDtoSourceType`、`ErrorProcessor.findErrorTypes`、`ImmutableProcessor.findModelMap` 全部改为复用 `matchesConfiguredSourceFilters()`。
    - `project/compiler/jimmer-ksp-ext/.../utils.kt` 已整体删除，`project/compiler` 侧不再从 `org.babyfish.jimmer.ksp` 旧包名消费 source filter helper。
  - `generic parser / converter metadata de-util` slice（2026-03-12）:
    - `GenericParser.kt`、`ConverterMetadata.kt` 已迁移到 `project/compiler/jimmer-ksp-ext/.../site/addzero/lsi/codegen/`，包名改为 `site.addzero.lsi.codegen`。
    - `DtoGenerator` 与 `ImmutableProp` 不再 import `org.babyfish.jimmer.ksp.util.GenericParser|ConverterMetadata|converterMetadataOf`，统一改为 LSI codegen 包入口。
    - `generatedAnnotation` / `suppressAllAnnotation` 也已迁移到 `site.addzero.lsi.codegen/JimmerCodegenAnnotationExt.kt`，旧 `org.babyfish.jimmer.ksp.util` 包当前仅剩 `MetaResource.kt` 与 `RecursiveAnnotations.kt` 两个未迁移文件。
  - `legacy util package cleanup` slice（2026-03-12）:
    - `guessResourceFile(...)` 已迁移到 `project/compiler/jimmer-ksp-ext/.../site/addzero/lsi/file/JimmerGeneratedResourceFile.kt`，`project/jimmer-ksp/.../JimmerProcessor.kt` 改为通过 `site.addzero.lsi.file.guessResourceFile` 读取生成资源目录。
    - `RecursiveAnnotations.kt` 已删除；该文件在全仓范围内已无业务调用点。
    - 经过上述迁移后，`project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp/util` 目录已清空。
  - `diagnostic/codegen namespace neutralization` slice（2026-03-12）:
    - `MetaException.kt` 已迁移到 `project/compiler/jimmer-ksp-ext/.../site/addzero/lsi/diagnostic/MetaException.kt`，`GeneratorException.kt` 已迁移到 `.../site/addzero/lsi/codegen/GeneratorException.kt`；KSP compiler 业务模块与 `project/jimmer-ksp/.../JimmerProcessor.kt` 均已改为消费新命名空间。
    - `site.addzero.lsi.codegen.GenericParser` 现直接依赖 `site.addzero.lsi.diagnostic.MetaException`，消除了 `site.addzero.* -> org.babyfish.jimmer.ksp.*` 的反向依赖。
    - 在该子批完成时，`project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp` 顶层残余文件曾收敛为 3 个：`Context.kt`、`JacksonTypes.kt`、`KspDtoCompiler.kt`；后续 `JacksonTypes neutralization` 已继续收敛。
  - `generatedAnnotation overload cleanup` slice（2026-03-12）:
    - `site/addzero/lsi/codegen/JimmerCodegenAnnotationExt.kt` 已删除 `generatedAnnotation(ImmutableType)` 便捷重载，仅保留已有的 `generatedAnnotation()` / `generatedAnnotation(ClassName)` / DTO overload，避免重复抽象。
    - `ProducerGenerator`、`PropsGenerator`、`ImplGenerator`、`FetcherGenerator`、`ImplementorGenerator` 已改为直接调用 `generatedAnnotation(type.className)`，并在替换点补充 `覆盖来源` + `迁移说明` 注释，方便覆盖率检查。
  - `JacksonTypes neutralization` slice（2026-03-12）:
    - `JacksonTypes.kt` 已迁移到 `project/compiler/jimmer-ksp-ext/.../site/addzero/lsi/codegen/JacksonTypes.kt`，`Context.kt` 改为依赖中立 `site.addzero.lsi.codegen.JacksonTypes`。
    - 该类型仅承载 Jackson 相关 `ClassName` 聚合，不再占据旧 `org.babyfish.jimmer.ksp` 顶层命名空间。
  - `DTO compiler neutralization` slice（2026-03-12）:
    - `KspDtoCompiler.kt` 已迁移到 `project/compiler/jimmer-ksp-ext/.../site/addzero/lsi/codegen/LsiDtoCompiler.kt`，类名同步去除 `Ksp` 前缀。
    - `DtoProcessor.findDtoTypeMap` 改为显式注入 `Context.lsiResolver::genericTypeCount`，`LsiDtoCompiler` 不再静态依赖 `Context`。
    - 经过该子批后，`project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp` 顶层残余文件已仅剩 `Context.kt`。
  - `Context neutralization` slice（2026-03-12）:
    - `Context.kt` 已迁移到 `project/compiler/jimmer-ksp-ext/.../site/addzero/context/Context.kt`，保留原对象名，仅将命名空间平移到既有中立 `site.addzero.context` 包。
    - `project/compiler/*ksp*` 模块与 `project/jimmer-ksp/.../JimmerProcessor.kt` 已统一改为 import 新 `site.addzero.context.Context`；旧 `org.babyfish.jimmer.ksp.Context` 引用已清零。
    - 经过该子批后，`project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp` 顶层目录已无文件残留。
  - `immutable codegen helper neutralization` slice（2026-03-12）:
    - `project/compiler/jimmer-ksp-ext/.../immutable/generator/Constants.kt` 与 `utils.kt` 已迁移到 `site/addzero/lsi/codegen/Constants.kt`、`site/addzero/lsi/codegen/utils.kt`。
    - `DtoGenerator`、`InputBuilderGenerator`、`ErrorGenerator`、`TxGenerator`、`TypedTupleGenerator`、`ImmutableType`、`ImmutableProp` 以及 immutable generator 模块内部调用点，均已改为从 `site.addzero.lsi.codegen` 消费这些常量/辅助函数。
    - 经过该子批后，`project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/generator` 目录已无文件残留；旧包命中只剩 immutable 子模块自身的生成器类包名，而非 ext helper 包。
  - `immutable meta namespace neutralization` slice（2026-03-12）:
    - `ImmutableType.kt`、`ImmutableProp.kt`、`ImmutablePropSemantics.kt`、`FormulaDependency.kt` 已整体迁移到 `project/compiler/jimmer-ksp-ext/.../site/addzero/lsi/jimmer/meta/`。
    - `site/addzero/context/Context.kt`、`site/addzero/lsi/codegen/LsiDtoCompiler.kt`、`site/addzero/lsi/codegen/utils.kt` 以及 DTO/immutable/tuple 模块的 import 已同步改为消费 `site.addzero.lsi.jimmer.meta.*`。
    - 经过该子批后，`project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/meta` 目录已无文件残留，`jimmer-ksp-ext` 的旧 `org.babyfish.jimmer.ksp` 命名空间整体已清空。
  - 最新复验（2026-03-12）:
    - `rg -n "import org\\.babyfish\\.jimmer\\.ksp\\.(get|getClassArgument|getClassListArgument|getEnumListArgument|annotation|annotations|fullName)|import org\\.babyfish\\.jimmer\\.ksp\\.\\*" project/compiler -g '*.kt'` 已清零。
    - `rg -n "import org\\.babyfish\\.jimmer\\.ksp\\.(MetaException|GeneratorException)" project/compiler project/jimmer-ksp -g '*.kt'` 已清零。
    - `rg -n "\\btoKs[A-Z]\\w*\\b|\\btoKS[A-Z]\\w*\\b|\\btoAp[A-Z]\\w*\\b|\\btoAPT[A-Z]\\w*\\b" project/compiler lib/lsi -g '*.kt'` 已清零。
    - `rg -n "^import com\\.google\\.devtools\\.ksp" project/compiler -g '*.kt'` 已清零。
    - `rg -n "org\\.babyfish\\.jimmer\\.ksp\\.include" project/compiler -g '*.kt'` 已清零（仅迁移注释保留命中）。
    - `rg -n "org\\.babyfish\\.jimmer\\.ksp\\.util\\.(generatedAnnotation|suppressAllAnnotation|GenericParser|ConverterMetadata|converterMetadataOf)" project/compiler -g '*.kt'` 已清零。
    - `find project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp/util -maxdepth 1 -type f` 结果为空。
    - `find project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp -maxdepth 1 -type f` 结果为空。
    - `find project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/generator -maxdepth 1 -type f` 结果为空。
    - `find project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/meta -maxdepth 1 -type f` 结果为空。
    - `rg -n "^import org\\.babyfish\\.jimmer\\.ksp\\.Context(\\.delayedClientTypeNames)?$|\\borg\\.babyfish\\.jimmer\\.ksp\\.Context\\b" project/compiler project/jimmer-ksp -g '*.kt'` 已清零。
    - `rg -n "import org\\.babyfish\\.jimmer\\.ksp\\.immutable\\.generator\\.(CLIENT_EXCEPTION_CLASS_NAME|JVM_STATIC_CLASS_NAME|PROPAGATION_CLASS_NAME|COLLECTIONS_CLASS_NAME|SELECTION_CLASS_NAME|TUPLE_MAPPER_CLASS_NAME|INPUT_CLASS_NAME|parseValidationMessages|upper)" project/compiler project/jimmer-ksp -g '*.kt'` 已清零。
    - `rg -n "import org\\.babyfish\\.jimmer\\.ksp\\.immutable\\.meta\\.(ImmutableType|ImmutableProp|ImmutablePropSemantics|FormulaDependency)|\\borg\\.babyfish\\.jimmer\\.ksp\\.immutable\\.meta\\.(ImmutableType|ImmutableProp|ImmutablePropSemantics|FormulaDependency)\\b" project/compiler project/jimmer-ksp lib/lsi -g '*.kt'` 已清零。
    - `rg -n "implementation\\(libs\\.kotlinpoet\\.ksp\\)|implementation\\(libs\\.ksp\\.symbolProcessing\\.api\\)" project/compiler -g 'build.gradle.kts'` 已清零。
    - Compile gate passed:
      - `:lib:lsi:lsi-core:compileKotlin`
      - `:lib:lsi:lsi-ksp:compileKotlin`
      - `:project:compiler:jimmer-ksp-ext:compileKotlin`
      - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
      - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
      - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
      - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
      - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
      - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
      - `:project:jimmer-ksp:compileKotlin`

Exit criteria:

- `META-INF/jimmer/client` output is diff-equivalent to baseline on representative projects.

### Phase 6 - Orchestration Unification

Action items:

- [x] Define platform-neutral processor graph contract (`dependsOn`, barrier semantics)（LSI `ProcessorSpi` + KSP 编排器生命周期接入）.
- [ ] Adapt APT staged execution to graph semantics without breaking current round handling.
- [ ] Keep KSP scheduler behavior unchanged while replacing hard-coded platform assumptions.

Exit criteria:

- APT and KSP orchestration semantics are documented with one shared contract and pass regression matrix.

### Phase 7 - Cleanup and Default Flip

Action items:

- [ ] Remove dead duplicated extraction code after parity gates pass.
- [ ] Flip migration flag default to LSI path.
- [ ] Keep one release cycle with fallback flag for rollback.
- [ ] Finalize architecture docs under `project/compiler`.

Exit criteria:

- LSI path is default for both APT and KSP.
- Legacy extraction path removed or isolated with deprecation window.

## Validation Matrix

For each phase, validate:

- [ ] Compile success on APT sample projects.
- [ ] Compile success on KSP sample projects.
- [ ] Generated file diff against baseline.
- [ ] Error diagnostics still point to correct declaration.
- [ ] Performance does not regress beyond agreed threshold.

## Risks and Mitigations

1. Property model mismatch (APT getter vs KSP property)
   - Mitigation: introduce explicit logical-property adapter layer before full port.
2. Type assignability edge cases diverge
   - Mitigation: keep adapter-owned type ops with integration tests for problematic patterns.
3. Annotation use-site target differences
   - Mitigation: normalize annotation lookup contract with explicit precedence rules.
4. Round/barrier semantics drift
   - Mitigation: keep staged rollout and strict baseline diff per phase.
5. Version skew from external LSI artifacts
   - Mitigation: during migration, prefer local module dependency wiring for deterministic behavior.

## Immediate Next Planning Outputs (No Code Refactor Yet)

- [ ] Freeze a baseline corpus and expected generated outputs list.
- [x] Draft/implement `LsiTypeOps` + `LsiResolver` interface proposal (API-first, now code-backed).
- [ ] Draft immutable meta shared-reader design (data model and adapter seam).

## Open Decisions Needed Before Implementation

1. Should migration use local `lib/lsi/*` modules directly during development, instead of published `site.addzero:*` artifacts?
2. Should client processor migration be split into two sub-phases (schema scan first, complex type graph later)?
3. Should APT keep current manual stage flow temporarily, or adopt graph scheduling immediately after Phase 1?

## Supplement (2026-03-07): Same-Name Class Mapping + LSI Symbol Generalization

### A. Same-Name Class Mapping (APT vs KSP, Full List)

Snapshot scope:

- APT: `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt`
- KSP: `project/**/src/main/kotlin/org/babyfish/jimmer/ksp`
- Count: `39` same-name top-level types

| # | Class | APT | KSP |
|---|---|---|---|
| 1 | `AssociatedIdGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/generator/AssociatedIdGenerator.java` | `project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/generator/AssociatedIdGenerator.kt` |
| 2 | `BuilderGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/generator/BuilderGenerator.java` | `project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/generator/BuilderGenerator.kt` |
| 3 | `CaseAppender` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/generator/CaseAppender.java` | `project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/generator/CaseAppender.kt` |
| 4 | `ClientExceptionContext` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ClientExceptionContext.java` | `project/compiler/client/jimmer-ksp-client/src/main/kotlin/org/babyfish/jimmer/ksp/client/ClientExceptionContext.kt` |
| 5 | `ClientExceptionMetadata` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ClientExceptionMetadata.java` | `project/compiler/client/jimmer-ksp-client/src/main/kotlin/org/babyfish/jimmer/ksp/client/ClientExceptionMetadata.kt` |
| 6 | `ClientProcessor` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ClientProcessor.java` | `project/compiler/client/jimmer-ksp-client/src/main/kotlin/org/babyfish/jimmer/ksp/client/ClientProcessor.kt` |
| 7 | `Context` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/Context.java` | `project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp/Context.kt` |
| 8 | `ConverterMetadata` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/util/ConverterMetadata.java` | `project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp/util/ConverterMetadata.kt` |
| 9 | `DocMetadata` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/DocMetadata.java` | `project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp/client/DocMetadata.kt` |
| 10 | `DraftGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/generator/DraftGenerator.java` | `project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/generator/DraftGenerator.kt` |
| 11 | `DraftImplGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/generator/DraftImplGenerator.java` | `project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/generator/DraftImplGenerator.kt` |
| 12 | `DtoContext` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/dto/DtoContext.java` | `project/compiler/dto/jimmer-ksp-dto/src/main/kotlin/org/babyfish/jimmer/ksp/dto/DtoContext.kt` |
| 13 | `DtoException` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/dto/DtoException.java` | `project/compiler/dto/jimmer-ksp-dto/src/main/kotlin/org/babyfish/jimmer/ksp/dto/DtoException.kt` |
| 14 | `DtoGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/dto/DtoGenerator.java` | `project/compiler/dto/jimmer-ksp-dto/src/main/kotlin/org/babyfish/jimmer/ksp/dto/DtoGenerator.kt` |
| 15 | `DtoProcessor` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/dto/DtoProcessor.java` | `project/compiler/dto/jimmer-ksp-dto/src/main/kotlin/org/babyfish/jimmer/ksp/dto/DtoProcessor.kt` |
| 16 | `ErrorGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/error/ErrorGenerator.java` | `project/compiler/error/jimmer-ksp-error/src/main/kotlin/org/babyfish/jimmer/ksp/error/ErrorGenerator.kt` |
| 17 | `ErrorProcessor` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/error/ErrorProcessor.java` | `project/compiler/error/jimmer-ksp-error/src/main/kotlin/org/babyfish/jimmer/ksp/error/ErrorProcessor.kt` |
| 18 | `ExportDocProcessor` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ExportDocProcessor.java` | `project/compiler/client/jimmer-ksp-client/src/main/kotlin/org/babyfish/jimmer/ksp/client/ExportDocProcessor.kt` |
| 19 | `FetcherGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/generator/FetcherGenerator.java` | `project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/generator/FetcherGenerator.kt` |
| 20 | `FormulaDependency` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/meta/FormulaDependency.java` | `project/compiler/jimmer-ksp-ext/src/main/kotlin/site/addzero/lsi/jimmer/meta/FormulaDependency.kt` |
| 21 | `GeneratorException` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/GeneratorException.java` | `project/compiler/jimmer-ksp-ext/src/main/kotlin/site/addzero/lsi/codegen/GeneratorException.kt` |
| 22 | `GenericParser` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/util/GenericParser.java` | `project/compiler/jimmer-ksp-ext/src/main/kotlin/site/addzero/lsi/codegen/GenericParser.kt` |
| 23 | `ImmutableProcessor` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/ImmutableProcessor.java` | `project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/ImmutableProcessor.kt` |
| 24 | `ImmutableProp` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/meta/ImmutableProp.java` | `project/compiler/jimmer-ksp-ext/src/main/kotlin/site/addzero/lsi/jimmer/meta/ImmutableProp.kt` |
| 25 | `ImmutableType` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/meta/ImmutableType.java` | `project/compiler/jimmer-ksp-ext/src/main/kotlin/site/addzero/lsi/jimmer/meta/ImmutableType.kt` |
| 26 | `ImplGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/generator/ImplGenerator.java` | `project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/generator/ImplGenerator.kt` |
| 27 | `ImplementorGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/generator/ImplementorGenerator.java` | `project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/generator/ImplementorGenerator.kt` |
| 28 | `InputBuilderGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/dto/InputBuilderGenerator.java` | `project/compiler/dto/jimmer-ksp-dto/src/main/kotlin/org/babyfish/jimmer/ksp/dto/InputBuilderGenerator.kt` |
| 29 | `JacksonTypes` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/JacksonTypes.java` | `project/compiler/jimmer-ksp-ext/src/main/kotlin/site/addzero/lsi/codegen/JacksonTypes.kt` |
| 30 | `JimmerProcessor` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/JimmerProcessor.java` | `project/jimmer-ksp/src/main/kotlin/org/babyfish/jimmer/ksp/JimmerProcessor.kt` |
| 31 | `MetaException` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/MetaException.java` | `project/compiler/jimmer-ksp-ext/src/main/kotlin/org/babyfish/jimmer/ksp/MetaException.kt` |
| 32 | `ProducerGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/generator/ProducerGenerator.java` | `project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/generator/ProducerGenerator.kt` |
| 33 | `PropsGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/generator/PropsGenerator.java` | `project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/generator/PropsGenerator.kt` |
| 34 | `SerializerGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/dto/SerializerGenerator.java` | `project/compiler/dto/jimmer-ksp-dto/src/main/kotlin/org/babyfish/jimmer/ksp/dto/SerializerGenerator.kt` |
| 35 | `TxGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/transactional/TxGenerator.java` | `project/compiler/transactional/jimmer-ksp-transactional/src/main/kotlin/org/babyfish/jimmer/ksp/transactional/TxGenerator.kt` |
| 36 | `TxProcessor` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/transactional/TxProcessor.java` | `project/compiler/transactional/jimmer-ksp-transactional/src/main/kotlin/org/babyfish/jimmer/ksp/transactional/TxProcessor.kt` |
| 37 | `TypedTupleGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/tuple/TypedTupleGenerator.java` | `project/compiler/tuple/jimmer-ksp-tuple/src/main/kotlin/org/babyfish/jimmer/ksp/tuple/TypedTupleGenerator.kt` |
| 38 | `TypedTupleProcessor` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/tuple/TypedTupleProcessor.java` | `project/compiler/tuple/jimmer-ksp-tuple/src/main/kotlin/org/babyfish/jimmer/ksp/tuple/TypedTupleProcessor.kt` |
| 39 | `ValidationGenerator` | `project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/generator/ValidationGenerator.java` | `project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/generator/ValidationGenerator.kt` |

### B. Unified Symbol Requirements (Generalize to LSI)

| Area | APT symbol today | KSP symbol today | Unified LSI target | Requirement |
|---|---|---|---|---|
| Declaration | `TypeElement` / `ExecutableElement` / `Element` | `KSClassDeclaration` / `KSPropertyDeclaration` / `KSDeclaration` | `LsiClass` / `LsiField` / `LsiMethod` (+ optional `LsiDeclaration`) | Domain rules must consume LSI symbols only |
| Type system | `TypeMirror` / `Types` | `KSType` / resolver assignability | `LsiType` + `LsiTypeOps` | Must support subtype, assignable, collection/list-strict, enum checks |
| Annotation model | `getAnnotation` / `AnnotationMirror` | `annotation(...)` / `KSAnnotation` | `LsiAnnotation` | Unified query by `qualifiedName` + typed argument access |
| Round scan | `RoundEnvironment` + manual traversal | `Resolver` + query APIs | `LsiResolver` | `allClasses/newClasses/findClassesAnnotatedWith/findClassByQualifiedName` (+ filter overloads) |
| File generation | `Filer` | `CodeGenerator` | `LsiFiler` | Same contract for output creation in both engines |
| Diagnostics | `MetaException(Element, ...)` | `MetaException(KSDeclaration, ..., anchor)` | `LsiDiagnosticAnchor` + unified exception contract | All validation errors provide stable symbol anchors |
| Generic parse | `GenericParser(TypeElement, superName)` | `GenericParser(KSClassDeclaration/LsiClass, superName)` | LSI-first generic parser API | Must resolve generic arguments independent of platform |
| Converter metadata | `ConverterMetadata(TypeMirror + TypeName)` | `ConverterMetadata(TypeName)` | LSI-based metadata contract | Keep output parity while removing platform types from domain model |
| Logical property | APT getter-derived (`getX`/`isX`) | Kotlin property native | LSI logical-property adapter | Single naming policy and conflict rules |
| Processor lifecycle | staged calls in `JimmerProcessor` | SPI graph with barriers | platform-neutral orchestration contract | `process` collect / `finish` generate across processors |
| Naming constants | mixed JavaPoet/KotlinPoet names | mixed + drift (`serializerProvider`/`serializeProvider`) | shared naming contract + adapter mapping | Avoid semantic drift in generated outputs |
| Caching key | APT element identity | KSP often string key or declaration identity | stable LSI symbol key | Cache consistency across rounds/engines |

### C. Priority Plan for LSI Generalization

Current signal: among these `39` same-name classes, only about `13` KSP classes already import/consume LSI APIs directly; migration is still in mid-state.

| Wave | Target classes | Goal | Done when |
|---|---|---|---|
| Wave 1 (foundation) | `Context`, `MetaException`, `GenericParser`, `ConverterMetadata`, `JacksonTypes`, `GeneratorException` | Finish symbol/type/diagnostic core contracts | No new domain logic imports `KS*`/`javax.lang.model.*` directly |
| Wave 2 (meta model) | `ImmutableType`, `ImmutableProp`, `FormulaDependency`, `DocMetadata` | Move immutable/doc semantic checks to LSI-first metadata | APT/KSP semantic outputs match baseline snapshots |
| Wave 3 (processor scan) | `JimmerProcessor`, `ImmutableProcessor`, `DtoProcessor`, `ErrorProcessor`, `TxProcessor`, `TypedTupleProcessor`, `ClientProcessor`, `ExportDocProcessor` | Unify scanner + barrier semantics (`process` collect / `finish` generate) | Deferred/barrier behavior is parity-verified on APT and KSP |
| Wave 4 (generator convergence) | All same-name `*Generator` classes in immutable/dto/error/tx/tuple/client | Reuse shared LSI metadata pipeline; keep language-specific emitters thin | Generated files diff-equal to pre-migration baseline |
| Wave 5 (flip + cleanup) | residual adapters and fallbacks | Enable LSI path by default and retire old extraction paths | Fallback path disabled by default and legacy code isolated/deleted |

### D. Execution Notes

- Keep migration rollback-safe: gate each wave by feature switch and baseline diff.
- Prefer local `lib/lsi/*` during migration to avoid artifact skew.
- Keep adapter boundary strict: platform API usage should not leak into shared domain modules.

## Supplement (2026-03-08): Next Continuation Slice

### Immediate follow-up scope

- Port APT `ClientProcessor` extraction path onto the same LSI-first traversal contract used in KSP.
- Move currently KSP-hosted client traversal helpers into a shared compiler-side LSI module (`project/compiler/client/...` shared package), leaving entry processors as thin adapters.
- Add cross-engine baseline diff checks for `META-INF/jimmer/client` and `META-INF/jimmer/doc.properties`.

### Acceptance targets for next slice

- `ClientProcessor` (APT + KSP) shares one LSI traversal algorithm (engine-specific bootstrapping only).
- No new `KS*` or `javax.lang.model.*` symbols appear in shared client extraction utilities.
- Golden diff passes for representative projects on both engines.

## Supplement (2026-03-11): KotlinPoet Bridge Downshift + Compiler Build Boundary Cleanup

### Completed in this slice

- `lsi-core` now owns the compiler-facing neutral KotlinPoet bridges that previously leaked from `lsi-ksp`:
  - `site.addzero.lsi.codegen.toClassName`
  - `site.addzero.lsi.codegen.toLsiClassName`
  - `site.addzero.lsi.clazz.toClassName`
  - `site.addzero.lsi.clazz.toNestedClassName`
  - `site.addzero.lsi.type.toTypeName`
  - `site.addzero.lsi.anno.toAnnotationSpec`
- `project/compiler/client/jimmer-ksp-client/.../DocMetadata.kt` now gets Draft/Impl doc extraction through `Context.findDraftImplDocMap(...)` callback injection; compiler-side code no longer directly imports `site.addzero.lsi.ksp.*`.
- `project/compiler` source scan is now clean for direct platform imports:
  - no `import com.google.devtools.ksp...`
  - no `import site.addzero.lsi.ksp...`
  - remaining `site.addzero.lsi.ksp` text in `project/compiler` is comment-only coverage documentation.
- Compiler build wiring was further shrunk:
  - removed direct `implementation(project(":lib:lsi:lsi-ksp"))` from:
    - `project/compiler/jimmer-ksp-ext/build.gradle.kts`
    - `project/compiler/dto/jimmer-ksp-dto/build.gradle.kts`
    - `project/compiler/immutable/jimmer-ksp-immutable/build.gradle.kts`
  - removed stale `kotlinpoet.ksp` commented dependency from `project/compiler/client/jimmer-ksp-client/build.gradle.kts`
- `lib/lsi/lsi-jimmer` no longer re-exports `lsi-ksp`:
  - `LsiClassJimmerKspExt.kt` switched to `site.addzero.lsi.codegen.toClassName`
  - `lib/lsi/lsi-jimmer/build.gradle.kts` removed `api(project(":lib:lsi:lsi-ksp"))`
- `LsiField` annotation contract was simplified:
  - removed duplicated `allAnnotations`
  - `annotations` is now the single field-annotation entry
  - KSP adapter folds property/getter/returnType annotations directly into `annotations`
  - compiler call sites (`RecursiveAnnotations` / immutable / dto path) were updated to consume `LsiField.annotations`
- `lsi-jimmer` continued boundary cleanup:
  - moved compiler-only `org/babyfish/jimmer/ksp/immutable/generator/Constants.kt` from `lib/lsi/lsi-jimmer` back to `project/compiler/jimmer-ksp-ext`
  - moved compiler-only SPI files from `lib/lsi/lsi-jimmer` back to `project/compiler/jimmer-ksp-ext`
    - `site/addzero/lsi/jimmer/processor/spi/EntityMetaConsumerSpi.kt`
    - `site/addzero/lsi/jimmer/processor/spi/ProcessorConstant.kt`
  - current `lsi-jimmer` source set now only保留 Jimmer 语义扩展文件
- `jimmer-ksp-ext` decoupling continuation:
  - `Context.explicitClientApi` switched from `EnableImplicitApi::class` to annotation FQ constant.
  - `ConverterMetadata` switched from `Converter::class` to interface FQ constant.
  - DTO compiler（现 `LsiDtoCompiler`，原 `KspDtoCompiler`）的 `isGeneratedValue` switched from `GeneratedValue::class` to annotation FQ constant.
  - `ImmutableType` removed direct `Formula/Id/Entity/OneToOne/ManyToOne/MappedSuperclass/Embeddable` class literals; formula/id/association detection now uses LSI annotation FQ constants and string attribute lookup.
  - `ImmutableProp` removed direct `GeneratedValue/Formula/LogicalDeleted/IdView/ManyToManyView/ManyToOne/OneToMany/ManyToMany/OneToOne/JoinSql/ExcludeFromAllScalars/Scalar` class literals from the migrated paths; related attribute reads now use string keys (`value/ref/mappedBy/prop/deeperProp/dependencies`).
  - `immutable/generator/Constants.kt` switched Jimmer runtime/meta/annotation constants from `::class.asClassName()` to `ClassName.bestGuess(...)`, reducing compiler-side hard dependency on jimmer-core Java compilation outputs.

### Validation

- Compile gate passed:
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-ksp:compileKotlin`
  - `:lib:lsi:lsi-apt:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin -x :project:jimmer-core:compileJava`
- Dependency graph verification passed:
  - `:lib:lsi:lsi-jimmer:dependencies --configuration compileClasspath`
  - `:project:compiler:jimmer-ksp-ext:dependencies --configuration compileClasspath`
  - `:project:compiler:dto:jimmer-ksp-dto:dependencies --configuration compileClasspath`
  - `:project:compiler:immutable:jimmer-ksp-immutable:dependencies --configuration compileClasspath`
- Verified result:
  - `lsi-jimmer` compile classpath no longer contains `lsi-ksp`
  - compiler modules above no longer pull `lsi-ksp` directly; current classpath path is `compiler -> lsi-core/lsi-jimmer`

### Additional completion (2026-03-11, later)

- `jimmer-ksp-ext` compile blocker is now closed:
  - `project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.kt` no longer directly imports `org.babyfish.jimmer.impl.util.Keywords`
  - `project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.kt` no longer directly imports `org.babyfish.jimmer.meta.impl.PropDescriptor`
  - `project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.kt` no longer directly imports `org.babyfish.jimmer.meta.impl.Utils`
  - `project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutablePropSemantics.kt` now hosts the current KSP-side minimal Kotlin semantic transplant for:
    - illegal property name guard
    - `IdView` default base-property inference
    - property descriptor/nullability/list/association validation
  - all migrated replacement points in `ImmutableProp.kt` are annotated with `覆盖来源` + `迁移说明` comments for coverage audit
- `project/jimmer-core/src/main/java/org/babyfish/jimmer/client/meta/impl/Schemas.java` missing `DeserializationContext` import was fixed, so `:project:jimmer-core:compileJava` no longer blocks downstream validation.
- `project/compiler/jimmer-ksp-ext/.../GeneratorException.kt` now supports `(message, cause)` so outer client/resource read failures can preserve original exceptions.
- `project/jimmer-ksp/build.gradle.kts` restored the boundary dependency on `:lib:lsi:lsi-ksp`; this matches the intended architecture where:
  - `project/jimmer-ksp` = outer KSP bootstrapping / `KSP -> LSI` glue
  - `project/compiler/**` = LSI-facing compiler logic
- `client/error` annotation semantics were further narrowed to LSI-first reads:
  - `lib/lsi/lsi-jimmer/.../JimmerAnnotations.kt` now adds diff constants for:
    - `DESCRIPTION`
    - `API_IGNORE`
    - `FETCH_BY`
    - `DEFAULT_FETCHER_OWNER`
    - `EXPORT_DOC`
    - `ERROR_FAMILY`
    - `ERROR_FIELD`
    - `ERROR_FIELDS`
    - `TYPED_TUPLE`
  - `project/compiler/client/jimmer-ksp-client/.../DocMetadata.kt`
    - `@Description` lookup now uses LSI annotation FQ + attribute-name access
    - `Context.findDraftImplDocMap(...)` now receives `DESCRIPTION` constant instead of `Description::class`
    - direct compiler-side dependency on `org.babyfish.jimmer.client.Description` has been removed
  - `project/compiler/error/jimmer-ksp-error/.../ErrorProcessor.kt`
    - error-family scanning now uses `LsiClass.annotation(ERROR_FAMILY)`
    - direct compiler-side dependency on `ErrorFamily::class` has been removed from the scan path
  - `project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.kt`
    - family name extraction now uses `LsiAnnotation.get<String>("value")`
    - `@ErrorField` collection now expands pure-LSI repeatable semantics (`ERROR_FIELD` + `ERROR_FIELDS`)
    - field argument reads (`name/type/list/nullable`) now use LSI attribute access instead of `ErrorField::name/type/list/nullable`
    - direct compiler-side dependency on `org.babyfish.jimmer.error.ErrorFamily` / `ErrorField` has been removed from the generator path
- more pure-annotation compiler sites were narrowed in the same way:
  - `project/compiler/client/jimmer-ksp-client/.../ClientProcessor.kt`
    - immutable/source-kind detection now uses `LsiClass.isJimmerType`
    - `@FetchBy` / `@DefaultFetcherOwner` / `@ApiIgnore` paths now use LSI annotation constants + attribute names
    - `@ClientException` paths now also use `CLIENT_EXCEPTION`
    - direct compiler-side dependency on `Immutable/Entity/MappedSuperclass/Embeddable::class`, `FetchBy::class`, `DefaultFetcherOwner::class`, `ApiIgnore::class`, `ClientException::class` has been removed from these semantic branches
  - `project/compiler/client/jimmer-ksp-client/.../LsiClientSchemaTraversal.kt`
    - parameter-level `@ApiIgnore` detection now uses `API_IGNORE`
    - service/method `@Api` groups now use `API`
  - `project/compiler/client/jimmer-ksp-client/.../ExportDocProcessor.kt`
    - `@ExportDoc` detection + generated comment header now use `EXPORT_DOC`
  - `project/compiler/client/jimmer-ksp-client/.../ClientExceptionContext.kt`
    - `@ClientException` lookup, `family/code/subTypes` reads, and subtype validation now use `CLIENT_EXCEPTION` + string attribute names
    - `utils.kt` adds `LsiAnnotation.getClassListArgument(name: String)` to support this without reintroducing concrete annotation class literals
  - `project/compiler/dto/jimmer-ksp-dto/.../DtoProcessor.kt`
    - DTO source-type legality now uses `isJimmerEntity/isJimmerEmbeddable/isJimmerImmutable` + annotation FQ constants in the error message
  - `project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.kt`
    - generated `@ApiIgnore` on state props is now emitted via shared FQ constant rather than `ApiIgnore::class`
  - `project/compiler/tuple/jimmer-ksp-tuple/.../TypedTupleProcessor.kt`
    - `@TypedTuple` detection and validation messages now use `TYPED_TUPLE`
  - `project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.kt`
    - multi-type source-file diagnostic text now uses shared Jimmer annotation FQ constants instead of `Immutable/Entity/MappedSuperclass/Embeddable::class`

### Additional completion (2026-03-12)

- `project/compiler/client/jimmer-ksp-client/.../LsiClientApiRules.kt` no longer imports `org.babyfish.jimmer.client.meta.ApiOperation`:
  - service/operation 判定现在统一复用 `lib/lsi/lsi-jimmer/.../JimmerAnnotations.kt` 中的 shared client/web annotation 常量
  - `RestController` 判定已迁移为 `REST_CONTROLLER`
  - `ApiOperation.AUTO_OPERATION_ANNOTATIONS` 判定已迁移为 `AUTO_API_OPERATION_ANNOTATIONS`
  - 相关替换点已经补充 `覆盖来源` + `迁移说明` 注释，便于按源码使用点核对覆盖率
- `lib/lsi/lsi-jimmer/.../JimmerAnnotations.kt` 本轮补入的差量常量：
  - `REST_CONTROLLER`
  - `REQUEST_MAPPING`
  - `GET_MAPPING`
  - `POST_MAPPING`
  - `PUT_MAPPING`
  - `DELETE_MAPPING`
  - `PATCH_MAPPING`
  - `AUTO_API_OPERATION_ANNOTATIONS`
- 这一步的目的不是引入新抽象，而是把现有 client 自动判定所依赖的注解 FQ 名单下沉为 LSI/Jimmer shared constant，避免 compiler 侧继续反向依赖 `jimmer-core` runtime meta 接口。
- 对 `project/compiler` 做残余扫描后，`ApiOperation` 在 compiler Kotlin 源码中的剩余命中已只剩覆盖注释文本，不再有实际 import/调用。

### Additional completion (2026-03-12, later)

- `lib/lsi/lsi-core/.../doc/LsiDoc.kt` 新增最小 LSI 文档对象：
  - parse/toString 语义对齐 `project/jimmer-core/.../client/meta/Doc.java`
  - 当前仅承载 `value` / `parameterValueMap` / `returnValue` / `propertyValueMap`
  - 没有引入新的文档层级或额外 builder API，只是把 compiler 已经在用的 `Doc.parse` 语义平移到 LSI
- `lib/lsi/lsi-jimmer/.../LsiDocJimmerExt.kt` 新增 `LsiDoc -> org.babyfish.jimmer.client.meta.Doc` 的单向转换：
  - 保持 compiler 内部面向 LSI 文档对象
  - 只有 client schema runtime 落点仍显式转换回 jimmer `Doc`
- `project/compiler/client/jimmer-ksp-client/.../DocMetadata.kt`
  - `getDoc(LsiClass/LsiField/LsiMethod)` 现在返回 `LsiDoc?`
  - 这意味着文档收集层不再直接 import `org.babyfish.jimmer.client.meta.Doc`
- `project/compiler/client/jimmer-ksp-client/.../ClientProcessor.kt`
  - `fetcherDoc`
  - `definition.doc`
  - `prop.doc`
  - `constant.doc`
  - 上述 runtime schema 文档赋值点全部改为 `docMetadata.getDoc(...)? .toJimmerDoc()`
- `project/compiler/client/jimmer-ksp-client/.../LsiClientSchemaTraversal.kt`
  - `service.doc`
  - `operation.doc`
  - 这两个 traversal 落点也都改为边界转换，不再让 traversal 本身依赖 runtime `Doc.parse`
- `project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.kt`
  - `Document.dtoTypeDoc`
  - `Document.baseTypeDoc`
  - `Document.getImpl` 中的 `prop.doc`
  - `Document.getImpl` 中的 `baseProp` 文档
  - 这些解析点全部改为 `LsiDoc.parse(...)`，DTO 生成阶段不再直接依赖 jimmer runtime `Doc`
- 结果：
  - `project/compiler` 主源码里已经没有 `import org.babyfish.jimmer.client.meta.Doc`
  - 主源码中的文档解析已全部切到 `LsiDoc.parse(...)`

### Additional completion (2026-03-12, later 2)

- `lib/lsi/lsi-jimmer/.../JimmerAnnotations.kt` 本轮继续补入的差量常量：
  - `CODE_BASED_EXCEPTION`
  - `CODE_BASED_RUNTIME_EXCEPTION`
  - `DEFAULT_ERROR_FAMILY`
- `project/compiler/client/jimmer-ksp-client/.../ClientExceptionContext.kt`
  - `CodeBasedException` / `CodeBasedRuntimeException` 报错文案
  - 异常父类终止判定
  - 默认错误族 `"DEFAULT"`
  - 上述点都已改为复用 lsi-jimmer 常量，不再直接 import runtime class literal
- `project/compiler/client/jimmer-ksp-client/.../ClientProcessor.kt`
  - `CODE_BASED_EXCEPTION_NAME`
  - `CODE_BASED_RUNTIME_EXCEPTION_NAME`
  - 这两个 `TypeName` 常量已改为通过 FQ 常量推导，不再依赖 `CodeBasedException::class.java`
- `project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.kt`
  - checked / unchecked 异常超类选择现在使用 `ClassName.bestGuess(CODE_BASED_EXCEPTION|CODE_BASED_RUNTIME_EXCEPTION)`
  - error 生成器已不再直接 import `CodeBasedException` / `CodeBasedRuntimeException`
- 结果：
  - `project/compiler` 主源码里已没有 `import org.babyfish.jimmer.error.CodeBasedException`
  - `project/compiler` 主源码里已没有 `import org.babyfish.jimmer.error.CodeBasedRuntimeException`

### Additional completion (2026-03-12, later 3)

- `project/compiler/client/jimmer-ksp-client` 这一轮继续做了“旧 KSP 包名残余收缩”，但没有引入新的语义抽象：
  - 下列已经 LSI-first、且不再直接依赖 `KS*` 的 helper / semantic 文件，已从 `org.babyfish.jimmer.ksp.client` 迁到中性包 `site.addzero.lsi.jimmer.client`
    - `LsiClientApiRules.kt`
    - `LsiClientSchemaTraversal.kt`
    - `DocMetadata.kt`
    - `ClientExceptionContext.kt`
    - `ClientExceptionMetadata.kt`
  - 这一步的目的只是把“处理器入口”和“可复用 LSI 语义”继续剥离，避免已经去 KSP 化的代码还挂在 `org.babyfish.jimmer.ksp.*` 命名空间下
- `project/compiler/client/jimmer-ksp-client/.../ClientProcessor.kt`
  - 已改为显式 import `site.addzero.lsi.jimmer.client.*` 下的上述 helper
  - 保持处理器入口仍留在 `org.babyfish.jimmer.ksp.client`，因为它现在仍承担 KSP 模块装配职责
- `project/compiler/dto/jimmer-ksp-dto/...`
  - `DtoProcessor.kt`
  - `DtoGenerator.kt`
  - 两处 `DocMetadata` 依赖已同步切到 `site.addzero.lsi.jimmer.client.DocMetadata`
- 收敛结果：
  - `project/compiler/client/jimmer-ksp-client/src/main/kotlin/org/babyfish/jimmer/ksp/client`
  - 现在只剩 `ClientProcessor.kt` 与 `ExportDocProcessor.kt`
  - client 模块里已经没有其它纯语义/helper 文件继续挂在旧 `org.babyfish.jimmer.ksp.client` 包下

### Additional completion (2026-03-12, later 4)

- `project/compiler/client/jimmer-ksp-client/.../ExportDocProcessor.kt`
  - 导出文档的扫描/解析/属性写出逻辑已继续从处理器入口中剥离
  - 新增中性 helper：
    - `project/compiler/client/jimmer-ksp-client/src/main/kotlin/site/addzero/lsi/jimmer/client/LsiExportDocSupport.kt`
  - 其中承载了：
    - `collectExportDocTypeNames(...)`
    - `resolveExportDocDeclarations(...)`
    - `writeExportDoc(...)`
- `ExportDocProcessor` 现在只保留 `onRound/onFinish` orchestration 和 `lsiFiler.createResourceFile(...)` 的落盘职责
- 这一步没有引入新的 domain abstraction，只是把已经完全不依赖 KSP 的 ExportDoc 语义流程从旧 `org.babyfish.jimmer.ksp.client` 包里继续向 `site.addzero.lsi.jimmer.client` 收拢
- 后续补齐（2026-03-15, current thread）:
  - `ExportDoc` 的 package-level `@ExportDoc` 已经不再保持空实现
  - 统一通过 `LsiClass.packageAnnotations` 收敛：
    - APT 映射 `PackageElement/package-info.java`
    - KSP 映射 Kotlin `@file:` package-level annotation
  - `LsiExportDocSupport.exportDocPkg(...)` 现已真正消费 LSI 包级注解语义，不再区分平台载体

### Additional completion (2026-03-12, later 5)

- `project/compiler/client/jimmer-ksp-client` 又做了一轮“重复 helper 收敛”，避免继续保留同义实现：
  - 新增中性 helper：
    - `project/compiler/client/jimmer-ksp-client/src/main/kotlin/site/addzero/lsi/jimmer/client/LsiClientTypeSupport.kt`
  - 收敛内容：
    - `LsiClass.toClientTypeName()`
    - `LsiType.toClientTypeName()`
    - `declaredClientFields(...)`
    - `declaredClientMethods(...)`
    - `sameClientTypeName(...)`
    - `LsiClass.clientFullName()`
- 对应替换点：
  - `project/compiler/client/jimmer-ksp-client/.../LsiClientSchemaTraversal.kt`
    - 移除了本地重复的 `toClientTypeName` / declared-method 逻辑，改为复用共享 helper
  - `project/compiler/client/jimmer-ksp-client/.../ClientProcessor.kt`
    - 移除了本地重复的 `toClientTypeName` / `sameTypeName` / `fullName` / declared-member 逻辑，改为复用共享 helper
- 这一步的目的不是再抽象一层，而是直接消掉同一模块内已经出现的重复 client 命名/声明归属规则，避免后续 APT 对齐时继续复制同样一套逻辑

### Additional completion (2026-03-12, later 6)

- `project/compiler/client/jimmer-ksp-client/.../ClientProcessor.kt` 本轮继续向“只保留 orchestration”收口：
  - 入口类里原先残留的三块 runtime materialization / semantic 逻辑已进一步挪出：
    - 异常类型名提取
    - `fillType` 及其 `nullability/@FetchBy/type-name-arguments/@JsonValue` 子流程
    - `fillDefinition` / `fillEnumDefinition`
  - 当前 `ClientProcessor` 只剩：
    - `onRound` 服务候选收集
    - `onFinish` builder / traversal input / resource 输出装配
    - `existingSchema()` 旧资源回读
    - `createBuilder(...)` 这层 `SchemaBuilder` override 胶水
    - `KspClientSchemaTraversalHooks` 平台钩子占位
- 新增中性 helper：
  - `project/compiler/client/jimmer-ksp-client/src/main/kotlin/site/addzero/lsi/jimmer/client/LsiClientExceptionTypeSupport.kt`
    - `resolveClientExceptionTypeNames(...)`
  - `project/compiler/client/jimmer-ksp-client/src/main/kotlin/site/addzero/lsi/jimmer/client/LsiClientSchemaMaterialization.kt`
    - `LsiClientSchemaMaterializationInput`
    - `fillClientType(...)`
    - `fillClientDefinition(...)`
  - `project/compiler/client/jimmer-ksp-client/src/main/kotlin/site/addzero/lsi/jimmer/client/LsiClientTypeSupport.kt`
    - 本轮补入 `asClientLsiType(...)`
    - 本轮补入 `simpleClientLsiType(...)`
- 这一步的实现约束保持不变：
  - 没有引入新的中立 schema IR
  - 仍直接构造 `jimmer-client` runtime schema 对象
  - converter metadata 读取继续由入口类通过显式 lambda 注入，helper 本身不直接依赖 `Context.typeOf`
  - 没有新增任何 `LSI -> KSP` / `LSI -> APT` 方向回桥

### Additional completion (2026-03-12, later 7)

- 按“第三优先级：先收旧 `org.babyfish.jimmer.ksp.*` 命名空间下的纯 helper/validator/support”继续做了两批平移：
  - transactional:
    - `project/compiler/transactional/jimmer-ksp-transactional/.../TxLsiValidator.kt`
    - 已迁到 `site.addzero.lsi.jimmer.transactional.TxLsiValidator`
    - `TxProcessor.kt` 已改为显式 import 新 validator
  - dto:
    - `DtoContext.kt`
    - `DtoInterfaces.kt`
    - `DtoException.kt`
    - 已迁到 `site.addzero.lsi.jimmer.dto`
    - `DtoProcessor.kt` / `DtoGenerator.kt` 已改为显式 import 新 support
- 这一步没有改动 DTO / Tx processor/generator 的外部类名，也没有改动处理器调度语义；只是继续把不依赖 `KS*` 的 support 语义从旧 KSP 包里剥离出来

### Additional completion (2026-03-12, later 8)

- immutable 侧继续收了两处纯 helper：
  - `project/compiler/immutable/jimmer-ksp-immutable/.../generator/CaseAppender.kt`
  - `project/compiler/immutable/jimmer-ksp-immutable/.../generator/Validations.kt`
  - 两者已迁到 `site.addzero.lsi.jimmer.immutable.generator`
- 已更新引用点：
  - `ImplementorGenerator.kt`
  - `ImplGenerator.kt`
  - `DraftImplGenerator.kt`
  - `ValidationGenerator.kt`
- 这一步仍然只处理 helper 平移，不触碰 immutable generator 主生成流程，也不改变生成输出语义

### Additional completion (2026-03-12, later 9)

- 继续按“旧 `org.babyfish.jimmer.ksp.*` 命名空间下的纯 helper/validator/support 优先平移”执行，这一轮补完了 dto / immutable / error / tx / tuple 侧剩余的一批纯生成 helper：
  - dto:
    - `DtoGenerator.kt`
    - `InputBuilderGenerator.kt`
    - `SerializerGenerator.kt`
    - 已统一切到 `site.addzero.lsi.jimmer.dto`
    - `DtoProcessor.kt` 已改为显式 import 新 `DtoGenerator`
  - immutable:
    - `AssociatedIdGenerator.kt`
    - `BuilderGenerator.kt`
    - `DraftGenerator.kt`
    - `DraftImplGenerator.kt`
    - `FetcherDslGenerator.kt`
    - `FetcherGenerator.kt`
    - `ImplGenerator.kt`
    - `ImplementorGenerator.kt`
    - `JimmerModuleGenerator.kt`
    - `ProducerGenerator.kt`
    - `PropsGenerator.kt`
    - `ValidationGenerator.kt`
    - 已统一切到 `site.addzero.lsi.jimmer.immutable.generator`
    - `ImmutableProcessor.kt` 已改为显式 import 新 generator 入口
  - error / transactional / tuple:
    - `ErrorGenerator.kt` -> `site.addzero.lsi.jimmer.error`
    - `TxGenerator.kt` -> `site.addzero.lsi.jimmer.transactional`
    - `TypedTupleGenerator.kt` -> `site.addzero.lsi.jimmer.tuple`
    - 三个 processor 入口都已改为显式 import 新 generator
- 这一轮仍然没有改动处理器入口 FQCN / SPI `id` / `dependsOn` 语义；只是把纯 LSI 生成 helper 从旧 KSP 包名下收走

### Residual old-package compiler files after this batch

- 基于源码扫描，`project/compiler/**` 下当前仍挂在 `org.babyfish.jimmer.ksp.*` 包下的 Kotlin 文件已经只剩处理器入口：
  - `project/compiler/client/jimmer-ksp-client/.../ClientProcessor.kt`
  - `project/compiler/client/jimmer-ksp-client/.../ExportDocProcessor.kt`
  - `project/compiler/dto/jimmer-ksp-dto/.../DtoProcessor.kt`
  - `project/compiler/error/jimmer-ksp-error/.../ErrorProcessor.kt`
  - `project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.kt`
  - `project/compiler/transactional/jimmer-ksp-transactional/.../TxProcessor.kt`
  - `project/compiler/tuple/jimmer-ksp-tuple/.../TypedTupleProcessor.kt`
- 含义：
  - 旧 KSP 命名空间在 compiler 目录里现在基本只承担“处理器入口保持外部兼容”这一层职责
  - 下一步如果继续收敛，应优先处理 entry processor 内剩余的 orchestration / materialization 边界，而不是再做 helper 平移

### Error metadata-first template progress (2026-03-12)

- Completed the first `XxxMetadata`-first template on `error`:
  - added `project/compiler/error/error-metadata-model`
  - added `project/compiler/error/error-metadata-extractor`
  - added `project/compiler/error/error-metadata-generator`
- Added compiler-side minimal artifact contract:
  - `project/compiler/jimmer-ksp-ext/.../GeneratedArtifact.kt`
  - includes `GeneratedSourceArtifact` and `GeneratedResourceArtifact`
  - generator side now returns pure artifacts instead of writing via `Context` / `LsiFiler`
- `error-metadata-model` now holds pure domain values only:
  - `ErrorTypeMetadata`
  - `ErrorItemMetadata`
  - `ErrorFieldMetadata`
  - no `LsiClass` / `LsiType` / `KS*` / `TypeElement` / `SchemaBuilder`
- `error-metadata-extractor` now centralizes:
  - error-family enum scan
  - family / exception-name derivation
  - `@ErrorField` / `@ErrorFields` parsing
  - shared-field + item-field merge / duplicate / reserved-name validation
  - doc extraction
  - metadata-id -> `LsiDiagnosticAnchor` source index
- `error-metadata-generator` now consumes only `ErrorTypeMetadata` + `checkedException` and returns source artifact.
- `jimmer-ksp-error/.../ErrorProcessor.kt` is now orchestration-only:
  - `onRound` collects `ErrorTypeMetadata`
  - `onFinish` generates artifacts
  - processor entry owns the final `ctx.lsiFiler.createSourceFile(...)` write
- Runtime-dependency cleanup completed for this slice:
  - `error-metadata-extractor` and `error-metadata-generator` no longer depend on `jimmer-core` just for `StringUtil`
  - `jimmer-ksp-ext/.../Constants.kt` switched Jackson `JSON_*_CLASS_NAME` constants from real-class loading to `ClassName.bestGuess(...)`, avoiding compiler runtime hard dependency on Jackson classes
- Tests added and passing for the new template:
  - `error-metadata-extractor` unit test covers enum scan / metadata extraction / duplicate-field validation
  - `error-metadata-generator` snapshot test guards generated source text
- Static guard rechecked for `project/compiler` after this slice:
  - no direct `import com.google.devtools.ksp.*`
  - no direct `import site.addzero.lsi.ksp.*`
  - no `toKs*` / `toAp*` usages

### Transactional metadata-first template progress (2026-03-12)

- Completed the second `XxxMetadata`-first template on `transactional`:
  - added `project/compiler/transactional/tx-metadata-model`
  - added `project/compiler/transactional/tx-metadata-extractor`
  - added `project/compiler/transactional/tx-metadata-generator`
- `tx-metadata-model` now holds pure domain values only:
  - `TxTypeMetadata`
  - `TxConstructorMetadata`
  - `TxMethodMetadata`
  - `TxParameterMetadata`
  - `TxTypeRefMetadata`
  - `TxAnnotationMetadata` + value model
  - no `LsiClass` / `LsiField` / `LsiMethod` / `LsiType`
- `tx-metadata-extractor` now centralizes:
  - `@Tx` class/method/constructor/field scan
  - type legality validation
  - sqlClient property resolution
  - target annotation extraction
  - constructor / method generation-input materialization
  - annotation / type recursive metadata flattening
  - metadata-id -> `LsiDiagnosticAnchor` source index
- `tx-metadata-generator` now consumes only `TxTypeMetadata` and returns source artifact.
- `jimmer-ksp-transactional/.../TxProcessor.kt` is now orchestration-only:
  - `onRound` collects `TxTypeMetadata`
  - `onFinish` generates artifacts
  - processor entry owns the final `ctx.lsiFiler.createSourceFile(...)` write
- Old transactional KSP helper objects removed from entry module:
  - `TxGenerator.kt`
  - `TxLsiValidator.kt`
  - their behavior is now folded into extractor/generator layers
- Tests added and passing for the new template:
  - `tx-metadata-extractor` unit test covers service scan / metadata extraction / sqlClient validation
  - `tx-metadata-generator` snapshot test guards generated source text
- Static guard remains clean for `project/compiler` after this slice:
  - no direct `import com.google.devtools.ksp.*`
  - no direct `import site.addzero.lsi.ksp.*`
  - no `toKs*` / `toAp*` usages

### Validation (closed KSP compile chain)

- Compile gate passed:
  - `:project:jimmer-core:compileJava`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
- Additional compile gate passed (2026-03-12, later):
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
- Additional compile gate passed (2026-03-12, later 2):
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
- Additional compile gate passed (2026-03-12, later 3):
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
- Additional compile gate passed (2026-03-12, later 4 / 5):
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
- Additional compile gate passed (2026-03-12, later 6):
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
- Additional compile gate passed (2026-03-12, later 7 / 8):
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
- Additional compile gate passed (2026-03-12, later 9):
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
- Additional static guard passed (2026-03-12, later 9):
  - `rg -n "^package org\\.babyfish\\.jimmer\\.ksp" project/compiler -g '*.kt'`
    - 命中已收敛到仅剩处理器入口 7 个文件
  - `rg -n "toKs[A-Za-z0-9_]*\\(|toAp[A-Za-z0-9_]*\\(" project/compiler -g '*.kt'`
    - 已清零
- Error metadata-first validation passed (2026-03-12):
  - `:project:compiler:error:error-metadata-model:compileKotlin`
  - `:project:compiler:error:error-metadata-extractor:compileKotlin`
  - `:project:compiler:error:error-metadata-generator:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:error:error-metadata-extractor:compileTestKotlin`
  - `:project:compiler:error:error-metadata-generator:compileTestKotlin`
  - `-x :project:jimmer-dto-compiler:jar -x :project:jimmer-core:jar :project:compiler:error:error-metadata-extractor:test :project:compiler:error:error-metadata-generator:test`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
- Transactional metadata-first validation passed (2026-03-12):
  - `:project:compiler:transactional:tx-metadata-model:compileKotlin`
  - `:project:compiler:transactional:tx-metadata-extractor:compileKotlin`
  - `:project:compiler:transactional:tx-metadata-generator:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:transactional:tx-metadata-extractor:compileTestKotlin`
  - `:project:compiler:transactional:tx-metadata-generator:compileTestKotlin`
  - `-x :project:jimmer-dto-compiler:jar -x :project:jimmer-core:jar :project:compiler:transactional:tx-metadata-extractor:test :project:compiler:transactional:tx-metadata-generator:test`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
- Tuple metadata-first validation passed (2026-03-12):
  - completed the third `XxxMetadata`-first template on `tuple`:
    - added `project/compiler/tuple/tuple-metadata-model`
    - added `project/compiler/tuple/tuple-metadata-extractor`
    - added `project/compiler/tuple/tuple-metadata-generator`
  - `tuple-metadata-model` now holds pure domain values only:
    - `TypedTupleMetadata`
    - `TypedTuplePropertyMetadata`
    - `TypedTupleTypeRefMetadata`
    - no `LsiClass` / `LsiField` / `LsiType` / `Context` / `LsiFiler`
  - `tuple-metadata-extractor` now centralizes:
    - `@TypedTuple` candidate scan
    - data-class / top-level / super-class legality checks
    - generated mapper naming
    - tuple property filtering / empty-property validation
    - `sourceIndex` anchor collection
  - `tuple-metadata-generator` now consumes only `TypedTupleMetadata` and returns source artifact
  - `jimmer-ksp-tuple/.../TypedTupleProcessor.kt` is now orchestration-only:
    - `onRound` collects metadata
    - `onFinish` generates artifacts and delegates file writing back to `ctx.lsiFiler`
  - old tuple KSP direct generator removed from entry module:
    - `project/compiler/tuple/jimmer-ksp-tuple/.../TypedTupleGenerator.kt`
  - tuple metadata tests passed:
    - `tuple-metadata-extractor` unit test covers metadata extraction / invalid non-data-class rejection
    - `tuple-metadata-generator` snapshot test guards generated source text
  - `:project:compiler:tuple:tuple-metadata-model:compileKotlin`
  - `:project:compiler:tuple:tuple-metadata-extractor:compileKotlin`
  - `:project:compiler:tuple:tuple-metadata-generator:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `-x :project:jimmer-dto-compiler:jar -x :project:jimmer-core:jar :project:compiler:tuple:tuple-metadata-extractor:test :project:compiler:tuple:tuple-metadata-generator:test`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Client metadata-first validation passed (2026-03-13):
  - completed the fourth `XxxMetadata`-first template on `client`:
    - added `project/compiler/client/client-metadata-model`
    - added `project/compiler/client/client-metadata-extractor`
    - added `project/compiler/client/client-metadata-generator`
  - `client-metadata-model` now holds pure domain values only:
    - `ClientSchemaMetadata`
    - `ClientServiceMetadata`
    - `ClientOperationMetadata`
    - `ClientParameterMetadata`
    - `ClientTypeDefinitionMetadata`
    - `ClientPropertyMetadata`
    - `ClientTypeRefMetadata`
    - no `LsiClass` / `LsiType` / `SchemaBuilder` / `Schema` / `Context` / `LsiFiler`
  - `client-metadata-extractor` now centralizes:
    - existing schema resource read-back
    - service/operation/type-definition traversal result extraction
    - runtime `Schema` -> pure `ClientSchemaMetadata` projection
    - shared client helper relocation:
      - `ClientExceptionContext`
      - `ClientExceptionMetadata`
      - `DocMetadata`
      - `LsiClientApiRules`
      - `LsiClientExceptionTypeSupport`
      - `LsiClientSchemaMaterialization`
      - `LsiClientSchemaTraversal`
      - `LsiClientTypeSupport`
  - `client-metadata-generator` now consumes only `ClientSchemaMetadata` and returns resource artifact:
    - output remains `META-INF/jimmer/client`
    - runtime schema serialization is rebuilt from metadata inside generator, not processor entry
  - `jimmer-ksp-client/.../ClientProcessor.kt` is now orchestration-only:
    - `onRound` collects service type names
    - `onFinish` extracts metadata, generates resource artifact, and delegates final write to `ctx.lsiFiler`
    - existing schema file read-back and converter type-name lookup remain explicit injected callbacks
  - `jimmer-ksp-client` dependency cleanup for this slice:
    - removed direct `kotlinpoet-ksp` dependency from processor entry module
    - kept base `kotlinpoet` only because current `converterMetadata.targetTypeName` contract still exposes that type
  - client metadata tests passed:
    - `client-metadata-extractor` unit test covers runtime `Schema` -> metadata projection
    - `client-metadata-generator` roundtrip test covers metadata -> resource -> runtime `Schema`
    - test runtime adds module-local fallback to `jimmer-core` / `jimmer-dto-compiler` compiled class directories so the slice can be verified while the repository still has the existing `:project:jimmer-core:jar` duplicate-entry issue
  - `:project:compiler:client:client-metadata-model:compileKotlin`
  - `:project:compiler:client:client-metadata-extractor:compileKotlin`
  - `:project:compiler:client:client-metadata-generator:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `-x :project:jimmer-dto-compiler:jar -x :project:jimmer-core:jar :project:compiler:client:client-metadata-extractor:test :project:compiler:client:client-metadata-generator:test`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable top-level generator artifact slice passed (2026-03-13):
  - `DraftGenerator`
  - `PropsGenerator`
  - `FetcherGenerator`
  - `JimmerModuleGenerator`
  - these top-level immutable generators now return `GeneratedArtifact` instead of writing through `ctx.lsiFiler` directly
  - `ImmutableProcessor` now owns the final source/resource write-back:
    - source artifacts -> `ctx.lsiFiler.createSourceFile(...)`
    - resource artifacts -> `ctx.lsiFiler.createResourceFile(...)`
  - scope/intent of this slice:
    - keep current `ImmutableType`-based generator internals unchanged
    - further compress processor/generator boundary toward orchestration-only processor + pure artifact-producing generator
    - do not introduce new immutable IR yet
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable generator dependency-slimming slice passed (2026-03-13):
  - `AssociatedIdGenerator` no longer holds full `Context`; it now consumes only `JacksonTypes`
  - `ImplementorGenerator` no longer holds full `Context`; it now consumes only `JacksonTypes`
  - `ImplGenerator` no longer holds full `Context`; it now consumes only `JacksonTypes`
  - `PropsGenerator` no longer accepts `Context` at all
  - `FetcherGenerator` no longer accepts `Context` at all
  - `DraftGenerator` now forwards only:
    - full `Context` to `DraftImplGenerator`
    - `JacksonTypes` to `ProducerGenerator` / `AssociatedIdGenerator`
  - `ProducerGenerator` now forwards only:
    - `JacksonTypes` to `ImplementorGenerator` / `ImplGenerator`
    - full `Context` to `DraftImplGenerator`
  - intent of this slice:
    - keep immutable generator output stable
    - shrink generator dependency surfaces from `Context` to explicit codegen metadata where possible
    - keep `JimmerModuleGenerator` resource-merge probing on `Context` temporarily until it can be lowered to ordinary file input
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable generator full de-contextualization slice passed (2026-03-13):
  - `DraftImplGenerator` no longer holds full `Context`; it now consumes only `JacksonTypes`
  - `ProducerGenerator` no longer forwards `Context` at all
  - `DraftGenerator` no longer holds or forwards `Context`
  - `JimmerModuleGenerator` no longer holds `Context`; old `META-INF/jimmer/entities` merge probe is precomputed by `ImmutableProcessor` and injected as ordinary `File?`
  - result of this slice:
    - immutable generator layer no longer directly depends on `site.addzero.context.Context`
    - `Context` now remains only in processor/orchestration and platform-glue call sites, not in immutable source/resource generators
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable fetcher metadata-input slice passed (2026-03-13):
  - added module-local fetcher metadata projection:
    - `ImmutableFetcherTypeMetadata`
    - `ImmutableFetcherPropMetadata`
    - `ImmutableFetcherFieldKind`
    - `ImmutableType.toFetcherTypeMetadata()`
  - `FetcherGenerator` now consumes only fetcher metadata, no longer imports `ImmutableType`
  - `FetcherDslGenerator` now consumes only fetcher metadata, no longer imports `ImmutableType` / `ImmutableProp`
  - semantic decisions moved out of generator layout code and into metadata projection:
    - cross-package `by` import collection
    - id-only fetch eligibility
    - reference fetch eligibility
    - recursive fetch eligibility
    - configurable child-fetch eligibility
    - field kind (`simple` / `reference` / `list`)
  - scope/intent of this slice:
    - validate immutable “generator input de-big-object” path with a smaller, self-contained generator chain first
    - keep metadata local to `jimmer-ksp-immutable` for now, avoiding premature `immutable-metadata-*` module expansion
    - preserve generated output semantics while removing direct generator reads of `ImmutableType/ImmutableProp`
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable props metadata-input slice passed (2026-03-13):
  - added module-local props metadata projection:
    - `ImmutablePropsTypeMetadata`
    - `ImmutablePropsPropMetadata`
    - `ImmutablePropsIdMetadata`
    - `ImmutablePropsTypeRefMetadata`
    - `ImmutableType.toPropsTypeMetadata()`
  - `PropsGenerator` now consumes only props metadata, no longer imports `ImmutableType` / `ImmutableProp`
  - semantic decisions moved out of props layout code and into metadata projection:
    - `isDsl(table/tableEx)` exposure
    - generated association id-view property naming (`getIdPropName`)
    - typed-prop category (`referenceList` / `reference` / `scalarList` / `scalar`)
    - association target id property shape
    - property type / target type / target-id type structure projection
  - local type projection uses `ImmutablePropsTypeRefMetadata` + generator-local `toTypeName(...)` bridge instead of keeping direct `ImmutableProp.typeName/targetTypeName` reads inside generator
  - scope/intent of this slice:
    - extend the same local metadata-input pattern from `fetcher` to the heaviest immutable source generator entry
    - keep metadata local to `jimmer-ksp-immutable` for now, avoiding premature `immutable-metadata-*` module expansion
    - preserve generated output semantics while removing another generator’s direct `ImmutableType/ImmutableProp` dependency
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable builder metadata-input slice passed (2026-03-13):
  - added module-local builder metadata projection:
    - `ImmutableBuilderTypeMetadata`
    - `ImmutableBuilderSetterMetadata`
    - `ImmutableType.toBuilderTypeMetadata()`
  - `BuilderGenerator` now consumes only builder metadata, no longer imports `ImmutableType` / `ImmutableProp`
  - extracted reusable method-annotation projection helper:
    - `ImmutableProp.nonJimmerMethodAnnotations(...)`
    - existing `copyNonJimmerMethodAnnotations(...)` now reuses that helper instead of owning the filtering loop itself
  - semantic decisions moved out of builder layout code and into metadata projection:
    - DraftImpl backing type / producer owner type naming
    - visibility-control slot collection (`isVisibilityControllable`)
    - setter parameter type / return type projection
    - non-Jimmer method annotation copy set
  - scope/intent of this slice:
    - continue shrinking the Draft-side generator chain with a smaller local seam before touching `Producer` -> `Implementor/Impl/DraftImpl`
    - keep metadata local to `jimmer-ksp-immutable` for now, avoiding premature `immutable-metadata-*` module expansion
    - preserve generated output semantics while removing another generator’s direct `ImmutableType/ImmutableProp` dependency
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable associated-id metadata-input slice passed (2026-03-13):
  - added module-local associated-id metadata projection:
    - `ImmutableAssociatedIdMetadata`
    - `ImmutableProp.toAssociatedIdMetadata()`
  - `AssociatedIdGenerator` now consumes only associated-id metadata, no longer imports `ImmutableProp`
  - `DraftGenerator` and `DraftImplGenerator` now project associated-id metadata at the call site instead of passing the whole property object into the helper generator
  - semantic decisions moved out of associated-id layout code and into metadata projection:
    - generated associated-id property naming
    - “should generate or skip” gating (`association` / `list` / `idViewProp` / duplicate name)
    - target id property type / getter / setter writeback shape
  - scope/intent of this slice:
    - continue shrinking Draft-side helper generators with low-risk local seams
    - keep metadata local to `jimmer-ksp-immutable` for now, avoiding premature `immutable-metadata-*` module expansion
    - preserve generated output semantics while removing another helper’s direct `ImmutableProp` dependency
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable implementor metadata-input slice passed (2026-03-13):
  - added module-local implementor metadata projection:
    - `ImmutableImplementorTypeMetadata`
    - `ImmutableImplementorPropCaseMetadata`
    - `ImmutableImplementorDeepPropIdMetadata`
    - `ImmutableType.toImplementorTypeMetadata()`
  - `ImplementorGenerator` now consumes only implementor metadata, no longer imports `ImmutableType` / `ImmutableProp`
  - `ProducerGenerator` now projects implementor metadata at the call site instead of passing the whole immutable type object into the helper generator
  - `addElseForNonExistingProp(...)` gained a plain `typeDescription: String` overload so metadata-first generators can reuse the same error text without depending on `ImmutableType`
  - `deeperPropIdPropName(...)` was lowered from `ImplementorGenerator` companion scope into immutable-local metadata/helper scope so `ImplGenerator` no longer depends on the Implementor generator class itself
  - semantic decisions moved out of implementor layout code and into metadata projection:
    - property order annotation payload
    - `__get` dispatch cases for `PropId` / `String`
    - producer type reference for `__type`
    - deep-prop companion constants for many-to-many-view paths
  - scope/intent of this slice:
    - continue shrinking the Producer-side helper generators before touching the heavier `Producer`/`Impl`/`DraftImpl` primary layout flows
    - keep metadata local to `jimmer-ksp-immutable` for now, avoiding premature `immutable-metadata-*` module expansion
    - preserve generated output semantics while removing another generator’s direct `ImmutableType/ImmutableProp` dependency
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable validation metadata-input slice passed (2026-03-13):
  - added module-local validation metadata projection:
    - `ImmutableValidationPropMetadata`
    - `ImmutableProp.toValidationPropMetadata()`
  - `ValidationGenerator` now consumes validation metadata, no longer imports `ImmutableProp`
  - `DraftImplGenerator.addProp` now projects validation metadata at the call site instead of passing the whole property object into `ValidationGenerator`
  - `DraftImplGenerator.addCompanionObject` now also reuses validation metadata for:
    - email pattern constants
    - regexp pattern constants
    - property-scoped validator field declarations
  - `regexpPatternFieldName(...)` and `validatorFieldName(...)` gained `String`-based overloads so metadata-first validation code can reuse the same naming rules without depending on `ImmutableProp`
  - semantic decisions moved out of validation layout code and into metadata projection:
    - validation annotation multi-map capture
    - property validator message map
    - property type shape (`nullable` / `nonNullType`)
    - property slot/name/error-anchor packaging
  - scope/intent of this slice:
    - keep shrinking the DraftImpl-side helper chain before touching the heavier draft state machine layout itself
    - keep metadata local to `jimmer-ksp-immutable` for now, avoiding premature `immutable-metadata-*` module expansion
    - preserve generated output semantics while removing another generator/helper path’s direct `ImmutableProp` dependency
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable dispatch-helper cleanup slice passed (2026-03-13):
  - `CaseAppender` is now a pure slot/name formatting helper:
    - no longer imports `ImmutableType`
    - no longer imports `ImmutableProp`
    - now accepts only `slotName` / `propName` plain values
  - call sites updated in:
    - `ImplGenerator.addIsLoadedFun`
    - `ImplGenerator.addIsVisibleFun`
    - `DraftImplGenerator.addUnloadFun`
    - `DraftImplGenerator.addSetFun`
    - `DraftImplGenerator.addShowFun`
  - `DraftImplGenerator.addShowFun` now reuses `addElseForNonExistingProp(type.toString(), argType)` instead of keeping a duplicated inline illegal-property error template
  - scope/intent of this slice:
    - continue shrinking helper-level semantic coupling before touching the heavier `Impl` / `DraftImpl` state-machine layout bodies
    - keep this as a low-risk local cleanup with zero new abstract layers
    - preserve generated output semantics while removing another helper’s direct `ImmutableType/ImmutableProp` dependency
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - `:lib:lsi:lsi-core:compileKotlin`
  - `:lib:lsi:lsi-jimmer:compileKotlin`
  - `:project:compiler:jimmer-ksp-ext:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable producer metadata-input slice passed (2026-03-13):
  - added module-local producer metadata projection:
    - `ImmutableProducerTypeMetadata`
    - `ImmutableProducerRedefinedPropMetadata`
    - `ImmutableProducerPropMetadata`
    - `ImmutableProducerSlotMetadata`
    - `ImmutableType.toProducerTypeMetadata()`
  - `ProducerGenerator` now consumes producer metadata for its own layout:
    - immutable type builder initialization
    - super-type producer references
    - redefined prop wiring
    - prop category / sql annotation dispatch
    - slot constant initialization
    - `produce(...)` signature typing
  - `DraftGenerator` now projects producer metadata at the call site instead of letting `ProducerGenerator` directly read `ImmutableType/ImmutableProp` for its own output
  - kept the change intentionally narrow:
    - `ProducerGenerator` still receives the original `ImmutableType` only as a pass-through for downstream `ImplementorGenerator` / `ImplGenerator` / `DraftImplGenerator`, because those larger generators are not fully metadata-first yet
    - no new shared immutable framework/module was introduced; metadata remains local to `jimmer-ksp-immutable`
  - scope/intent of this slice:
    - continue shrinking the primary `Producer` layout before touching the heavier `Impl` / `DraftImpl` state-machine bodies
    - preserve generated output semantics while moving another generator body off direct `ImmutableType/ImmutableProp` semantic reads
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable impl header-field-init metadata-input slice passed (2026-03-13):
  - added module-local impl metadata projection:
    - `ImmutableImplTypeMetadata`
    - `ImmutableImplFieldMetadata`
    - `ImmutableType.toImplTypeMetadata()`
  - `ImplGenerator` now consumes impl metadata for:
    - generated annotation class name
    - implementor superinterface reference
    - backing field declarations
    - field default value materialization
    - constructor-time hidden-slot visibility initialization
  - `ProducerGenerator` now projects impl metadata at the call site instead of letting `ImplGenerator` directly read `ImmutableType/ImmutableProp` for these layout decisions
  - kept the change intentionally narrow:
    - `ImplGenerator` still receives the original `ImmutableType` only for the remaining getter/load/hash/equals state-machine logic
    - no new shared immutable framework/module was introduced; metadata remains local to `jimmer-ksp-immutable`
  - scope/intent of this slice:
    - continue shrinking the primary `Impl` layout before touching the heavier property getter and comparison/state-machine branches
    - preserve generated output semantics while moving another generator body off direct `ImmutableType/ImmutableProp` semantic reads
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable impl getter metadata-input slice passed (2026-03-13):
  - extended module-local impl metadata projection with:
    - `ImmutableImplGetterPropMetadata`
    - `ImmutableImplGetterPropKind`
    - `ImmutableProp.toImplGetterPropMetadata()`
  - `ImplGenerator.addProp` now consumes getter metadata for:
    - description annotation text
    - property return type
    - id-view list/scalar branching
    - many-to-many-view deeper-prop branching
    - unloaded-condition / error-throw field wiring for normal properties
  - `ImplGenerator` no longer directly reads `ImmutableProp` inside its getter layout loop; `ProducerGenerator` keeps projecting `ImmutableType.toImplTypeMetadata()` at the call site
  - kept the change intentionally narrow:
    - `ImplGenerator` still receives the original `ImmutableType` only for `clone`, `__isLoaded`, `__isVisible`, `hashCode`, `equals` and other remaining state-machine/comparison logic
    - no new shared immutable framework/module was introduced; metadata remains local to `jimmer-ksp-immutable`
  - scope/intent of this slice:
    - continue shrinking the primary `Impl` body before touching the heavier `__isLoaded/__equals` style branches
    - preserve generated output semantics while moving another generator body off direct `ImmutableProp` semantic reads
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable draft-impl accessor metadata-input slice passed (2026-03-13):
  - added module-local draft-impl accessor metadata projection:
    - `ImmutableDraftImplPropFunMetadata`
    - `ImmutableDraftImplPropRefMetadata`
    - `ImmutableProp.toDraftImplPropFunMetadata()`
    - `ImmutableProp.toDraftImplPropRefMetadata()`
  - `DraftImplGenerator` now projects metadata at the call site for:
    - `addPropFun(...)`
    - `addPropRefFun(...)`
  - `DraftImplGenerator.addPropFun` now consumes metadata for:
    - propId load checks
    - draft return/cast types
    - list vs target-producer initialization branch
  - `DraftImplGenerator.addPropRefFun` now consumes metadata for:
    - block parameter draft type
    - reference-style `prop().apply(block)` layout
  - kept the change intentionally narrow:
    - `DraftImplGenerator.addProp` getter/setter body still directly uses `ImmutableProp`
    - `DraftImplGenerator` still receives the original `ImmutableType` for unload/set/show and other remaining state-machine logic
    - no new shared immutable framework/module was introduced; metadata remains local to `jimmer-ksp-immutable`
  - scope/intent of this slice:
    - keep shrinking `DraftImpl` from the small accessor seams outward before touching the heavier mutable setter/state-machine path
    - preserve generated output semantics while moving another generator body off direct `ImmutableProp` semantic reads
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable draft-impl property getter-setter metadata-input slice passed (2026-03-13):
  - extended module-local draft-impl property metadata with:
    - `ImmutableDraftImplPropertyMetadata`
    - `ImmutableDraftImplPropertyGetterKind`
    - `ImmutableDraftImplPropertySetterKind`
    - `ImmutableProp.toDraftImplPropertyMetadata()`
  - `DraftImplGenerator.addProp` now consumes property metadata for:
    - property return type and mutability
    - getter branch selection:
      - id-view list
      - draft list
      - draft object
      - passthrough
    - setter branch selection:
      - id-view transform
      - id-view direct assign
      - standard validation + modified-field writeback
  - `DraftImplGenerator.addProp` no longer directly reads `ImmutableProp`; `ValidationGenerator` input, modified-field names, id-view transform details and list-copy decisions all come from preprojected metadata
  - kept the change intentionally narrow:
    - `DraftImplGenerator` still iterates original `ImmutableType` for `addAssociatedIdProp`, unload/set/show dispatch and other remaining state-machine logic
    - no new shared immutable framework/module was introduced; metadata remains local to `jimmer-ksp-immutable`
  - scope/intent of this slice:
    - finish shrinking the main DraftImpl property-declaration path before touching dispatch/state-machine branches
    - preserve generated output semantics while removing another large generator body’s direct `ImmutableProp` dependency
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable draft-impl dispatch metadata-input slice passed (2026-03-13):
  - extended module-local draft-impl metadata with:
    - `ImmutableDraftImplDispatchTypeMetadata`
    - `ImmutableDraftImplDispatchPropMetadata`
    - `ImmutableDraftImplUnloadKind`
    - `ImmutableDraftImplSetKind`
    - `ImmutableType.toDraftImplDispatchTypeMetadata()`
  - `DraftImplGenerator` now consumes dispatch metadata for:
    - `addUnloadFun(...)`
    - `addSetFun(...)`
    - `addShowFun(...)`
  - `ProducerGenerator` now projects draft-impl dispatch metadata at the call site instead of letting `DraftImplGenerator` directly read `ImmutableType.propsOrderById` / `ImmutableProp` semantics in those dispatch loops
  - semantic decisions moved out of dispatch layout code and into metadata projection:
    - unload branch kind:
      - delegate to base prop
      - no-op for kotlin formula
      - reset loaded/value pair
      - reset value only
    - set branch kind:
      - readonly ignore
      - assign with nullable check
    - dispatch-wide values:
      - prop order
      - slot/name mapping
      - visibility size
      - type description for illegal-prop error text
  - kept the change intentionally narrow:
    - `DraftImplGenerator` still keeps the original `ImmutableType` for non-dispatch logic such as fields, resolve flow, companion details and associated-id generation
    - no new shared immutable framework/module was introduced; metadata remains local to `jimmer-ksp-immutable`
  - scope/intent of this slice:
    - finish shrinking the remaining DraftImpl dispatch/state-machine shell before considering deeper resolve/ctx/companion cleanup
    - preserve generated output semantics while removing another large block’s direct `ImmutableProp` loop dependence
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable draft-impl type metadata-input slice passed (2026-03-13):
  - extended module-local draft-impl metadata with:
    - `ImmutableDraftImplTypeMetadata`
    - `ImmutableDraftImplMemberMetadata`
    - `ImmutableDraftImplResolvePropMetadata`
    - `ImmutableDraftImplResolveKind`
    - `ImmutableDraftImplTypeValidatorMetadata`
    - `ImmutableType.toDraftImplTypeMetadata()`
  - `DraftImplGenerator` now consumes full draft-impl type metadata for:
    - class header / constructor typing
    - `addFields()`
    - member loop in `generate()`
    - `addResolveFun()`
    - `addCompanionObject()`
    - associated-id helper entry
  - `ProducerGenerator` now projects full draft-impl type metadata at the call site instead of passing raw `ImmutableType` plus a separate dispatch metadata object
  - semantic decisions moved out of `DraftImplGenerator` into metadata projection:
    - draft-impl member aggregation:
      - property
      - prop fun
      - prop ref fun
      - associated-id helper
    - resolve-stage list/object branch selection
    - type-level validator list
    - companion validation/email/pattern source set
    - draft/producer/impl class-name wiring
  - kept the change intentionally narrow:
    - metadata remains local to `jimmer-ksp-immutable`
    - no new shared immutable framework/module was introduced
    - `ImplGenerator` remaining state/hash/equals logic and `DraftGenerator` top-level interface helpers are still pending
  - scope/intent of this slice:
    - finish removing `DraftImplGenerator`'s direct `ImmutableType/ImmutableProp` dependency before moving to the remaining immutable state-machine paths
    - preserve generated output semantics while continuing the local metadata-first seam strategy
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable impl state metadata-input slice passed (2026-03-13):
  - extended module-local impl metadata with:
    - `draftProducerImplClassName`
    - `draftProducerImplementorClassName`
    - `typeDescription`
    - `stateProps`
    - `ImmutableImplStatePropMetadata`
    - `ImmutableImplLoadKind`
    - `ImmutableImplLoadDependencyMetadata`
    - `ImmutableImplLoadSlotRefMetadata`
  - `ImplGenerator` now consumes impl metadata for:
    - `addCloneFun()`
    - `addIsLoadedFun(...)`
    - `addIsVisibleFun(...)`
    - `addHashCodeFun(...)`
    - `addEqualsFun(...)`
  - `ProducerGenerator` now projects a single `toImplTypeMetadata()` result into `ImplGenerator`; raw `ImmutableType` is no longer passed into the impl sub-generator
  - semantic decisions moved out of `ImplGenerator` into metadata projection:
    - id-view list/scalar load checks
    - many-to-many view deeper-prop load checks
    - kotlin-formula dependency-chain load checks
    - loaded/nullability/id/association comparison semantics
    - draft-impl / implementor target class-name wiring
    - illegal-prop error text source description
  - kept the change intentionally narrow:
    - metadata remains local to `jimmer-ksp-immutable`
    - no new shared immutable framework/module was introduced
    - `DraftGenerator` top-level interface/helper path is still pending
  - scope/intent of this slice:
    - finish removing `ImplGenerator`'s direct `ImmutableType/ImmutableProp` dependency before moving to the remaining immutable top-level helpers
    - preserve generated output semantics while continuing the local metadata-first seam strategy
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable draft top-level metadata-input slice passed (2026-03-13):
  - added module-local draft metadata projection:
    - `DraftMetadata.kt`
    - `ImmutableDraftTypeMetadata`
    - `ImmutableDraftDeclaredPropMetadata`
    - `ImmutableDraftAddFunMetadata`
    - `ImmutableDraftNewFunMetadata`
    - `ImmutableDraftCopyFunMetadata`
    - `ImmutableType.toDraftTypeMetadata(...)`
  - `DraftGenerator` now consumes draft metadata for:
    - top-level draft interface declaration
    - declared property / fun / ref-fun layout
    - associated-id helper entry
    - `addBy(...)`
    - `by(...)` / `TypeName(...)`
    - `copy(...)`
  - `ImmutableProcessor` now projects draft metadata before invoking `DraftGenerator`
  - `ProducerGenerator` now consumes only producer metadata; `ImmutableType` is no longer forwarded from `DraftGenerator` into producer sub-generation
  - semantic decisions moved out of `DraftGenerator` into metadata projection:
    - super draft inheritance list
    - declared prop mutability and abstract helper signatures
    - add/by/copy helper receiver/base/block signature variants
    - nested producer/builder metadata wiring
  - kept the change intentionally narrow:
    - metadata remains local to `jimmer-ksp-immutable`
    - no new shared immutable framework/module was introduced
    - remaining immutable coupling is now concentrated in module-local metadata projection helpers rather than main generator classes
  - scope/intent of this slice:
    - finish the current `immutable` generator metadata-first收口 so the major generator classes no longer directly consume `ImmutableType/ImmutableProp`
    - preserve generated output semantics while keeping the next step open for shared-reader/model extraction
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable processor source-metadata orchestration slice passed (2026-03-13):
  - added immutable metadata modules:
    - `project/compiler/immutable/immutable-metadata-model`
    - `project/compiler/immutable/immutable-metadata-extractor`
    - `project/compiler/immutable/immutable-metadata-generator`
  - `ImmutableProcessor.generateJimmerTypes(...)` now builds and consumes file-level source metadata instead of repeatedly re-reading `ImmutableType` lists during finish-stage orchestration
  - added / moved structures:
    - `ImmutableSourceMetadata` in `immutable-metadata-model`
    - `ImmutableSourceMetadataExtractor` in `immutable-metadata-extractor`
    - `JimmerModuleMetadataGenerator` in `immutable-metadata-generator`
    - processor-local `ImmutableSourceGenerationMetadata` now keeps pure `ImmutableSourceMetadata` plus generator-facing metadata inputs
  - orchestration decisions moved out of inline finish-stage branching and into source metadata projection:
    - source package/file naming
    - draft generator inputs
    - first SQL type selection
    - props/fetcher generator inputs
    - entity qualified-name collection for module resource generation
    - multi-type error reporting names
  - kept the change intentionally narrow:
    - `findModelMap()` round collection still aggregates `ImmutableType`; this slice only shrinks finish-stage orchestration coupling and starts the immutable `metadata-model/extractor/generator` module layout
    - draft/props/fetcher main generators still stay in `jimmer-ksp-immutable` for now
  - scope/intent of this slice:
    - extend metadata-first cleanup from generator bodies into processor finish-stage orchestration
    - start giving immutable the same explicit metadata module topology already used by error/tx/client/tuple, without changing external processor behavior
  - `:project:compiler:immutable:immutable-metadata-model:compileKotlin`
  - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
  - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable generator cluster-downstream slice passed (2026-03-13):
  - physically moved pure immutable generator/projection helpers from `jimmer-ksp-immutable` into `immutable-metadata-generator`:
    - `AssociatedIdGenerator.kt`
    - `AssociatedIdMetadata.kt`
    - `BuilderGenerator.kt`
    - `BuilderMetadata.kt`
    - `FetcherDslGenerator.kt`
    - `FetcherGenerator.kt`
    - `FetcherMetadata.kt`
    - `PropsGenerator.kt`
    - `PropsMetadata.kt`
    - `ValidationGenerator.kt`
    - `ValidationMetadata.kt`
    - `CaseAppender.kt`
    - `Validations.kt`
  - kept package names and generator output semantics unchanged:
    - `site.addzero.lsi.jimmer.immutable.generator`
    - no processor FQCN / SPI / output target changes
  - module-boundary follow-up applied in `immutable-metadata-generator`:
    - added `projects.project.jimmerDtoCompiler`
    - added `projects.project.jimmerCore`
    - widened only the cross-module entry types/functions from `internal` to module-visible so `jimmer-ksp-immutable` can continue orchestrating them
  - effect of this slice:
    - `jimmer-ksp-immutable` shrank further toward processor-entry/orchestration-only responsibility
    - associated-id/builder/fetcher/props/validation generation chain is now physically hosted beside `JimmerModuleMetadataGenerator`, instead of remaining in the processor-entry module
    - immutable generator-side pure helper placement is now more aligned with the `*-metadata-generator` topology already used by other compiler domains
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no new neutral immutable IR was introduced
    - remaining immutable-local projection helpers still include:
      - `DraftMetadata.kt`
      - `DraftImplAccessorMetadata.kt`
      - `ImplMetadata.kt`
      - `ImplementorMetadata.kt`
      - `ProducerMetadata.kt`
      - and `ImmutableProcessor.findModelMap()` still aggregates raw `ImmutableType`
  - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable generator metadata full-relocation slice passed (2026-03-13):
  - continued moving the remaining immutable metadata projection files from `jimmer-ksp-immutable` into `immutable-metadata-generator`:
    - `DraftMetadata.kt`
    - `DraftImplAccessorMetadata.kt`
    - `ImplMetadata.kt`
    - `ImplementorMetadata.kt`
    - `ProducerMetadata.kt`
  - widened only the cross-module metadata entry declarations needed by remaining generator/processor callers:
    - `ImmutableDraftTypeMetadata` / `toDraftTypeMetadata(...)`
    - `ImmutableDraftImplTypeMetadata` / related draft-impl metadata enums and entry projections
    - `ImmutableImplTypeMetadata` / related impl metadata enums and entry projections
    - `ImmutableImplementorTypeMetadata` / `deeperPropIdPropName(...)`
    - `ImmutableProducerTypeMetadata` / related producer metadata enums and entry projections
  - effect of this slice:
    - immutable 的 generator-facing metadata projection 已整体从 processor entry module 平移到 `immutable-metadata-generator`
    - `jimmer-ksp-immutable` 不再保留任何 `*Metadata.kt` 投影文件
    - immutable 侧“metadata-first + generator-module”边界进一步固化
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - `ImmutableProcessor.findModelMap()` 仍然直接聚合 `ImmutableType`
    - `ImmutableProcessor.toSourceGenerationMetadata(...)` 仍然在 processor 内桥接 `ImmutableType -> source metadata + generator metadata`
  - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable generator entry-module shrink slice passed (2026-03-13):
  - moved the remaining pure immutable KotlinPoet generators from `jimmer-ksp-immutable` into `immutable-metadata-generator`:
    - `DraftGenerator.kt`
    - `DraftImplGenerator.kt`
    - `ImplementorGenerator.kt`
    - `ImplGenerator.kt`
    - `ProducerGenerator.kt`
  - current state:
    - `project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable` now only contains `ImmutableProcessor.kt`
    - immutable entry module is effectively processor-orchestration-only
  - dependency cleanup:
    - `jimmer-ksp-immutable/build.gradle.kts` removed direct `jimmerCore` and `kotlinpoet` implementation dependencies
    - attempted to remove `jimmerDtoCompiler` too, but had to keep it because `ImmutableProcessor` still directly references `ImmutableType`, whose supertype chain requires `BaseType` on the compile classpath
  - module-boundary follow-up:
    - `DraftGenerator` was widened to module-visible because `ImmutableProcessor` invokes it directly across the module boundary
    - downstream `ProducerGenerator` / `ImplementorGenerator` / `ImplGenerator` / `DraftImplGenerator` remain internal inside `immutable-metadata-generator`
  - effect of this slice:
    - `jimmer-ksp-immutable` has now reached the intended shape of “KSP processor entry + orchestration only”
    - immutable generation implementation lives in `immutable-metadata-generator`; file-level planning is split between `immutable-metadata-model/extractor/generator` and the processor
  - remaining immutable refactor focus is now much narrower:
    - `ImmutableProcessor.findModelMap()` still aggregates `ImmutableType`
    - `ImmutableProcessor.toSourceGenerationMetadata(...)` still does the final `ImmutableType -> draft/props/fetcher/source` projection bridge inside the processor module
  - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
  - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
  - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
  - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
  - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
  - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
  - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
  - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable source grouping/plan helper slice passed (2026-03-14):
  - added / moved helper entry points:
    - `ImmutableSourceGenerationPlan.kt` in `immutable-metadata-generator`
    - `ImmutableSourceTypeMapExtractor.kt` in `immutable-metadata-extractor`
  - `ImmutableProcessor` no longer owns:
    - `findModelMap()`
    - `sourceKeyOf(...)`
    - processor-local source-generation-plan assembly
  - effect of this slice:
    - round-stage candidate filtering, Jimmer type gate, interface/type-parameter/visibility validation, and source-key grouping are now hosted in `immutable-metadata-extractor`
    - finish-stage `ImmutableType -> source plan` projection is now hosted in `immutable-metadata-generator`
    - processor entry now keeps only resolver scheduling, round accumulation, finish reconstruction, artifact write-out, and SPI notification
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no new neutral immutable IR was introduced
    - `ImmutableProcessor` still reconstructs `Map<String, List<ImmutableType>>` during `onFinish()`
  - revalidated with:
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable artifact-assembly shrink slice passed (2026-03-14):
  - added `ImmutableGeneratedArtifacts.kt` in `immutable-metadata-generator`
  - moved out of `ImmutableProcessor.generateJimmerTypes(...)`:
    - per-source `DraftGenerator` / `PropsGenerator` / `FetcherGenerator` artifact expansion
    - source-level multi-immutable-type conflict validation
    - `PackageCollector` and common package prefix aggregation
    - `JimmerModuleMetadataGenerator` orchestration assembly
  - effect of this slice:
    - `ImmutableProcessor` now delegates source-plan materialization to `toGeneratedArtifacts(...)`
    - processor entry keeps only `sourcePlans` creation, artifact write-out, and finish-stage orchestration
    - immutable generator-side package/resource assembly now fully lives under `immutable-metadata-generator`
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no new immutable neutral schema/model abstraction was introduced
    - `DraftGenerator` still explicitly receives `jacksonTypes`, now passed through the metadata-generator helper instead of directly from processor-local loops
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable finish-rebuild helper slice passed (2026-03-14):
  - added `CollectedImmutableTypeMapExtractor.kt` in `immutable-metadata-extractor`
  - moved out of `ImmutableProcessor.onFinish()`:
    - `sourceKey -> qualifiedName collection` 回放到 `Map<String, List<ImmutableType>>` 的重建循环
    - per-entry `lsiResolver.findClassByQualifiedName(...)` + `ctx.typeOf(...)` 桥接
  - effect of this slice:
    - `ImmutableProcessor` finish-stage 只保留 barrier、空集短路、artifact generation、SPI notification
    - source collection replay semantics now live beside round scan/grouping helper in the immutable extractor module
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - processor still stores round accumulation as `sourceKey -> qualifiedName set`
    - processor still builds source plans from `ImmutableType`
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable round-metadata accumulation slice passed (2026-03-14):
  - added `ImmutableCollectedSourceMetadata.kt` in `immutable-metadata-model`
  - renamed / reshaped round extractor helper:
    - `ImmutableSourceTypeMapExtractor.kt` -> `ImmutableCollectedSourceMetadataExtractor.kt`
    - round scan output changed from `Map<String, List<ImmutableType>>` to pure `List<ImmutableCollectedSourceMetadata>`
  - added `ImmutableCollectedSourceAccumulator.kt` in `immutable-metadata-extractor`
  - effect of this slice:
    - `ImmutableProcessor.onRound()` no longer constructs `ImmutableType`
    - process-stage accumulation now keeps only `sourceKey + qualifiedNames` metadata
    - processor-local `collectedBySourceKey` / `collectedTypeNames` state was removed and replaced by a domain-specific accumulator helper
    - `ImmutableType` construction is now deferred to finish-stage rebuild only
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no new generic metadata framework was introduced
    - finish-stage source-plan projection still starts from rebuilt `Map<String, List<ImmutableType>>`
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-model:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable finish-bridge generator consolidation slice passed (2026-03-14):
  - `immutable-metadata-generator/build.gradle.kts` now depends on `immutable-metadata-extractor`
  - added `Map<String, List<ImmutableType>>.toGeneratedArtifacts(...)` overload in `ImmutableGeneratedArtifacts.kt`
  - moved out of `ImmutableProcessor`:
    - `sourceKey + ImmutableType list -> ImmutableSourceMetadata`
    - `ImmutableType list -> ImmutableSourceGenerationPlan`
    - `source plan -> artifacts` orchestration entry call chain
  - follow-up shrink:
    - `ImmutableProcessor` removed its local `ImmutableSourceMetadataExtractor`
    - `ImmutableProcessor.generateJimmerTypes(...)` helper was deleted
    - `onFinish()` now directly performs `resolve -> generated artifacts write-out -> entity meta notify`
  - effect of this slice:
    - processor entry is now closer to pure lifecycle/orchestration-only shape
    - immutable metadata-generator owns the full finish-stage generation bridge from rebuilt `ImmutableType` buckets to final artifacts
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no new neutral immutable IR was introduced
    - `ImmutableType` is still the rebuilt finish-stage big object passed into the generation bridge
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-model:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable entity-consumer input decoupling slice passed (2026-03-14):
  - added `ImmutableCollectedSourceAccumulator.toLsiClasses(...)`
  - moved out of `ImmutableProcessor`:
    - `immutableTypeMultiMap.values.flatten().map { it.lsiClass }` notification input projection
  - effect of this slice:
    - `EntityMetaConsumerSpi` input is now replayed directly from collected-source metadata
    - processor no longer depends on finish-stage `ImmutableType` map for SPI notification input
    - rebuilt `ImmutableType` buckets are now only used for generation bridge, not for side-channel SPI input assembly
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no behavior change to SPI contract (`List<LsiClass>`)
    - processor still rebuilds `ImmutableType` for final generation bridge
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable single-pass finish resolution slice passed (2026-03-14):
  - added `ImmutableCollectedSourceResolution.kt` in `immutable-metadata-extractor`
  - `ImmutableCollectedSourceAccumulator` now exposes `resolve(...)` instead of separate:
    - `toImmutableTypeMap(...)`
    - `toLsiClasses(...)`
  - added `ImmutableCollectedSourceResolution.toGeneratedArtifacts(...)` in `immutable-metadata-generator`
  - removed obsolete helper:
    - `CollectedImmutableTypeMapExtractor.kt`
  - effect of this slice:
    - collected-source metadata is now replayed only once during finish-stage
    - `LsiClass` notification input and rebuilt `ImmutableType` buckets are produced in one resolver pass
    - `ImmutableProcessor` no longer explicitly imports `ImmutableType`
    - processor finish-stage now consumes a single resolved aggregate plus final artifacts
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no new neutral immutable IR was introduced
    - the resolved aggregate still internally carries rebuilt `ImmutableType` buckets for generation
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable generator API-surface shrink slice passed (2026-03-14):
  - deleted `ImmutableSourceGenerationPlan.kt` standalone file
  - moved `ImmutableSourceGenerationPlan` and `List<ImmutableType>.toImmutableSourceGenerationPlan(...)` into `ImmutableGeneratedArtifacts.kt` as private implementation details
  - narrowed generator internals:
    - `Map<String, List<ImmutableType>>.toGeneratedArtifacts(...)` is now private
    - `List<ImmutableSourceGenerationPlan>.toGeneratedArtifacts(...)` is now private
  - cleanup follow-up:
    - removed unused `ImmutableType` import from `ImmutableProcessor`
  - effect of this slice:
    - `ImmutableType`-driven source-generation-plan bridge is now fully hidden inside the generator implementation file
    - processor + cross-module call sites only see `ImmutableCollectedSourceResolution -> GeneratedArtifact`
    - immutable metadata-generator external API surface is smaller and more aligned with metadata-first orchestration
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no behavioral change to generated outputs
    - private implementation still internally relies on rebuilt `ImmutableType`
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable resolved-source consolidation slice passed (2026-03-14):
  - `ImmutableCollectedSourceResolution` now carries:
    - `sources: List<ImmutableResolvedSource>`
    - flattened `lsiClasses`
  - each `ImmutableResolvedSource` now carries:
    - `metadata: ImmutableSourceMetadata`
    - rebuilt `immutableTypes`
  - extractor follow-up:
    - `ImmutableSourceMetadataExtractor.extract(...)` input had already been narrowed from `List<ImmutableType>` to `List<LsiClass>`
    - `ImmutableCollectedSourceAccumulator.resolve(...)` now precomputes `ImmutableSourceMetadata` and stores it directly into each resolved source
  - processor/generator follow-up:
    - `ImmutableProcessor.onFinish` no longer reads the removed `immutableTypeMultiMap`; early-exit now checks `resolvedSources.sources`
    - `ImmutableGeneratedArtifacts.kt` no longer instantiates `ImmutableSourceMetadataExtractor` on the generator side
    - source-level SQL/entity/fetcher/source-name aggregation now stays fully inside extractor/resolve stage
  - effect of this slice:
    - finish-stage parallel maps are gone; per-source resolved aggregate is now the single handoff shape
    - source metadata extraction is now complete before generator entry
    - per-source handoff no longer carries `LsiClass`; flattened `lsiClasses` is retained only at resolution top-level for `EntityMetaConsumerSpi`
    - rebuilt `ImmutableType` is still retained only for downstream draft/props/fetcher metadata projection
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no generated-output behavior changes
    - downstream generator metadata projection still consumes rebuilt `ImmutableType`
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable generator projector-centralization slice passed (2026-03-14):
  - new private bridge file:
    - `immutable-metadata-generator/.../ImmutableGeneratorMetadataProjector.kt`
  - bridge consolidation:
    - the scattered `ImmutableType` / `ImmutableProp` -> generator-metadata projection functions from `DraftMetadata.kt` / `BuilderMetadata.kt` / `FetcherMetadata.kt` / `ImplementorMetadata.kt` / `ProducerMetadata.kt` / `PropsMetadata.kt` / `ImplMetadata.kt` / `DraftImplAccessorMetadata.kt` / `AssociatedIdMetadata.kt` / `ValidationMetadata.kt` are now concentrated in that single projector file
  - resulting boundary cleanup:
    - the metadata definition files above now only keep pure metadata data classes and pure metadata helpers
    - `PropsMetadata.kt::toPropsTypeRefMetadata()` was widened from file-private to module-visible so projector-side props projection can still reuse the pure type-ref helper without reintroducing a second bridge copy
  - effect of this slice:
    - remaining `ImmutableType` bridge in `immutable-metadata-generator` is now explicit and single-pointed instead of spread across many metadata files
    - next migration step can push that single projector further toward extractor/model boundaries without reopening every generator metadata file
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no generated-output behavior changes
    - no generator class/FQCN changes
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable artifact-assembly de-ImmutableType slice passed (2026-03-14):
  - `ImmutableGeneratorMetadataProjector.kt` now also owns:
    - internal `ImmutableSourceGenerationPlan`
    - `ImmutableResolvedSource.toImmutableSourceGenerationPlan(...)`
  - `ImmutableGeneratedArtifacts.kt` follow-up:
    - removed direct `ImmutableType` import and the local `List<ImmutableType>.toImmutableSourceGenerationPlan(...)`
    - artifact assembly now directly consumes projector-output source plans
  - resulting boundary cleanup:
    - `immutable-metadata-generator/.../metadata/generator/ImmutableGeneratedArtifacts.kt` is now pure artifact-assembly logic over already projected metadata
    - generator-side `ImmutableType` bridge is narrowed further to projector file plus the existing validation helper path
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no generated-output behavior changes
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable validation-bridge single-point slice passed (2026-03-14):
  - `immutable-metadata-generator/.../Validations.kt` has been deleted
  - `ImmutableGeneratorMetadataProjector.kt` now also owns the validation-annotation aggregation helper formerly exposed as `ImmutableProp.validationAnnotationMirrorMultiMap`
  - resulting boundary cleanup:
    - direct `ImmutableProp` / `ImmutableType` imports inside `immutable-metadata-generator` are now localized to a single file:
      - `immutable-metadata-generator/.../ImmutableGeneratorMetadataProjector.kt`
    - `ValidationGenerator` continues to consume only `ImmutableValidationPropMetadata`
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no generated-output behavior changes
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable props/fetcher metadata hoist slice passed (2026-03-14):
  - new pure metadata-model files:
    - `immutable-metadata-model/.../ImmutablePropsMetadata.kt`
    - `immutable-metadata-model/.../ImmutableFetcherMetadata.kt`
  - extractor follow-up:
    - added `immutable-metadata-extractor/.../ImmutablePropsFetcherMetadataProjector.kt`
    - `ImmutableCollectedSourceAccumulator.resolve(...)` now precomputes:
      - `propsTypeMetadata`
      - `fetcherTypeMetadata`
    - `ImmutableResolvedSource` now carries those two projected metadata objects beside `metadata` and rebuilt `immutableTypes`
  - generator follow-up:
    - deleted `immutable-metadata-generator/.../FetcherMetadata.kt`
    - `PropsMetadata.kt` now only keeps the generator-side `ImmutablePropsTypeRefMetadata.toTypeName(...)` materializer helper
    - `ImmutableGeneratorMetadataProjector.kt` no longer owns props/fetcher projection logic; source plan projection now directly reuses resolved-source cached metadata
    - `PropsGenerator.kt` / `FetcherGenerator.kt` / `FetcherDslGenerator.kt` now import metadata from `immutable-metadata-model`
  - resulting boundary cleanup:
    - props/fetcher value objects are no longer generator-private
    - source-level target-type selection for props/fetcher now happens in extractor resolve stage instead of generator projector replay
    - remaining `ImmutableType` bridge in generator projector shrank again; props/fetcher left the file completely
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no generated-output behavior changes
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-model:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable implementor metadata hoist slice passed (2026-03-14):
  - new pure metadata-model file:
    - `immutable-metadata-model/.../ImmutableImplementorMetadata.kt`
  - extractor follow-up:
    - added `immutable-metadata-extractor/.../ImmutableImplementorMetadataProjector.kt`
    - moved `toImplementorTypeMetadata()` and shared `deeperPropIdPropName()` naming helper into extractor
  - generator follow-up:
    - deleted `immutable-metadata-generator/.../ImplementorMetadata.kt`
    - `ImplementorGenerator.kt` and `ProducerMetadata.kt` now import implementor metadata from `immutable-metadata-model`
    - `ImmutableGeneratorMetadataProjector.kt` now imports extractor-side `toImplementorTypeMetadata()` / `deeperPropIdPropName()` instead of defining implementor projection locally
  - resulting boundary cleanup:
    - implementor value objects are no longer generator-private
    - the many-to-many deeper-prop constant-name helper is now shared from extractor instead of being trapped in generator projector
    - `ImmutableGeneratorMetadataProjector.kt` size dropped further from the earlier 849 lines to 701 lines after props/fetcher/implementor hoists
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no generated-output behavior changes
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-model:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable associated-id/validation metadata hoist slice passed (2026-03-14):
  - new pure metadata-model files:
    - `immutable-metadata-model/.../ImmutableAssociatedIdMetadata.kt`
    - `immutable-metadata-model/.../ImmutableValidationMetadata.kt`
  - extractor follow-up:
    - added `immutable-metadata-extractor/.../ImmutableAssociatedIdValidationMetadataProjector.kt`
    - moved `validationAnnotationMirrorMultiMap`, `toAssociatedIdMetadata()` and `toValidationPropMetadata()` out of generator projector into extractor
    - `immutable-metadata-model` and `immutable-metadata-extractor` now both declare `kotlinpoet` dependency because these metadata payloads already carry `TypeName` / `ClassName`
  - generator follow-up:
    - deleted:
      - `immutable-metadata-generator/.../AssociatedIdMetadata.kt`
      - `immutable-metadata-generator/.../ValidationMetadata.kt`
    - `AssociatedIdGenerator.kt` / `DraftGenerator.kt` / `DraftMetadata.kt` / `DraftImplAccessorMetadata.kt` / `DraftImplGenerator.kt` / `ValidationGenerator.kt` now consume associated-id / validation metadata from `immutable-metadata-model`
    - `ImmutableGeneratorMetadataProjector.kt` now imports extractor-side associated-id / validation projection helpers instead of defining them locally
  - resulting boundary cleanup:
    - associated-id / validation value objects are no longer generator-private
    - generator projector lost another standalone property-level bridge segment
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no generated-output behavior changes
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-model:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Immutable builder metadata hoist slice passed (2026-03-14):
  - new pure metadata-model file:
    - `immutable-metadata-model/.../ImmutableBuilderMetadata.kt`
  - extractor follow-up:
    - added `immutable-metadata-extractor/.../ImmutableBuilderMetadataProjector.kt`
    - moved `toBuilderTypeMetadata()`, `toBuilderSetterMetadata()` and `isVisibilityControllable()` out of generator projector into extractor
  - generator follow-up:
    - deleted `immutable-metadata-generator/.../BuilderMetadata.kt`
    - `BuilderGenerator.kt` and `DraftMetadata.kt` now consume builder metadata from `immutable-metadata-model`
    - `ImmutableGeneratorMetadataProjector.kt` now imports extractor-side builder projection helper instead of defining builder projection locally
  - resulting boundary cleanup:
    - builder value objects are no longer generator-private
    - projector shrank again after removing another type-level/prop-level bridge segment
    - `immutable-metadata-generator/.../ImmutableGeneratorMetadataProjector.kt` line count is now down to 602
  - kept the change intentionally narrow:
    - no APT changes
    - no DTO changes
    - no generated-output behavior changes
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-model:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:compiler:immutable:immutable-metadata-generator:compileKotlin`
    - `:project:compiler:immutable:jimmer-ksp-immutable:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:compiler:transactional:jimmer-ksp-transactional:compileKotlin`
    - `:project:compiler:tuple:jimmer-ksp-tuple:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- APT compile gate still failing before this slice can be declared cross-engine complete:
  - `:lib:lsi:lsi-apt:compileKotlin` passed
  - `:project:jimmer-apt:compileJava` failed on existing `AptLsiContext` API drift:
    - missing `getLsiResolver()`
    - missing `init(ProcessingEnvironment, null)`
    - missing `resetRound(RoundEnvironment, null)`
  - this blocker is pre-existing and not introduced by the new `error-metadata-*` modules
- Note:
  - 2026-03-12 这轮验证时，默认增量缓存出现 Kotlin daemon/cacheable 锁冲突；使用 `./gradlew --stop` 后，再通过 `-Pkotlin.incremental=false` 完成验证。
  - 该异常表现为 build cache/daemon 状态问题，不是本轮代码修改引入的语义编译错误。
- LSI symbol-capability completion slice advanced (2026-03-14, current thread):
  - 本轮主线已从“继续 hoist metadata / poet 桥接”切回“补齐 apt/ksp 共用的纯符号语义”，避免把 `lsi-core` 继续拖进 poet 迁移范围。
  - completed symbol additions in `lsi-core`:
    - `LsiClass.packageName`
    - `LsiClass.simpleNames`
    - `LsiClass.isStatic`
    - `LsiMethod.thrownTypes`
    - `LsiClass.fileName/isObject/isCompanionObject` 的文档与 adapter 语义不再标记为 “目前 ksp only”
  - completed adapter alignment:
    - `lsi-apt`:
      - `AptLsiClass` 现在补齐 `packageName/simpleNames/isStatic/fileName`
      - `AptLsiMethod` 现在补齐 `thrownTypes`
      - `AptLsiAnnotation` 现在补齐元注解遍历，以及 class-literal / nested-annotation / list attribute 的 LSI 化返回，不再只退化成字符串
    - `lsi-ksp`:
      - `KspLsiClass` 现在补齐 `packageName/simpleNames/isStatic`
      - `KspLsiMethod` 现在把 Kotlin `@Throws` 统一投影成 `thrownTypes`
    - `lsi-reflection`:
      - `ClazzLsiClass` / `ClazzLsiMethod` 已同步补齐对应接口实现，用于维持 `lsi-core` 新符号面的完整性
  - completed compiler-side consumption:
    - `client-metadata-extractor`:
      - `LsiClientExceptionTypeSupport` 统一读取 `method.thrownTypes`，同时兼容旧 `@Throws` 注解路径
      - `LsiClientTypeSupport` 改为基于 `packageName + simpleNames` 构造 client `TypeName`，不再手拆 `qualifiedName`
      - `LsiClientSchemaMaterialization` 补回 “仅接受 top-level 或 static nested type” 校验，消费 `LsiClass.isStatic`
    - `tx-metadata-*`:
      - `TxMethodMetadata` 新增 `thrownTypes`
      - extractor 补回 `@Tx` 方法的 RuntimeException 约束校验
      - generator 用 Kotlin `@Throws(...)` 重新物化异常声明，避免丢失 method-level thrown-type 语义
  - validated with:
    - `:lib:lsi:lsi-core:compileKotlin`
    - `:lib:lsi:lsi-reflection:compileKotlin`
    - `:project:compiler:client:client-metadata-extractor:compileKotlin`
    - `:project:compiler:transactional:tx-metadata-model:compileKotlin`
    - `:project:compiler:transactional:tx-metadata-extractor:compileKotlin`
    - `:project:compiler:transactional:tx-metadata-generator:compileKotlin`
    - 上述 `client/tx` compile gate 需要临时跳过 `:project:compiler:jimmer-ksp-ext:compileKotlin`、`:lib:lsi:lsi-ksp:compileKotlin`、`:lib:lsi:lsi-apt:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
  - current external blocker:
    - `:lib:lsi:lsi-ksp:compileKotlin` and `:lib:lsi:lsi-apt:compileKotlin` are failing in the shared worktree because the parallel poet-migration line has already introduced uncompilable files under `lib/lsi/**/poet/*`
    - `:project:compiler:jimmer-ksp-ext:compileKotlin` is also blocked by the same parallel poet bridge breakage (`toClassName` / `toAnnotationSpec` / `toTypeName` unresolved), not by this symbol-capability slice
  - implication:
    - 当前已经能确认：这轮 symbol 补齐本身在 `lsi-core + reflection + client/tx metadata` 侧是闭合的
    - 但要宣布 `apt/ksp -> lsi` cross-engine compile gate 全绿，必须先等 `lsi-poet-migration-plan.md` 那条线程把 `lib/lsi/**/poet` / `jimmer-ksp-ext` 的中间断面修平
- LSI package-annotation completion slice (2026-03-15, current thread):
  - 本轮继续沿“纯符号能力补齐”推进，没有引入新的 metadata IR，也没有回桥到 `KS*` / `PackageElement`
  - completed symbol additions in `lsi-core`:
    - `LsiClass.packageAnnotations`
    - `LsiClassExt.packageAnnotation(...)`
    - `LsiMethod.annotations` 文档语义改为统一承接 method + return-type 注解落点
  - completed adapter alignment:
    - `lsi-apt`:
      - `AptLsiClass` 现在补齐 package-level 注解投影，覆盖 `PackageElement/package-info.java`
      - `AptLsiMethod.annotations` 现在统一包含方法本体 + returnType 注解
    - `lsi-ksp`:
      - `KspLsiClass` 现在补齐 package-level 注解投影，覆盖 Kotlin `@file:` package annotation
      - `KspLsiMethod.annotations` 现在统一包含函数本体 + returnType 注解
    - `lsi-reflection`:
      - `ClazzLsiClass` 同步补齐 `Class.getPackage().annotations`
  - completed compiler-side consumption:
    - `project/compiler/client/jimmer-ksp-client/.../LsiExportDocSupport.kt`
      - `collectExportDocTypeNames(...)` 改为使用 `LsiClass.packageName`
      - `exportDocPkg(...)` 改为通过 `LsiClass.packageAnnotations` 构建包级 `@ExportDoc` 继承树
      - 之前“package-level ExportDoc 空实现”的说明已失效，现已被真实 LSI 语义替换
    - 后续 shared helper 若需复用 APT `setNullityByJetBrainsAnnotation` 语义，已经可以先通过 `LsiMethod.annotations` 读取 return-type 注解，不必再回退到平台 API
  - validated with:
    - `:lib:lsi:lsi-core:compileKotlin`
    - `:lib:lsi:lsi-reflection:compileKotlin`
    - `:lib:lsi:lsi-ksp:compileKotlin`
    - `:lib:lsi:lsi-apt:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
  - note:
    - 这说明上一轮文档里“`lsi-ksp` / `lsi-apt` / `jimmer-ksp-ext` 被 poet 线程阻塞”的状态，至少在当前工作树下已经不是事实；后续应以当前 compile gate 为准，而不是继续沿用旧阻塞判断
- LSI apt record-component completion slice (2026-03-15, current thread):
  - 本轮继续收口 APT client 路径里还残留的 `RECORD_COMPONENT -> accessor` 平台特判，但处理方式仍是“先补 LSI 语义”，而不是把逻辑抬回 processor
  - completed adapter alignment:
    - `lsi-apt`:
      - `ElementExt` 新增 `isRecordComponent()`
      - `AptFieldExt` 新增 `Element.toLsiFieldOrNull(...)`，统一承接普通 field 与 record component
      - `AptLsiField` 新增 `AptLsiRecordComponentField`
      - `AptLsiClass.fields` 现在会把 Java record component 视为逻辑属性，并对同名 backing field 做去重，优先暴露 record component
  - completed semantics:
    - record component 注解统一承接：
      - component 本体
      - accessor 方法
      - accessor return type
    - record component 文档统一承接：
      - component doc
      - accessor doc fallback
    - 这样后续 shared helper 若直接消费 `LsiClass.fields`，无需再重复维护 `RECORD_COMPONENT` 反射桥接
  - 覆盖来源：
    - `project/jimmer-apt/.../client/ClientProcessor.fillDefinition` 的 `RECORD_COMPONENT` 反射 accessor 兼容逻辑
  - validated with:
    - `:lib:lsi:lsi-apt:compileKotlin`
    - `:project:jimmer-apt:compileJava`
- Client metadata extractor de-KSP + APT client metadata-first slice (2026-03-15, current thread):
  - 本轮把 `client-metadata-extractor` 从 `jimmer-ksp-ext` 的 KSP 上下文绑定里拆出来，使它真正能被 APT/KSP 共用，而不是只有 KSP 入口能复用
  - completed shared extractor boundary changes:
    - `project/compiler/client/client-metadata-extractor/.../DocMetadata.kt`
      - draft impl 文档读取改为显式 `draftImplDocMapProvider`
    - `project/compiler/client/client-metadata-extractor/.../LsiClientApiRules.kt`
      - service source-filter 判定改为显式 `matchesSourceFilters` 注入
    - `project/compiler/client/client-metadata-extractor/.../LsiClientSchemaTraversal.kt`
      - `handleClientApiService(...)` 不再在 helper 内部重复做 service 资格扫描，默认要求调用方先完成筛选
    - `project/compiler/client/client-metadata-extractor/.../ClientSchemaMetadataExtractor.kt`
      - `DocMetadata` / `ClientExceptionContext` 改为按次构建
      - `ClientSchemaMetadataExtractionInput` 新增 `draftImplDocMapOf`
  - completed LSI core ownership correction:
    - `MetaException`
    - `GeneratorException`
    - `GeneratedSourceArtifact` / `GeneratedResourceArtifact`
    - 以上三类基础契约已从 `project/compiler/jimmer-ksp-ext` 下沉到 `lib/lsi/lsi-core`
    - 迁移说明：这些都是 shared metadata/extractor/generator 的基础设施，不应继续寄存在 KSP 扩展模块
  - completed processor orchestration alignment:
    - KSP:
      - `project/compiler/client/jimmer-ksp-client/.../ClientProcessor.kt`
      - 现在显式注入 `matchesConfiguredSourceFilters()` 与 `Context.findDraftImplDocMap(...)`
    - APT:
      - `project/jimmer-apt/.../client/ClientProcessor.java`
      - 已改成 metadata-first orchestration：`collectClientApiServiceTypeNames -> ClientSchemaMetadataExtractor -> ClientSchemaMetadataGenerator -> writeArtifact`
      - 保留 JDK8 `@FetchBy` 防护与既有 `META-INF/jimmer/client` 资源路径
      - converter targetType 与 draft impl doc map 都只在入口层适配为 shared extractor 所需的 LSI/callback 语义
  - completed dependency alignment:
    - `project/jimmer-apt/build.gradle.kts` 新增：
      - `client-metadata-model`
      - `client-metadata-extractor`
      - `client-metadata-generator`
    - `project/compiler/client/client-metadata-extractor/build.gradle.kts`
      - 去掉 `project:compiler:jimmer-ksp-ext`
      - 改为直接依赖 `lib:lsi:lsi-core` 与 `lib:lsi:lsi-jimmer`
  - validated with:
    - `:lib:lsi:lsi-core:compileKotlin`
    - `:project:compiler:jimmer-ksp-ext:compileKotlin`
    - `:project:compiler:client:client-metadata-extractor:compileKotlin`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:jimmer-apt:compileJava`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches

- Latest completed slice on APT error metadata-first convergence:
  - `project/jimmer-apt/.../error/ErrorProcessor.java`
    - 不再回查 `TypeElement` 作为生成输入，改为直接消费 `ErrorTypeMetadata`
    - processor 侧新增 artifact 落盘，入口职责对齐到 orchestration + write
  - `project/jimmer-apt/.../error/ErrorGenerator.java`
    - 输入由 `TypeElement` 切换为 `ErrorTypeMetadata`
    - family / exception naming / field / enum item / doc 不再在 Java generator 内二次解析
    - Java generator 仅保留 JavaPoet 渲染边界，不再读注解镜像或元素树做语义推导
  - `project/compiler/error/error-metadata-extractor/.../ErrorMetadataExtractor.kt`
    - primitive + `list=true` 非法性从旧 APT Java generator 前移到 shared extractor，保持 metadata-first 路径的语义闭包
  - `project/compiler/error/error-metadata-extractor/.../ErrorMetadataExtractorTest.kt`
    - 新增 primitive-list 非法性单测，补回旧 generator 才覆盖到的错误分支
- Latest completed slice on APT diagnostics + immutable scan convergence:
  - `project/jimmer-apt/.../JimmerProcessor.java`
    - APT 主入口新增 shared LSI `MetaException` catch 与 `LsiDiagnosticAnchor -> Element` 回放
    - shared extractor 在 APT 入口抛出的 LSI 诊断现在可以重新落到 javac `Messager.printMessage(..., element)`，不再只剩纯字符串报错
  - `project/jimmer-apt/.../immutable/ImmutableProcessor.java`
    - `validateTopLevel` 改为基于 `lsiResolver.findClassesAnnotatedWith(...)` + `LsiClass.isTopLevel`
    - `parseImmutableTypes` 的候选扫描/过滤/接口与泛型/可见性校验改为复用 `immutable-metadata-extractor` 的 shared round-source extractor
    - processor 本地只保留 `qualifiedName -> TypeElement/ImmutableType` 的兼容回放，旧 Java generators 暂时保持不动
  - `project/jimmer-apt/build.gradle.kts`
    - APT 入口补充 `immutable-metadata-model/extractor` 依赖，允许 immutable 扫描阶段直接复用 shared metadata 模块
- Latest completed slice on APT immutable collected-source metadata convergence:
  - `project/compiler/immutable/immutable-metadata-extractor/.../ImmutableCollectedSourceMetadataResolution.kt`
    - 新增纯 `ImmutableSourceMetadata + LsiClass` 的 collected-source resolve 结果，补齐 APT/KSP 可共享的 source 聚合边界
    - shared resolve 不再直接泄漏 `site.addzero.lsi.jimmer.meta.ImmutableType`，避免 APT 为复用扫描/聚合逻辑而吞入 KSP-side immutable meta 大对象
  - `project/compiler/immutable/immutable-metadata-extractor/.../ImmutableCollectedSourceAccumulator.kt`
    - 新增 `resolveMetadata(...)`，将 source metadata 聚合前移到 shared extractor
    - 现有 `resolve(...)` 收缩为“共享 metadata resolve + KSP-specific ImmutableType 投影”两段式，保持 KSP `props/fetcher` metadata 生成语义不变
  - `project/jimmer-apt/.../immutable/ImmutableProcessor.java`
    - APT immutable 入口切到 `ImmutableCollectedSourceAccumulator.resolveMetadata(...)`
    - processor 不再自行重建 `sourceLsiClasses + sourceMetadataExtractor` 流程，只保留 `LsiClass -> TypeElement/ImmutableType` 的兼容回放与旧 Java generator 边界
    - `generateJimmerTypes` 的 source-level 编排输入现在统一来自 shared collected-source metadata resolve，APT/KSP 在 source 聚合层首次对齐到同一条流水线
  - validated with:
    - `:project:compiler:immutable:immutable-metadata-extractor:compileKotlin`
    - `:project:jimmer-apt:compileJava`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Latest completed slice on APT immutable processor API shrinking:
  - `project/jimmer-apt/.../immutable/ImmutableProcessor.java`
    - `process(...)` 对外返回面从 `Map<TypeElement, ImmutableType>` 缩小为 `Collection<TypeElement>`
    - `ImmutableType` 不再通过 processor API 泄漏到外部调用方，只保留在 processor 内部作为旧 Java generator 的兼容边界
    - `ParsedImmutableTypes` 内部额外缓存纯 `typeElements` 列表，避免 `JimmerProcessor` 再依赖 `keySet()` 这种历史实现细节
  - `project/jimmer-apt/.../JimmerProcessor.java`
    - immutable 主链改为直接消费 `new ImmutableProcessor(...).process()`
    - `EntryProcessor` 仍保持原行为，只拿 `TypeElement` 集合做入口文件与索引生成
  - validated with:
    - `:project:jimmer-apt:compileJava`
    - `:project:compiler:client:jimmer-ksp-client:compileKotlin`
    - `:project:compiler:dto:jimmer-ksp-dto:compileKotlin`
    - `:project:compiler:error:jimmer-ksp-error:compileKotlin`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches
- Latest completed slice on APT immutable internal state shrinking:
  - `project/jimmer-apt/.../immutable/ImmutableProcessor.java`
    - `ParsedImmutableTypes` 内部状态继续从 `Map<TypeElement, ImmutableType>` 收缩为 `Map<String, ImmutableType> + ImmutableCollectedSourceMetadataResolution`
    - `TypeElement` 解析被后移到 `process()` 返回边界，processor 内部不再长期持有 `TypeElement` 与 `ImmutableType` 混合状态
    - `typeElements` 去重语义改为 `LinkedHashSet` 后再转列表，保持旧 `keySet()` 路径的稳定去重行为
  - validated with:
    - `:project:jimmer-apt:compileJava`
    - `:project:jimmer-ksp:compileKotlin`
  - static guard rechecked:
    - `rg -n "^import com\\.google\\.devtools\\.ksp\\.|^import site\\.addzero\\.lsi\\.ksp\\.|\\btoKs\\w*\\b|\\btoAp\\w*\\b" project/compiler -g'*.kt' -g'*.java'`
    - no matches

### What is still not done

- APT client 入口已经切到 shared metadata pipeline，但这还不等于 APT 全域完成；目前真正完成对齐的是 `ExportDoc`、`Tx`、`TypedTuple`、`Client` 这几条入口线，APT immutable/error/dto 仍有各自的历史语义对象和生成器边界待继续收口。
- Wave 2/3 are still only partially complete in abstraction terms:
  - `DocMetadata` 已经返回 `LsiDoc`，但 client traversal 仍会在 runtime schema 落点把它转换为 `jimmer-core` `Doc`；文档 IR 还没有贯穿到更下游的 schema/materialization 层。
  - error/client/doc semantic annotations now read through LSI metadata, but the semantic model itself is still backed by existing jimmer runtime/meta objects instead of a shared LSI contract.
  - client metadata-first 已经落地，但 extractor 仍会先复用现有 client runtime traversal/materialization 生成 `Schema`，再投影成 `ClientSchemaMetadata`；generator 也仍会把 metadata 物化回 `jimmer-client` runtime schema，而不是先引入新的中立 schema IR。
  - client 模块虽然已经把纯语义/helper 和 metadata 模板收敛到 `site.addzero.lsi.jimmer.client` / `client-metadata-*`，并把 `ClientProcessor` 压缩成 orchestration-only，但 `ExportDocProcessor` 仍是处理器入口，尚未进一步拆出更中性的 orchestration/materialization 边界。
  - `ImmutablePropSemantics.kt` is a KSP-side stopgap semantic transplant, not the final shared LSI descriptor abstraction.
  - immutable generator layer虽然已经去掉 `Context`，主要 generator 类、source plan projection、artifact assembly、finish rebuild helper、finish bridge、entity-consumer 输入回放、finish-stage 单次解析、source-generation-plan API 面收口以及 source metadata 的 LSI-only 抽取也都已切到 metadata-first；并且已经补出 `immutable-metadata-model/extractor/generator` 模块骨架。`props` / `fetcher` / `implementor` / `associated-id` / `validation` / `builder` 六组 metadata 已经前移到 model/extractor，generator 私域桥接继续收窄。APT 侧目前只完成了 top-level 校验与 round-scan 的 shared extractor 接入，剩余耦合主要集中在 rebuilt `ImmutableType` 仍被用于 draft / impl / producer / draft-impl 这些类型或属性级投影，尚未进一步终止为更纯的 immutable metadata 输入。
- The next meaningful refactor target remains:
  - continue pushing `DocMetadata` / client-doc / error semantic metadata toward reusable LSI contracts
  - decide whether `ClientProcessor` / `ExportDocProcessor` should keep their current module-local orchestration role, or whether part of the remaining schema/materialization flow should be extracted before touching APT parity
  - continue shrinking processor-entry-local residual orchestration logic, now that old-package pure-LSI helpers in dto / immutable / error / tx / tuple have been收口
  - if immutable is continued next, continue from resolved aggregate里仍携带的 rebuilt `ImmutableType` big object itself toward更明确的 immutable metadata-model/extractor 边界
  - decide whether client traversal should first extract a neutral LSI schema graph before materializing `jimmer-client` runtime objects
  - decide whether `ClientExceptionContext` should produce a neutral LSI exception graph before adapting to `jimmer-client` runtime metadata
  - then decide whether `ImmutablePropSemantics` should stay compiler-local or be replaced by a true shared LSI descriptor layer
