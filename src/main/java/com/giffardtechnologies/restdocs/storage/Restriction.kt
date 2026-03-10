package com.giffardtechnologies.restdocs.storage

/**
 * A constraint applied to a field's allowed values (e.g., a length limit or a value range).
 *
 * @property restriction The name of the restriction type (e.g., `"minLength"`, `"pattern"`).
 * @property value The single restriction value when [hasMultipleValues] is `false`.
 * @property values The list of allowed values when the restriction accepts multiple entries.
 * @property hasMultipleValues `true` when [values] is non-null and non-empty.
 */
class Restriction(val restriction: String) {
    val value = ""
    val values: ArrayList<Any>? = null
    val hasMultipleValues: Boolean
        get() = !values.isNullOrEmpty()
}