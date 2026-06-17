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
 * **Pass 1** walks [com.giffardtechnologies.restdocs.domain.Method.parameters] (already-flattened fields)
 * and recurses transitively through any [TypeSpec.TypeRefSpec] it encounters.
 *
 * **Pass 2** walks each DataObject that is not in `parameterUsed` (i.e. ResponseOnly or never-seen
 * as a direct TypeRef) through its canonical field list in response context. This catches the case
 * where a DataObject is only ever referenced via `include:` (so it never appears as a TypeRef in
 * method responses and defaults to ResponseOnly), but itself owns a TypeRef field to a DataObject
 * that pass 1 saw only in parameter context. Without pass 2 that inner DataObject would be
 * misclassified as ParameterOnly instead of Mixed.
 */
class DataObjectUsageClassifier(document: Document) {

    private val parameterUsed = mutableSetOf<String>()
    private val responseUsed = mutableSetOf<String>()

    init {
        // Pass 1: walk method params and responses
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

        // Pass 2: DataObjects that are ResponseOnly (not in parameterUsed) may own TypeRef fields
        // that pass 1 only saw in parameter context. Walk their canonical fields in response context
        // so those referenced types are not left as ParameterOnly when they are actually reachable
        // through a response-side DataObject.
        document.dataObjects
            .filter { it.typeName !in parameterUsed }
            .forEach { dataObject ->
                dataObject.type.fields.forEach { walkResponseContext(it.type) }
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
