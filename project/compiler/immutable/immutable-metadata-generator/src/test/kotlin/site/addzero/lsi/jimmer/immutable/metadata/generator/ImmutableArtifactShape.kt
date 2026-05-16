package site.addzero.lsi.jimmer.immutable.metadata.generator

import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiAnnotationUseSiteTarget
import site.addzero.lsi.poet.LsiArrayExpression
import site.addzero.lsi.poet.LsiArrayOfNullsExpression
import site.addzero.lsi.poet.LsiAssignmentStatement
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCallableReferenceExpression
import site.addzero.lsi.poet.LsiCastExpression
import site.addzero.lsi.poet.LsiClassLiteralExpression
import site.addzero.lsi.poet.LsiCodeBlock
import site.addzero.lsi.poet.LsiCodeExpression
import site.addzero.lsi.poet.LsiCollectionElementExpression
import site.addzero.lsi.poet.LsiCollectionSizeExpression
import site.addzero.lsi.poet.LsiEnumConstantExpression
import site.addzero.lsi.poet.LsiExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiForRangeStatement
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiIndexAccessExpression
import site.addzero.lsi.poet.LsiIntArrayExpression
import site.addzero.lsi.poet.LsiJavaClassExpression
import site.addzero.lsi.poet.LsiLambdaTypeName
import site.addzero.lsi.poet.LsiLambdaExpression
import site.addzero.lsi.poet.LsiLengthExpression
import site.addzero.lsi.poet.LsiListExpression
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiMakeIdOnlyExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiPropertyGetExpression
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiPropertySetStatement
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiSafeCastExpression
import site.addzero.lsi.poet.LsiStatement
import site.addzero.lsi.poet.LsiSuperExpression
import site.addzero.lsi.poet.LsiThisExpression
import site.addzero.lsi.poet.LsiThrowStatement
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.LsiTypeVariableName
import site.addzero.lsi.poet.LsiTryStatement
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiVarargExpression
import site.addzero.lsi.poet.LsiVariableDeclarationStatement
import site.addzero.lsi.poet.LsiWildcardTypeName
import site.addzero.lsi.poet.LsiWhenStatement
import site.addzero.lsi.poet.findKotlinOnlyRawCode

