package com.giffardtechnologies.restdocs.htmlgen

import com.giffardtechnologies.restdocs.domain.Service.Common
import com.giffardtechnologies.restdocs.domain.DataObject
import com.giffardtechnologies.restdocs.domain.Document
import com.giffardtechnologies.restdocs.domain.Method
import com.giffardtechnologies.restdocs.domain.NamedEnumeration
import com.giffardtechnologies.restdocs.domain.Response
import com.giffardtechnologies.restdocs.domain.Restriction
import com.giffardtechnologies.restdocs.domain.Service
import com.giffardtechnologies.restdocs.domain.type.BooleanRepresentation
import com.giffardtechnologies.restdocs.domain.type.Field
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import com.giffardtechnologies.restdocs.vavr.isNotEmpty
import io.vavr.collection.Array

class ObjectInspectionHelper(val document: Document) {

    fun hasEnumerations(document: Document): Boolean {
        return document.enumerations.isNotEmpty()
    }

    fun hasVisibleDataObjects(document: Document): Boolean {
        return document.dataObjects.any { !it.isHidden }
    }

    fun getVisibleDataObjects(document: Document): Array<DataObject> {
        return document.dataObjects.filter { !it.isHidden }
    }

    fun hasEnumValues(enumeration: NamedEnumeration): Boolean {
        return enumeration.type.values.isNotEmpty()
    }

    fun getLongName(enumConstant: EnumConstant): String {
        return enumConstant.longName ?: enumConstant.value
    }

    fun hasDescription(enumConstant: EnumConstant): Boolean {
        return !enumConstant.description.isNullOrEmpty()
    }

    fun hasFields(dataObject: DataObject) : Boolean {
        return dataObject.type.fields.isNotEmpty()
    }

    fun getFields(dataObject: DataObject) : ArrayList<Field> {
        return FieldElementList(document, dataObject.fields).getFields()
    }

    fun hasChildren(dataObject: DataObject) : Boolean {
        return !dataObject.childTypes.isNullOrEmpty()
    }

    fun hasFields(typeSpec: TypeSpec) : Boolean {
        return if (typeSpec is TypeSpec.ObjectSpec) typeSpec.fields.isNotEmpty() else false
    }

    fun getFields(typeSpec: TypeSpec) : ArrayList<Field> {
        return FieldElementList(document, typeSpec.fields!!).getFields()
    }

    fun getEffectiveFields(typeSpec: TypeSpec) : Boolean {
        return if (typeSpec is TypeSpec.ObjectSpec) typeSpec.fields.isNotEmpty() else false
    }

    fun isTypeRef(typeSpec: TypeSpec?): Boolean {
        return !typeSpec?.typeRef.isNullOrEmpty()
    }

    fun hasDefaultValue(field: Field): Boolean {
        return !field.defaultValue.isNullOrEmpty()
    }

    fun hasInterpretedAs(field: Field): Boolean {
        return field.type is TypeSpec.BooleanSpec && field.type.representedAs != BooleanRepresentation.AsInteger
    }

    fun hasRestrictions(field: Field): Boolean {
        return field.type is TypeSpec.DataSpec && field.type.restrictions.isNotEmpty()
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
        return common.headers.isNotEmpty()
    }

    fun hasParameters(common: Common): Boolean {
        return common.parameters.isNotEmpty()
    }

    fun hasResponseDataObjects(common: Common): Boolean {
        return common.responseDataObjects.isNotEmpty()
    }

    fun getDescription(method: Method): String {
        return method.description ?: ""
    }

    fun hasParameters(method: Method): Boolean {
        return method.parameters.fields.isNotEmpty()
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