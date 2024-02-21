package com.giffardtechnologies.restdocs.htmlgen

import com.giffardtechnologies.restdocs.storage.Common
import com.giffardtechnologies.restdocs.storage.DataObject
import com.giffardtechnologies.restdocs.storage.Document
import com.giffardtechnologies.restdocs.storage.Method
import com.giffardtechnologies.restdocs.storage.NamedEnumeration
import com.giffardtechnologies.restdocs.storage.Response
import com.giffardtechnologies.restdocs.storage.Restriction
import com.giffardtechnologies.restdocs.storage.Service
import com.giffardtechnologies.restdocs.storage.type.EnumConstant
import com.giffardtechnologies.restdocs.storage.type.Field
import com.giffardtechnologies.restdocs.storage.type.TypeSpec

class ObjectInspectionHelper(val document: Document) {

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

    fun getEffectiveFields(typeSpec: TypeSpec) : Boolean {
        return !typeSpec.fields.isNullOrEmpty()
    }

    fun isTypeRef(typeSpec: TypeSpec?): Boolean {
        return !typeSpec?.typeRef.isNullOrEmpty()
    }

    fun hasDefaultValue(field: Field): Boolean {
        return !field.defaultValue.isNullOrEmpty()
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

}