internal data class ImmutableArtifactShape(
    val qualifiedName: String,
    val role: ImmutableArtifactRole,
    val memberImportCount: Int,
    val topLevelPropertyCount: Int,
    val topLevelCallableCount: Int,
    val lambdaTypePaths: List<String>,
    val unsupportedUseSiteTargetPaths: List<String>,
    val extensionMemberPaths: List<String>,
    val rawCodePaths: List<String>,
    val objectTypePaths: List<String>,
) {

    fun kotlinSidecarRetainedBlockers(): List<String> =
        javaBoundaryBlockers().filter { it in KOTLIN_SIDECAR_RETAINED_BLOCKERS }

    fun pendingJavaNormalizationBlockers(): List<String> =
        javaBoundaryBlockers().filterNot { it in KOTLIN_SIDECAR_RETAINED_BLOCKERS }

    fun javaBoundaryBlockers(): List<String> =
        buildList {
            if (memberImportCount > 0) {
                add("member imports")
            }
            if (topLevelPropertyCount > 0) {
                add("top-level properties")
            }
            if (topLevelCallableCount > 0) {
                add("top-level callables")
            }
            if (lambdaTypePaths.isNotEmpty()) {
                add("LsiLambdaTypeName")
            }
            if (unsupportedUseSiteTargetPaths.isNotEmpty()) {
                add("unsupported use-site targets")
            }
            if (extensionMemberPaths.isNotEmpty()) {
                add("extension members")
            }
            if (rawCodePaths.isNotEmpty()) {
                add("Kotlin-only raw code")
            }
            if (objectTypePaths.isNotEmpty()) {
                add("LsiTypeSpecKind.OBJECT")
            }
        }

    fun describe(): String =
        buildString {
            appendLine("qualifiedName=$qualifiedName")
            appendLine("role=$role")
            appendLine("memberImportCount=$memberImportCount")
            appendLine("topLevelPropertyCount=$topLevelPropertyCount")
            appendLine("topLevelCallableCount=$topLevelCallableCount")
            appendLine("lambdaTypePaths=")
            for (path in lambdaTypePaths) {
                appendLine(path)
            }
            appendLine("unsupportedUseSiteTargetPaths=")
            for (path in unsupportedUseSiteTargetPaths) {
                appendLine(path)
            }
            appendLine("extensionMemberPaths=")
            for (path in extensionMemberPaths) {
                appendLine(path)
            }
            appendLine("rawCodePaths=")
            for (path in rawCodePaths) {
                appendLine(path)
            }
            appendLine("objectTypePaths=")
            for (path in objectTypePaths) {
                appendLine(path)
            }
        }

    companion object {
        fun from(fileSpec: LsiFileSpec): ImmutableArtifactShape =
            ImmutableArtifactShape(
                qualifiedName = fileSpec.qualifiedName,
                role = fileSpec.immutableArtifactRole(),
                memberImportCount = fileSpec.memberImports.size,
                topLevelPropertyCount = fileSpec.topLevelProperties.size,
                topLevelCallableCount = fileSpec.topLevelCallables.size,
                lambdaTypePaths = buildList {
                    fileSpec.topLevelProperties.forEach { property ->
                        addAll(property.lambdaTypePaths("file '${fileSpec.qualifiedName}' -> top-level property '${property.name}'"))
                    }
                    fileSpec.topLevelCallables.forEach { callable ->
                        addAll(callable.lambdaTypePaths("file '${fileSpec.qualifiedName}' -> top-level callable '${callable.name}'"))
                    }
                    fileSpec.types.forEach { type ->
                        addAll(type.lambdaTypePaths("file '${fileSpec.qualifiedName}' -> type '${type.name}'"))
                    }
                },
                unsupportedUseSiteTargetPaths = buildList {
                    fileSpec.annotations.forEachIndexed { index, annotation ->
                        addAll(annotation.unsupportedUseSiteTargetPaths("file '${fileSpec.qualifiedName}' annotation[$index]"))
                    }
                    fileSpec.topLevelProperties.forEach { property ->
                        addAll(property.unsupportedUseSiteTargetPaths("file '${fileSpec.qualifiedName}' -> top-level property '${property.name}'"))
                    }
                    fileSpec.topLevelCallables.forEach { callable ->
                        addAll(callable.unsupportedUseSiteTargetPaths("file '${fileSpec.qualifiedName}' -> top-level callable '${callable.name}'"))
                    }
                    fileSpec.types.forEach { type ->
                        addAll(type.unsupportedUseSiteTargetPaths("file '${fileSpec.qualifiedName}' -> type '${type.name}'"))
                    }
                },
                extensionMemberPaths = buildList {
                    fileSpec.topLevelProperties.forEach { property ->
                        addAll(property.extensionMemberPaths("file '${fileSpec.qualifiedName}' -> top-level property '${property.name}'"))
                    }
                    fileSpec.topLevelCallables.forEach { callable ->
                        addAll(callable.extensionMemberPaths("file '${fileSpec.qualifiedName}' -> top-level callable '${callable.name}'"))
                    }
                    fileSpec.types.forEach { type ->
                        addAll(type.extensionMemberPaths("file '${fileSpec.qualifiedName}' -> type '${type.name}'"))
                    }
                },
                rawCodePaths = buildList {
                    fileSpec.topLevelProperties.forEach { property ->
                        addAll(property.rawCodePaths("file '${fileSpec.qualifiedName}' -> top-level property '${property.name}'"))
                    }
                    fileSpec.topLevelCallables.forEach { callable ->
                        addAll(callable.rawCodePaths("file '${fileSpec.qualifiedName}' -> top-level callable '${callable.name}'"))
                    }
                    fileSpec.types.forEach { type ->
                        addAll(type.rawCodePaths("file '${fileSpec.qualifiedName}' -> type '${type.name}'"))
                    }
                },
                objectTypePaths = buildList {
                    fileSpec.types.forEach { type ->
                        addAll(type.objectTypePaths("file '${fileSpec.qualifiedName}' -> type '${type.name}'"))
                    }
                },
            )
    }
}

private val KOTLIN_SIDECAR_RETAINED_BLOCKERS = setOf(
    "top-level properties",
    "top-level callables",
    "LsiLambdaTypeName",
    "extension members",
)

private fun LsiTypeSpec.objectTypePaths(path: String): List<String> =
    buildList {
        if (kind == LsiTypeSpecKind.OBJECT) {
            add(path)
        }
        nestedTypes.forEach { nestedType ->
            addAll(nestedType.objectTypePaths("$path type '${nestedType.name}'"))
        }
    }

