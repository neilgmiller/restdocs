package com.giffardtechnologies.restdocs.domain

import io.vavr.collection.Array

/**
 * A class for including the fields of another data object.
 */
class FieldListIncludeElement(
    val include: DataObject,
    val includeOnly: Array<String> = Array.empty(),
    val excluding: Array<String> = Array.empty(),
    val overrideRequired: RequiredOverride? = null,
) : FieldListElement

class RequiredOverride(
    val required: Boolean,
    val excluding: Array<String> = Array.empty(),
)
