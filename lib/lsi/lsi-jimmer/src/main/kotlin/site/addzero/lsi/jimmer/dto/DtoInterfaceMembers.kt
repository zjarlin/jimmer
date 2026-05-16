@file:JvmName("DtoInterfaceMembersSupport")

package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.dto.compiler.DtoType
import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.dto.LsiDtoBaseProp
import site.addzero.lsi.dto.LsiDtoBaseType
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.poet.isLsiBooleanLikeQualifiedName
import site.addzero.lsi.poet.isLsiObjectLikeQualifiedName
import site.addzero.lsi.poet.isLsiVoidLikeQualifiedName
import site.addzero.lsi.resolver.LsiResolver

data class DtoInterfaceMembers(
    val methodNames: Set<String>,
    val propertyNames: Set<String>,
)

fun <T : LsiDtoBaseType, P : LsiDtoBaseProp> analyzeDtoInterfaceMembers(
    resolver: LsiResolver,
    dtoType: DtoType<T, P>,
): DtoInterfaceMembers {
    if (dtoType.superInterfaces.isEmpty()) {
        return DtoInterfaceMembers(emptySet(), emptySet())
    }
    val methodNames = linkedSetOf<String>()
    val propertyNames = linkedSetOf<String>()
    val handledTypeNames = mutableSetOf<String>()
    for (typeRef in dtoType.superInterfaces) {
        val declaration = resolver.findClassByQualifiedName(typeRef.typeName)
            ?: error("Internal bug: super interface \"${typeRef.typeName}\" does not exists")
        if (!declaration.isInterface) {
            throw DtoAstException(
                dtoType.dtoFile,
                typeRef.line,
                typeRef.col,
                "The super type \"${typeRef.typeName}\" is not interface"
            )
        }
        collectMembers(
            declaration = declaration,
            handledTypeNames = handledTypeNames,
            methodNames = methodNames,
            propertyNames = propertyNames,
        )
    }
    return DtoInterfaceMembers(
        methodNames = methodNames,
        propertyNames = propertyNames,
    )
}

private fun collectMembers(
    declaration: LsiClass,
    handledTypeNames: MutableSet<String>,
    methodNames: MutableSet<String>,
    propertyNames: MutableSet<String>,
) {
    val qualifiedName = declaration.qualifiedName ?: declaration.simpleName ?: return
    if (!handledTypeNames.add(qualifiedName)) {
        return
    }
    for (method in declaration.methods) {
        val methodName = method.name ?: continue
        if (method.isStatic) {
            continue
        }
        if (method.typeParameterCount != 0) {
            throw MetaException(
                method,
                "Illegal abstract method, the declaring interface \"" +
                    qualifiedName +
                    "\" or its derived interface is used as the super interface of generated DTO type " +
                    "so that this abstract method cannot have generic parameters",
                null,
            )
        }
        when {
            methodName == "hashCode" && method.parameters.isEmpty() -> continue
            methodName == "equals" &&
                method.parameters.size == 1 &&
                isObjectLikeType(method.parameters[0].typeName) -> continue
            methodName == "toString" && method.parameters.isEmpty() -> continue
        }
        val propName = dtoPropertyName(method)
            ?: throw MetaException(
                method,
                "Illegal abstract method, the declaring interface \"" +
                    qualifiedName +
                    "\" or its derived interface is used as the super interface of generated DTO type " +
                    "but this abstract method can be consider as neither getter and setter",
                null,
            )
        if (propName.firstOrNull()?.isDigit() == true) {
            throw MetaException(
                method,
                "The property name \"" +
                    propName +
                    "\", its first character cannot be digit",
                null,
            )
        }
        methodNames += methodName
        propertyNames += propName
    }
    for (superType in declaration.interfaces) {
        collectMembers(
            declaration = superType,
            handledTypeNames = handledTypeNames,
            methodNames = methodNames,
            propertyNames = propertyNames,
        )
    }
}

private fun dtoPropertyName(method: LsiMethod): String? {
    val methodName = method.name ?: return null
    var propName = StringUtil.propName(methodName, isBooleanType(method.returnTypeName))
    if (propName != null) {
        if (method.parameters.isNotEmpty() || isVoidLike(method.returnTypeName)) {
            propName = null
        }
    } else if (
        method.parameters.size == 1 &&
        isVoidLike(method.returnTypeName) &&
        methodName.startsWith("set") &&
        methodName.length > 3 &&
        methodName[3].isUpperCase()
    ) {
        propName = StringUtil.identifier(methodName.substring(3))
    }
    return propName
}

private fun isBooleanType(typeName: String?): Boolean =
    typeName.isLsiBooleanLikeQualifiedName()

private fun isVoidLike(typeName: String?): Boolean =
    typeName.isLsiVoidLikeQualifiedName()

private fun isObjectLikeType(typeName: String?): Boolean =
    typeName.isLsiObjectLikeQualifiedName()