private fun LsiTypeSpec.lambdaTypePaths(path: String): List<String> =
    buildList {
        superClass?.collectLambdaTypePaths("$path superClass", this)
        superInterfaces.forEachIndexed { index, type ->
            type.collectLambdaTypePaths("$path superInterface[$index]", this)
        }
        superTypes.forEachIndexed { index, type ->
            type.collectLambdaTypePaths("$path superType[$index]", this)
        }
        typeVariables.forEachIndexed { index, typeVariable ->
            typeVariable.collectLambdaTypePaths("$path typeVariable[$index]", this)
        }
        properties.forEach { property ->
            addAll(property.lambdaTypePaths("$path property '${property.name}'"))
        }
        callables.forEach { callable ->
            addAll(callable.lambdaTypePaths("$path callable '${callable.name}'"))
        }
        nestedTypes.forEach { nestedType ->
            addAll(nestedType.lambdaTypePaths("$path type '${nestedType.name}'"))
        }
    }

private fun LsiTypeSpec.unsupportedUseSiteTargetPaths(path: String): List<String> =
    buildList {
        annotations.forEachIndexed { index, annotation ->
            addAll(annotation.unsupportedUseSiteTargetPaths("$path annotation[$index]"))
        }
        properties.forEach { property ->
            addAll(property.unsupportedUseSiteTargetPaths("$path property '${property.name}'"))
        }
        callables.forEach { callable ->
            addAll(callable.unsupportedUseSiteTargetPaths("$path callable '${callable.name}'"))
        }
        nestedTypes.forEach { nestedType ->
            addAll(nestedType.unsupportedUseSiteTargetPaths("$path type '${nestedType.name}'"))
        }
    }

private fun LsiTypeSpec.extensionMemberPaths(path: String): List<String> =
    buildList {
        properties.forEach { property ->
            addAll(property.extensionMemberPaths("$path property '${property.name}'"))
        }
        callables.forEach { callable ->
            addAll(callable.extensionMemberPaths("$path callable '${callable.name}'"))
        }
        nestedTypes.forEach { nestedType ->
            addAll(nestedType.extensionMemberPaths("$path type '${nestedType.name}'"))
        }
    }

private fun LsiTypeSpec.rawCodePaths(path: String): List<String> =
    buildList {
        properties.forEach { property ->
            addAll(property.rawCodePaths("$path property '${property.name}'"))
        }
        callables.forEach { callable ->
            addAll(callable.rawCodePaths("$path callable '${callable.name}'"))
        }
        nestedTypes.forEach { nestedType ->
            addAll(nestedType.rawCodePaths("$path type '${nestedType.name}'"))
        }
    }

private fun LsiPropertySpec.lambdaTypePaths(path: String): List<String> =
    buildList {
        receiverType?.collectLambdaTypePaths("$path receiver", this)
        type.collectLambdaTypePaths("$path type", this)
    }

private fun LsiPropertySpec.unsupportedUseSiteTargetPaths(path: String): List<String> =
    buildList {
        val allowedUseSiteTargets =
            if (shouldRenderAsJavaAccessor()) {
                setOf(
                    LsiAnnotationUseSiteTarget.GET,
                    LsiAnnotationUseSiteTarget.SET,
                    LsiAnnotationUseSiteTarget.FIELD,
                )
            } else {
                setOf(LsiAnnotationUseSiteTarget.FIELD)
            }
        annotations.forEachIndexed { index, annotation ->
            if (annotation.useSiteTarget != null && annotation.useSiteTarget !in allowedUseSiteTargets) {
                add("$path annotation[$index]")
            }
        }
    }

private fun LsiPropertySpec.extensionMemberPaths(path: String): List<String> =
    listOfNotNull(path.takeIf { receiverType != null })

private fun LsiPropertySpec.rawCodePaths(path: String): List<String> =
    buildList {
        if (initializer?.hasKotlinOnlyRawCodeBlocker() == true) {
            add("$path initializer")
        }
        getterStatements.forEachIndexed { index, statement ->
            if (statement.hasKotlinOnlyRawCodeBlocker()) {
                add("$path getterStatements[$index]")
            }
        }
        setterStatements.forEachIndexed { index, statement ->
            if (statement.hasKotlinOnlyRawCodeBlocker()) {
                add("$path setterStatements[$index]")
            }
        }
    }

