package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.restdocs.domain.DataObject
import com.giffardtechnologies.restdocs.domain.Document
import com.giffardtechnologies.restdocs.domain.type.TypeSpec

/**
 * How a [DataObject] is used across the service.
 *
 * Used by [DataObjectUsageClassifier] to decide whether to suppress constructor defaults
 * and/or generate a companion `FooInput` class without defaults.
 */
enum class DataObjectClassification {
    /** Used only in response types — generate with constructor defaults. */
    ResponseOnly,
    /** Used only in parameter types — generate without constructor defaults. */
    ParameterOnly,
    /** Used in both contexts — generate `Foo` with defaults and `FooInput` without. */
    Mixed,
}

/**
 * Analyses the document's service methods to classify each [DataObject]
 * by how it is used: on the parameter side, the response side, or both.
 *
 * The traversal walks [com.giffardtechnologies.restdocs.domain.Method.parameters] (already-flattened fields)
 * and recurses transitively through any [TypeSpec.TypeRefSpec]
 * it encounters. Include-elements are not walked separately because their fields are already inlined into
 * the flat parameter list and will be seen during that traversal.
 */
class DataObjectUsageClassifier(document: Document) {

    private val parameterUsed = mutableSetOf<String>()
    private val responseUsed = mutableSetOf<String>()

    init {
        document.service?.let { service ->
            service.common?.parameters?.forEach { field ->
                walkParamContext(field.type)
            }
            service.methods.forEach { method ->
                method.parameters.forEach { field ->
                    walkParamContext(field.type)
                }
                method.response?.typeSpec?.let { walkResponseContext(it) }
                method.asyncResponse?.typeSpec?.let { walkResponseContext(it) }
            }
        }
    }

    private fun walkParamContext(typeSpec: TypeSpec) {
        when (typeSpec) {
            is TypeSpec.TypeRefSpec -> {
                if (parameterUsed.add(typeSpec.referenceName)) {
                    val named = typeSpec.typeRef.value
                    if (named is DataObject) {
                        named.type.fields.forEach { walkParamContext(it.type) }
                    }
                }
            }
            is TypeSpec.ArraySpec -> walkParamContext(typeSpec.items)
            is TypeSpec.MapSpec<*> -> walkParamContext(typeSpec.items)
            is TypeSpec.ObjectSpec -> typeSpec.fields.forEach { walkParamContext(it.type) }
            else -> {}
        }
    }

    private fun walkResponseContext(typeSpec: TypeSpec) {
        when (typeSpec) {
            is TypeSpec.TypeRefSpec -> {
                if (responseUsed.add(typeSpec.referenceName)) {
                    val named = typeSpec.typeRef.value
                    if (named is DataObject) {
                        named.type.fields.forEach { walkResponseContext(it.type) }
                    }
                }
            }
            is TypeSpec.ArraySpec -> walkResponseContext(typeSpec.items)
            is TypeSpec.MapSpec<*> -> walkResponseContext(typeSpec.items)
            is TypeSpec.ObjectSpec -> typeSpec.fields.forEach { walkResponseContext(it.type) }
            else -> {}
        }
    }

    /**
     * Returns the [DataObjectClassification] for the DataObject with the given [name].
     *
     * DataObjects that appear in neither context are classified as [DataObjectClassification.ResponseOnly]
     * (i.e. generate with defaults), which is the safe default for objects the generator doesn't recognise
     * as parameter-side types.
     */
    fun classify(name: String): DataObjectClassification {
        val inParam = name in parameterUsed
        val inResponse = name in responseUsed
        return when {
            inParam && inResponse -> DataObjectClassification.Mixed
            inParam -> DataObjectClassification.ParameterOnly
            else -> DataObjectClassification.ResponseOnly
        }
    }
}
