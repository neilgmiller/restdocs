package com.giffardtechnologies.restdocs.domain

import io.vavr.collection.Array

/**
 * A class for including the fields of another data object.
 */
class FieldListIncludeElement(
    val include: DataObject,
    val excluding: Array<String> = Array.empty(),
) : FieldListElement