private fun LsiCallableSpec.lambdaTypePaths(path: String): List<String> =
    buildList {
        receiverType?.collectLambdaTypePaths("$path receiver", this)
        returnType?.collectLambdaTypePaths("$path returnType", this)
        typeVariables.forEachIndexed { index, typeVariable ->
            typeVariable.collectLambdaTypePaths("$path typeVariable[$index]", this)
        }
        parameters.forEach { parameter ->
            parameter.type.collectLambdaTypePaths("$path parameter '${parameter.name}' type", this)
        }
        thrownTypes.forEachIndexed { index, type ->
            type.collectLambdaTypePaths("$path thrownType[$index]", this)
        }
    }

private fun LsiCallableSpec.unsupportedUseSiteTargetPaths(path: String): List<String> =
    buildList {
        annotations.forEachIndexed { index, annotation ->
            addAll(annotation.unsupportedUseSiteTargetPaths("$path annotation[$index]"))
        }
        parameters.forEach { parameter ->
            parameter.annotations.forEachIndexed { index, annotation ->
                addAll(annotation.unsupportedUseSiteTargetPaths("$path parameter '${parameter.name}' annotation[$index]"))
            }
        }
    }

private fun LsiCallableSpec.extensionMemberPaths(path: String): List<String> =
    listOfNotNull(path.takeIf { receiverType != null })

private fun LsiCallableSpec.rawCodePaths(path: String): List<String> =
    buildList {
        parameters.forEach { parameter ->
            if (parameter.defaultValue?.hasKotlinOnlyRawCodeBlocker() == true) {
                add("$path parameter '${parameter.name}' defaultValue")
            }
        }
        delegateCall?.arguments?.forEachIndexed { index, expression ->
            if (expression.hasKotlinOnlyRawCodeBlocker()) {
                add("$path delegateCall argument[$index]")
            }
        }
        statements.forEachIndexed { index, statement ->
            if (statement.hasKotlinOnlyRawCodeBlocker()) {
                add("$path statements[$index]")
            }
        }
    }

private fun LsiAnnotationSpec.unsupportedUseSiteTargetPaths(path: String): List<String> =
    listOfNotNull(path.takeIf { useSiteTarget != null })

private fun LsiTypeName.collectLambdaTypePaths(path: String, sink: MutableList<String>) {
    when (this) {
        is LsiLambdaTypeName -> {
            sink += path
            receiverType?.collectLambdaTypePaths("$path receiver", sink)
            parameterTypes.forEachIndexed { index, parameterType ->
                parameterType.collectLambdaTypePaths("$path parameter[$index]", sink)
            }
            returnType.collectLambdaTypePaths("$path returnType", sink)
        }
        is LsiParameterizedTypeName -> {
            rawType.collectLambdaTypePaths("$path rawType", sink)
            typeArguments.forEachIndexed { index, typeArgument ->
                typeArgument.collectLambdaTypePaths("$path typeArgument[$index]", sink)
            }
        }
        is LsiTypeVariableName -> {
            bounds.forEachIndexed { index, bound ->
                bound.collectLambdaTypePaths("$path bound[$index]", sink)
            }
        }
        is LsiWildcardTypeName -> {
            producerTypes.forEachIndexed { index, type ->
                type.collectLambdaTypePaths("$path producer[$index]", sink)
            }
            consumerTypes.forEachIndexed { index, type ->
                type.collectLambdaTypePaths("$path consumer[$index]", sink)
            }
        }
        else -> {}
    }
}

private fun LsiStatement.hasKotlinOnlyRawCodeBlocker(): Boolean =
    when (this) {
        is LsiAssignmentStatement ->
            target.hasKotlinOnlyRawCodeBlocker() || expression.hasKotlinOnlyRawCodeBlocker()
        is LsiExpressionStatement -> expression.hasKotlinOnlyRawCodeBlocker()
        is LsiIfStatement ->
            condition.hasKotlinOnlyRawCodeBlocker() ||
                thenStatements.any(LsiStatement::hasKotlinOnlyRawCodeBlocker) ||
                elseStatements.any(LsiStatement::hasKotlinOnlyRawCodeBlocker)
        is LsiTryStatement ->
            tryStatements.any(LsiStatement::hasKotlinOnlyRawCodeBlocker) ||
                finallyStatements.any(LsiStatement::hasKotlinOnlyRawCodeBlocker)
        is LsiForRangeStatement ->
            from.hasKotlinOnlyRawCodeBlocker() ||
                until.hasKotlinOnlyRawCodeBlocker() ||
                statements.any(LsiStatement::hasKotlinOnlyRawCodeBlocker)
        is LsiPropertySetStatement ->
            receiver.hasKotlinOnlyRawCodeBlocker() || expression.hasKotlinOnlyRawCodeBlocker()
        is LsiReturnStatement -> expression?.hasKotlinOnlyRawCodeBlocker() == true
        is LsiThrowStatement -> expression.hasKotlinOnlyRawCodeBlocker()
        is LsiVariableDeclarationStatement -> initializer.hasKotlinOnlyRawCodeBlocker()
        is LsiWhenStatement ->
            subject.hasKotlinOnlyRawCodeBlocker() ||
                cases.any { case ->
                    case.conditions.any(LsiExpression::hasKotlinOnlyRawCodeBlocker) ||
                        case.statements.any(LsiStatement::hasKotlinOnlyRawCodeBlocker)
                } ||
                elseStatements.any(LsiStatement::hasKotlinOnlyRawCodeBlocker)
    }

