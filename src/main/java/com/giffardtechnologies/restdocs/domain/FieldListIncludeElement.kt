package com.giffardtechnologies.restdocs.domain

/**
 * A class for including the fields of data object inline with the a list of fields
 */
class FieldListIncludeElement : FieldListElement {
    /**
     * A reference to a DataObject, all fields of that object will be included
     */
    var include: String? = null
    var excluding = ArrayList<String>()
}
