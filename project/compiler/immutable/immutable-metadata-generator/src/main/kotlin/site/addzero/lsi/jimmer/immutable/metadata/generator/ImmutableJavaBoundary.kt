package site.addzero.lsi.jimmer.immutable.metadata.generator

import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiFileSpec
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
import site.addzero.lsi.poet.LsiThrowStatement
import site.addzero.lsi.poet.LsiThisExpression
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTryStatement
import site.addzero.lsi.poet.LsiVarargExpression
import site.addzero.lsi.poet.LsiVariableDeclarationStatement
import site.addzero.lsi.poet.LsiWhenStatement
import site.addzero.lsi.poet.LsiTypeVariableName
import site.addzero.lsi.poet.LsiWildcardTypeName
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.findKotlinOnlyRawCode

internal enum class ImmutableArtifactRole {
    JAVA_SHARED,
    KOTLIN_SIDECAR,
}

internal fun LsiFileSpec.immutableArtifactRole(): ImmutableArtifactRole =
    if (javaBoundaryBlockers().isEmpty()) {
        ImmutableArtifactRole.JAVA_SHARED
    } else {
        ImmutableArtifactRole.KOTLIN_SIDECAR
    }

internal fun LsiFileSpec.javaBoundaryBlockers(): List<String> =
    buildList {
        if (memberImports.isNotEmpty()) {
            add("member imports")
        }
        if (topLevelProperties.isNotEmpty()) {
            add("top-level properties")
        }
        if (topLevelCallables.isNotEmpty()) {
            add("top-level callables")
        }
        if (hasLambdaTypeBlocker()) {
            add("LsiLambdaTypeName")
        }
        if (hasUnsupportedUseSiteTargetBlocker()) {
            add("unsupported use-site targets")
        }
        if (hasExtensionMemberBlocker()) {
            add("extension members")
        }
        if (hasKotlinOnlyRawCodeBlocker()) {
            add("Kotlin-only raw code")
        }
        if (types.any(LsiTypeSpec::hasObjectTypeBlocker)) {
            add("LsiTypeSpecKind.OBJECT")
        }
    }

private fun LsiFileSpec.hasLambdaTypeBlocker(): Boolean =
    topLevelProperties.any(LsiPropertySpec::hasLambdaTypeBlocker) ||
        topLevelCallables.any(LsiCallableSpec::hasLambdaTypeBlocker) ||
        types.any(LsiTypeSpec::hasLambdaTypeBlocker)

private fun LsiFileSpec.hasUnsupportedUseSiteTargetBlocker(): Boolean =
    annotations.any(LsiAnnotationSpec::hasUnsupportedUseSiteTargetBlocker) ||
        topLevelProperties.any(LsiPropertySpec::hasUnsupportedUseSiteTargetBlocker) ||
        topLevelCallables.any(LsiCallableSpec::hasUnsupportedUseSiteTargetBlocker) ||
        types.any(LsiTypeSpec::hasUnsupportedUseSiteTargetBlocker)

private fun LsiFileSpec.hasExtensionMemberBlocker(): Boolean =
    topLevelProperties.any(LsiPropertySpec::hasExtensionMemberBlocker) ||
        topLevelCallables.any(LsiCallableSpec::hasExtensionMemberBlocker) ||
        types.any(LsiTypeSpec::hasExtensionMemberBlocker)

private fun LsiFileSpec.hasKotlinOnlyRawCodeBlocker(): Boolean =
    topLevelProperties.any(LsiPropertySpec::hasKotlinOnlyRawCodeBlocker) ||
        topLevelCallables.any(LsiCallableSpec::hasKotlinOnlyRawCodeBlocker) ||
        types.any(LsiTypeSpec::hasKotlinOnlyRawCodeBlocker)

private fun LsiTypeSpec.hasObjectTypeBlocker(): Boolean =
    kind == LsiTypeSpecKind.OBJECT || nestedTypes.any(LsiTypeSpec::hasObjectTypeBlocker)

private fun LsiTypeSpec.hasLambdaTypeBlocker(): Boolean =
    superClass?.hasLambdaTypeBlocker() == true ||
        superInterfaces.any(LsiTypeName::hasLambdaTypeBlocker) ||
        superTypes.any(LsiTypeName::hasLambdaTypeBlocker) ||
        typeVariables.any(LsiTypeVariableName::hasLambdaTypeBlocker) ||
        properties.any(LsiPropertySpec::hasLambdaTypeBlocker) ||
        callables.any(LsiCallableSpec::hasLambdaTypeBlocker) ||
        nestedTypes.any(LsiTypeSpec::hasLambdaTypeBlocker)