private fun LsiExpression.hasKotlinOnlyRawCodeBlocker(): Boolean =
    when (this) {
        is LsiArrayExpression -> elements.any(LsiExpression::hasKotlinOnlyRawCodeBlocker)
        is LsiIntArrayExpression -> elements.any(LsiExpression::hasKotlinOnlyRawCodeBlocker)
        is LsiArrayOfNullsExpression -> size.hasKotlinOnlyRawCodeBlocker()
        is LsiBinaryExpression ->
            left.hasKotlinOnlyRawCodeBlocker() || right.hasKotlinOnlyRawCodeBlocker()
        is LsiCallableReferenceExpression -> receiver.hasKotlinOnlyRawCodeBlocker()
        is LsiCallExpression ->
            receiver?.hasKotlinOnlyRawCodeBlocker() == true ||
                arguments.any(LsiExpression::hasKotlinOnlyRawCodeBlocker)
        is LsiCollectionElementExpression ->
            receiver.hasKotlinOnlyRawCodeBlocker() || index.hasKotlinOnlyRawCodeBlocker()
        is LsiCollectionSizeExpression -> receiver.hasKotlinOnlyRawCodeBlocker()
        is LsiCastExpression -> expression.hasKotlinOnlyRawCodeBlocker()
        is LsiSafeCastExpression -> expression.hasKotlinOnlyRawCodeBlocker()
        is LsiCodeExpression -> code.hasKotlinOnlyRawCodeBlocker()
        is LsiIndexAccessExpression ->
            receiver.hasKotlinOnlyRawCodeBlocker() || index.hasKotlinOnlyRawCodeBlocker()
        is LsiLengthExpression -> receiver.hasKotlinOnlyRawCodeBlocker()
        is LsiLambdaExpression ->
            expression?.hasKotlinOnlyRawCodeBlocker() == true ||
                statements.any(LsiStatement::hasKotlinOnlyRawCodeBlocker)
        is LsiListExpression -> elements.any(LsiExpression::hasKotlinOnlyRawCodeBlocker)
        is LsiMakeIdOnlyExpression -> idExpression.hasKotlinOnlyRawCodeBlocker()
        is LsiNewExpression -> arguments.any(LsiExpression::hasKotlinOnlyRawCodeBlocker)
        is LsiPropertyGetExpression -> receiver.hasKotlinOnlyRawCodeBlocker()
        is LsiPropertyAccessExpression -> receiver.hasKotlinOnlyRawCodeBlocker()
        is LsiVarargExpression -> expression.hasKotlinOnlyRawCodeBlocker()
        is LsiJavaClassExpression,
        is LsiTypeExpression,
        is LsiClassLiteralExpression,
        is LsiEnumConstantExpression,
        is LsiLiteralExpression,
        is LsiNameExpression,
        LsiNullExpression,
        LsiSuperExpression,
        LsiThisExpression -> false
    }

private fun LsiCodeBlock.hasKotlinOnlyRawCodeBlocker(): Boolean =
    findKotlinOnlyRawCode() != null ||
        args.any { arg ->
            when (arg) {
                is LsiCodeBlock -> arg.hasKotlinOnlyRawCodeBlocker()
                is LsiExpression -> arg.hasKotlinOnlyRawCodeBlocker()
                else -> false
            }
        }

private fun LsiPropertySpec.shouldRenderAsJavaAccessor(): Boolean =
    !modifiers.contains(LsiModifier.STATIC) && (
        getterStatements.isNotEmpty() ||
            setterStatements.isNotEmpty() ||
            modifiers.contains(LsiModifier.ABSTRACT) ||
            modifiers.contains(LsiModifier.OVERRIDE) ||
            !modifiers.contains(LsiModifier.PRIVATE)
    )
