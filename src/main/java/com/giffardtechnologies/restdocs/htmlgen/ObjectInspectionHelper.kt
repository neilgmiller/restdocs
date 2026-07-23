package com.giffardtechnologies.restdocs.htmlgen

import com.giffardtechnologies.restdocs.storage.Common
import com.giffardtechnologies.restdocs.storage.DataObject
import com.giffardtechnologies.restdocs.storage.Document
import com.giffardtechnologies.restdocs.storage.Method
import com.giffardtechnologies.restdocs.storage.NamedBitSet
import com.giffardtechnologies.restdocs.storage.NamedEnumeration
import com.giffardtechnologies.restdocs.storage.Response
import com.giffardtechnologies.restdocs.storage.Restriction
import com.giffardtechnologies.restdocs.storage.Service
import com.giffardtechnologies.restdocs.storage.type.EnumConstant
import com.giffardtechnologies.restdocs.storage.type.Field
import com.giffardtechnologies.restdocs.storage.type.FieldElementList
import com.giffardtechnologies.restdocs.storage.type.TypeSpec

class ObjectInspectionHelper(val document: Document) {

    fun hasBitSets(document: Document): Boolean {
        return document.bitsets.isNotEmpty()
    }

    fun hasEnumerations(document: Document): Boolean {
        return document.enumerations.isNotEmpty()
    }

    fun hasVisibleDataObjects(document: Document): Boolean {
        return document.dataObjects.any { !it.isHidden }
    }

    fun getVisibleDataObjects(document: Document): List<DataObject> {
        return document.dataObjects.filter { !it.isHidden }
    }

    fun hasEnumValues(enumeration: NamedEnumeration): Boolean {
        return !enumeration.values.isNullOrEmpty()
    }

    fun hasBitSetValues(bitSet: NamedBitSet): Boolean {
        return !bitSet.values.isNullOrEmpty()
    }

    fun getLongName(enumConstant: EnumConstant): String {
        return enumConstant.longName ?: enumConstant.value
    }

    fun hasDescription(enumConstant: EnumConstant): Boolean {
        return !enumConstant.description.isNullOrEmpty()
    }

    fun hasFields(dataObject: DataObject) : Boolean {
        return dataObject.fields.isNotEmpty()
    }

    fun getFields(dataObject: DataObject) : ArrayList<Field> {
        return FieldElementList(document, dataObject.fields).getFields()
    }

    fun hasChildren(dataObject: DataObject) : Boolean {
        return !dataObject.childTypes.isNullOrEmpty()
    }

    fun hasFields(typeSpec: TypeSpec) : Boolean {
        return !typeSpec.fields.isNullOrEmpty()
    }

    fun getFields(typeSpec: TypeSpec) : ArrayList<Field> {
        return FieldElementList(document, typeSpec.fields!!).getFields()
    }

    fun isTypeRef(typeSpec: TypeSpec?): Boolean {
        return !typeSpec?.typeRef.isNullOrEmpty()
    }

    fun hasDefaultValue(field: Field): Boolean {
        return !field.defaultValue.isNullOrEmpty()
    }

    fun hasParsedAs(field: Field): Boolean {
        return field.parsedAs != null
    }

    fun hasInterpretedAs(field: Field): Boolean {
        return field.interpretedAs != null
    }

    fun hasRestrictions(field: Field): Boolean {
        return !field.restrictions.isNullOrEmpty()
    }

    fun hasMultipleValues(restriction: Restriction): Boolean {
        return !restriction.hasMultipleValues
    }

    fun hasCommon(service: Service): Boolean {
        return service.common != null
    }

    fun hasResponseDataObjects(service: Service): Boolean {
        return service.common?.responseDataObjects?.isNotEmpty() ?: false
    }

    fun hasHeaders(common: Common): Boolean {
        return !common.headers.isNullOrEmpty()
    }

    fun hasParameters(common: Common): Boolean {
        return !common.parameters.isNullOrEmpty()
    }

    fun hasResponseDataObjects(common: Common): Boolean {
        return common.responseDataObjects.isNotEmpty()
    }

    fun getDescription(method: Method): String {
        return method.description ?: ""
    }

    fun hasParameters(method: Method): Boolean {
        return !method.parameters.isNullOrEmpty()
    }

    fun getParameters(typeSpec: Method) : ArrayList<Field> {
        return FieldElementList(document, typeSpec.parameters!!).getFields()
    }

    fun hasRequestBody(method: Method): Boolean {
        return method.requestBody != null
    }

    fun hasDescription(response: Response?): Boolean {
        return !response?.description.isNullOrEmpty()
    }

    fun hasNoPayload(response: Response?): Boolean {
        return response?.noPayload == true
    }

    fun isDeprecated(method: Method): Boolean {
        return method.deprecated
    }

    fun hasDeprecationNote(method: Method): Boolean {
        return !method.deprecationNote.isNullOrBlank()
    }

    fun hasDeprecatedSince(method: Method): Boolean {
        return !method.deprecatedSince.isNullOrBlank()
    }

    /**
     * Builds a synthetic [Field] representing a scalar `asyncResponse`, so it can be rendered
     * through the same `#fieldrow` table as object-shaped responses. The name is always "jr" —
     * the field the async job result is delivered in — since [Response] has no wire name of its
     * own, only an optional [Response.longName] for documentation.
     */
    fun asyncResponseAsField(response: Response): Field {
        return Field(
            name = "jr",
            longName = response.longName ?: "",
            description = response.description ?: "",
            type = response.type,
            parsedAs = response.parsedAs,
            interpretedAs = response.interpretedAs,
            typeRef = response.typeRef,
            key = response.key,
            flagType = response.flagType,
            items = response.items,
            restrictions = response.restrictions,
            fields = response.fields,
            values = response.values,
        )
    }

}