private fun LsiTypeSpec.hasUnsupportedUseSiteTargetBlocker(): Boolean =
    annotations.any(LsiAnnotationSpec::hasUnsupportedUseSiteTargetBlocker) ||
        properties.any(LsiPropertySpec::hasUnsupportedUseSiteTargetBlocker) ||
        callables.any(LsiCallableSpec::hasUnsupportedUseSiteTargetBlocker) ||
        nestedTypes.any(LsiTypeSpec::hasUnsupportedUseSiteTargetBlocker)

private fun LsiTypeSpec.hasExtensionMemberBlocker(): Boolean =
    properties.any(LsiPropertySpec::hasExtensionMemberBlocker) ||
        callables.any(LsiCallableSpec::hasExtensionMemberBlocker) ||
        nestedTypes.any(LsiTypeSpec::hasExtensionMemberBlocker)

private fun LsiTypeSpec.hasKotlinOnlyRawCodeBlocker(): Boolean =
    properties.any(LsiPropertySpec::hasKotlinOnlyRawCodeBlocker) ||
        callables.any(LsiCallableSpec::hasKotlinOnlyRawCodeBlocker) ||
        nestedTypes.any(LsiTypeSpec::hasKotlinOnlyRawCodeBlocker)

private fun LsiPropertySpec.hasLambdaTypeBlocker(): Boolean =
    receiverType?.hasLambdaTypeBlocker() == true || type.hasLambdaTypeBlocker()

private fun LsiPropertySpec.hasExtensionMemberBlocker(): Boolean =
    receiverType != null

private fun LsiPropertySpec.hasUnsupportedUseSiteTargetBlocker(): Boolean {
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
    return annotations.any { annotation ->
        annotation.useSiteTarget != null && annotation.useSiteTarget !in allowedUseSiteTargets
    }
}

private fun LsiPropertySpec.hasKotlinOnlyRawCodeBlocker(): Boolean =
    initializer?.hasKotlinOnlyRawCodeBlocker() == true ||
        getterStatements.any(LsiStatement::hasKotlinOnlyRawCodeBlocker) ||
        setterStatements.any(LsiStatement::hasKotlinOnlyRawCodeBlocker)

private fun LsiCallableSpec.hasLambdaTypeBlocker(): Boolean =
    receiverType?.hasLambdaTypeBlocker() == true ||
        returnType?.hasLambdaTypeBlocker() == true ||
        typeVariables.any(LsiTypeVariableName::hasLambdaTypeBlocker) ||
        parameters.any { it.type.hasLambdaTypeBlocker() } ||
        thrownTypes.any(LsiTypeName::hasLambdaTypeBlocker)

private fun LsiCallableSpec.hasUnsupportedUseSiteTargetBlocker(): Boolean =
    annotations.any(LsiAnnotationSpec::hasUnsupportedUseSiteTargetBlocker) ||
        parameters.any { parameter ->
            parameter.annotations.any(LsiAnnotationSpec::hasUnsupportedUseSiteTargetBlocker)
        }

private fun LsiCallableSpec.hasExtensionMemberBlocker(): Boolean =
    receiverType != null

private fun LsiCallableSpec.hasKotlinOnlyRawCodeBlocker(): Boolean =
    parameters.any { it.defaultValue?.hasKotlinOnlyRawCodeBlocker() == true } ||
        delegateCall?.arguments?.any(LsiExpression::hasKotlinOnlyRawCodeBlocker) == true ||
        statements.any(LsiStatement::hasKotlinOnlyRawCodeBlocker)

private fun LsiTypeVariableName.hasLambdaTypeBlocker(): Boolean =
    bounds.any(LsiTypeName::hasLambdaTypeBlocker)

private fun LsiAnnotationSpec.hasUnsupportedUseSiteTargetBlocker(): Boolean =
    useSiteTarget != null

private fun LsiTypeName.hasLambdaTypeBlocker(): Boolean =
    when (this) {
        is LsiLambdaTypeName -> true
        is LsiParameterizedTypeName ->
            rawType.hasLambdaTypeBlocker() || typeArguments.any(LsiTypeName::hasLambdaTypeBlocker)
        is LsiTypeVariableName -> hasLambdaTypeBlocker()
        is LsiWildcardTypeName ->
            producerTypes.any(LsiTypeName::hasLambdaTypeBlocker) ||
                consumerTypes.any(LsiTypeName::hasLambdaTypeBlocker)
        else -> false
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
