package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.restdocs.domain.Document
import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import com.google.common.base.CaseFormat
import java.util.*
import java.util.regex.MatchResult
import java.util.regex.Matcher
import java.util.regex.Pattern

fun Field.toClassName(asInner: Boolean = false): String {
    var name = longName
    if (type is TypeSpec.ArraySpec && type.items is TypeSpec.ObjectSpec) {
        if (name.endsWith("List")) {
            name = name.substring(0, name.length - 4)
        } else if (name.endsWith("ies")) {
            name = name.replace("ies$".toRegex(), "y")
        } else if (name.endsWith("s") && !name.endsWith("ss")) {
            name = name.substring(0, name.length - 1)
        }
    }
    val className = fieldNameToClassStyle(name)
    return if (parentName != null && !className.startsWith(parentName) && !asInner) {
        parentName + className
    } else {
        className
    }
}

fun fieldNameToClassStyle(input: String): String {
    var input = input
    input = input.replace("ID".toRegex(), "Id")
    var classStyle: String = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, input)
    if (classStyle.contains('_')) {
        classStyle = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, classStyle)
    }
    classStyle = classStyle.replace("Id(s)?([A-Z].*)?$".toRegex(), "ID$1$2")
    return classStyle
}

fun fieldToClassStyle(field: Field, parentName: String? = null, asInner: Boolean = false): String {
    var name = field.longName
    if (field.type is TypeSpec.ArraySpec && field.type.items is TypeSpec.ObjectSpec) {
        if (name.endsWith("List")) {
            name = name.substring(0, name.length - 4)
        } else if (name.endsWith("ies")) {
            name = name.replace("ies$".toRegex(), "y")
        } else if (name.endsWith("s") && !name.endsWith("ss")) {
            name = name.substring(0, name.length - 1)
        }
    }
    val className = fieldNameToClassStyle(name)
    return if (parentName != null && !className.startsWith(parentName) && !asInner) {
        parentName + className
    } else {
        className
    }
}

fun toConstantStyle(input: String): String {
    var input = input
    input = input.replace("ID".toRegex(), "Id")
    var upperCase: String = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, input)
    // fix all-caps named items, e.g: URL or ID, which would otherwise become U_R_L and I_D
    val pattern = Pattern.compile("(?<start>^|_)(?<replace>([A-Z])((_)([A-Z]))+)(?<end>_|$)")
    val matcher = pattern.matcher(upperCase)
    upperCase = matcher.replaceAll { matchResult: MatchResult ->
        // give me access to names groups
        val wink = matchResult as Matcher
        wink.group("start") + wink.group("replace").replace("_", "") + wink.group("end")
    }
    return upperCase
}

fun convertToEnumConstantStyle(longName: String): String {
    return if (longName.matches("_-".toRegex())) {
        longName.uppercase(Locale.getDefault()).replace('-', '_')
    } else {
        toConstantStyle(longName)
    }
}
@Suppress("unused")
class ModelExts(private val mDocument: Document) {

//    fun fieldClass(field: Field, convertIntBoolean: Boolean = false): String? {
//        Objects.requireNonNull(field, "A field is required")
//        return getTypeString(field.type, field.isRequired, convertIntBoolean)
//    }
//
//    fun fieldInitializer(field: Field): String {
//        Objects.requireNonNull(field, "A field is required")
//        if (field.type === DataType.ARRAY) {
//            return " = new ArrayList<>()"
//        } else if (field.type === DataType.ENUM) {
//            return " = new FutureProofEnumContainer<>(" + fieldToClassStyle(field) + ".class)"
//        }
//        return ""
//    }

//    fun getTypeString(typeSpec: TypeSpec, required: Boolean): String? {
//        return getTypeString(typeSpec, required, false)
//    }

//    fun getTypeString(typeSpec: TypeSpec, required: Boolean, convertIntBoolean: Boolean): String? {
//        val type: DataType<*> = typeSpec.type
//        if (type != null) {
//            when (type) {
//                INT -> {
//                    if (convertIntBoolean && hasBooleanRestriction(typeSpec)) {
//                        return if (required) "boolean" else "Boolean"
//                    }
//                    return if (required) "int" else "Integer"
//                }
//
//                LONG -> return if (required) "long" else "Long"
//                FLOAT -> return if (required) "float" else "Float"
//                DOUBLE -> return if (required) "double" else "Double"
//                BOOLEAN -> return if (required) "boolean" else "Boolean"
//                OBJECT -> return if (typeSpec is Field) {
//                    val field = typeSpec as Field
//                    fieldToClassStyle(field)
//                } else {
//                    "Object"
//                }
//
//                STRING -> {
//                    return if (hasBooleanRestriction(typeSpec)) {
//                        if (required) "boolean" else "Boolean"
//                    } else "String"
//                }
//
//                DATE -> return "LocalDate"
//                COLLECTION -> return "Map<" + getKeyTypeString(typeSpec.key) + ", " + getTypeString(
//                    typeSpec.items,
//                    false,
//                    convertIntBoolean
//                ) + ">"
//
//                ENUM -> return if (typeSpec is Field) {
//                    val field = typeSpec as Field
//                    "FutureProofEnumContainer<" + fieldToClassStyle(field) + ">"
//                } else {
//                    throw IllegalStateException("Raw enum type specified, cannot generate name.")
//                }
//
//                ARRAY ->                    // pass required false, since we can't use primitives
//                    return "List<" + getTypeString(typeSpec.items, false, convertIntBoolean) + ">"
//
//                BITSET -> when (typeSpec.flagType) {
//                    INT -> return if (required) "int" else "Integer"
//                    LONG -> return if (required) "long" else "Long"
//                }
//            }
//        } else if (typeSpec.typeRef != null) {
//            val dataObject = mDocument.getDataObjectByName(typeSpec.typeRef)
//            val enumeration = mDocument.getEnumerationByName(typeSpec.typeRef)
//            check(!(dataObject == null && enumeration == null)) { "Type reference to undefined type: " + typeSpec.typeRef + "." }
//            return typeSpec.typeRef
//        }
//        return null
//    }

    fun toGetterStyle(input: String): String {
        return input.replace("Id$".toRegex(), "ID").replace("Ids$".toRegex(), "IDs")
    }


    fun fieldNameToClassStyle(input: String): String {
        var input = input
        input = input.replace("ID".toRegex(), "Id")
        var classStyle: String = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, input)
        classStyle = classStyle.replace("Id(s)?([A-Z].*)?$".toRegex(), "ID$1$2")
        return classStyle
    }


//    fun responseClass(requestClassName: String, response: Response?): String? {
//        Objects.requireNonNull(requestClassName, "A requestClassName is required")
//        if (response == null) {
//            return null
//        } else {
//            val type: TypeSpec = response.typeSpec
//            if (type != null) {
//                return if (type === DataType.OBJECT) {
//                    requestClassName.replace("Request$".toRegex(), "Response")
//                } else {
//                    // pass required false, since we can't use primitives
//                    getTypeString(response, false)
//                }
//            } else if (response.typeRef != null) {
//                return response.typeRef
//            }
//        }
//        return null
//    }
}
