package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.FieldElementList
import com.giffardtechnologies.restdocs.domain.Method
import com.giffardtechnologies.restdocs.domain.Response
import com.giffardtechnologies.restdocs.domain.Service
import com.giffardtechnologies.restdocs.domain.dsl.dataObject
import com.giffardtechnologies.restdocs.domain.dsl.document
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DataObjectUsageClassifierTest {

    // -- basic classification --

    @Test
    fun `type not referenced anywhere defaults to ResponseOnly`() {
        val doc = document("test") {
            addDataObject(dataObject("Foo") {})
            service = Service(methods = Array.empty())
        }
        assertEquals(DataObjectClassification.ResponseOnly, DataObjectUsageClassifier(doc).classify("Foo"))
    }

    @Test
    fun `TypeRefSpec in response classifies type as ResponseOnly`() {
        val doc = document("test") {
            addDataObject(dataObject("Foo") {})
            service = Service(
                methods = Array.of(method(response = Response(TypeSpec.TypeRefSpec("Foo", context))))
            )
        }
        assertEquals(DataObjectClassification.ResponseOnly, DataObjectUsageClassifier(doc).classify("Foo"))
    }

    @Test
    fun `TypeRefSpec in asyncResponse classifies type as ResponseOnly`() {
        val doc = document("test") {
            addDataObject(dataObject("Foo") {})
            service = Service(
                methods = Array.of(method(asyncResponse = Response(TypeSpec.TypeRefSpec("Foo", context))))
            )
        }
        assertEquals(DataObjectClassification.ResponseOnly, DataObjectUsageClassifier(doc).classify("Foo"))
    }

    @Test
    fun `TypeRefSpec in parameter field classifies type as ParameterOnly`() {
        val doc = document("test") {
            addDataObject(dataObject("Foo") {})
            service = Service(
                methods = Array.of(method(params = listOf(field("foo", TypeSpec.TypeRefSpec("Foo", context)))))
            )
        }
        assertEquals(DataObjectClassification.ParameterOnly, DataObjectUsageClassifier(doc).classify("Foo"))
    }

    @Test
    fun `TypeRefSpec appearing in both parameter and response classifies type as Mixed`() {
        val doc = document("test") {
            addDataObject(dataObject("Foo") {})
            service = Service(
                methods = Array.of(
                    method(
                        params = listOf(field("fooParam", TypeSpec.TypeRefSpec("Foo", context))),
                        response = Response(TypeSpec.TypeRefSpec("Foo", context))
                    )
                )
            )
        }
        assertEquals(DataObjectClassification.Mixed, DataObjectUsageClassifier(doc).classify("Foo"))
    }

    // -- transitive traversal --

    @Test
    fun `DataObject nested inside parameter DataObject is classified as ParameterOnly`() {
        val doc = document("test") {
            val ctx = context
            addDataObject(dataObject("Bar") {})
            addDataObject(dataObject("Foo") {
                add(Field(name = "bar", longName = "bar", type = TypeSpec.TypeRefSpec("Bar", ctx)))
            })
            service = Service(
                methods = Array.of(method(params = listOf(field("foo", TypeSpec.TypeRefSpec("Foo", ctx)))))
            )
        }
        val classifier = DataObjectUsageClassifier(doc)
        assertEquals(DataObjectClassification.ParameterOnly, classifier.classify("Foo"))
        assertEquals(DataObjectClassification.ParameterOnly, classifier.classify("Bar"))
    }

    @Test
    fun `DataObject nested inside response DataObject is classified as ResponseOnly`() {
        val doc = document("test") {
            val ctx = context
            addDataObject(dataObject("Bar") {})
            addDataObject(dataObject("Foo") {
                add(Field(name = "bar", longName = "bar", type = TypeSpec.TypeRefSpec("Bar", ctx)))
            })
            service = Service(
                methods = Array.of(method(response = Response(TypeSpec.TypeRefSpec("Foo", ctx))))
            )
        }
        val classifier = DataObjectUsageClassifier(doc)
        assertEquals(DataObjectClassification.ResponseOnly, classifier.classify("Foo"))
        assertEquals(DataObjectClassification.ResponseOnly, classifier.classify("Bar"))
    }

    // -- collection wrappers --

    @Test
    fun `TypeRefSpec inside ArraySpec in parameter field classifies type as ParameterOnly`() {
        val doc = document("test") {
            addDataObject(dataObject("Foo") {})
            service = Service(
                methods = Array.of(
                    method(params = listOf(field("foos", TypeSpec.ArraySpec(TypeSpec.TypeRefSpec("Foo", context)))))
                )
            )
        }
        assertEquals(DataObjectClassification.ParameterOnly, DataObjectUsageClassifier(doc).classify("Foo"))
    }

    @Test
    fun `TypeRefSpec inside MapSpec value in parameter field classifies type as ParameterOnly`() {
        val doc = document("test") {
            addDataObject(dataObject("Foo") {})
            service = Service(
                methods = Array.of(
                    method(
                        params = listOf(
                            field("fooMap", TypeSpec.MapSpec(DataType.StringType, TypeSpec.TypeRefSpec("Foo", context)))
                        )
                    )
                )
            )
        }
        assertEquals(DataObjectClassification.ParameterOnly, DataObjectUsageClassifier(doc).classify("Foo"))
    }

    // -- inline ObjectSpec --

    @Test
    fun `TypeRefSpec inside inline ObjectSpec in parameter field classifies type as ParameterOnly`() {
        val doc = document("test") {
            addDataObject(dataObject("Foo") {})
            service = Service(
                methods = Array.of(
                    method(
                        params = listOf(
                            field(
                                "wrapper",
                                TypeSpec.ObjectSpec(
                                    FieldElementList(
                                        Array.of(Field(name = "foo", longName = "foo", type = TypeSpec.TypeRefSpec("Foo", context)))
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
        assertEquals(DataObjectClassification.ParameterOnly, DataObjectUsageClassifier(doc).classify("Foo"))
    }

    // -- common.parameters --

    @Test
    fun `TypeRefSpec in service common parameters classifies type as ParameterOnly`() {
        val doc = document("test") {
            addDataObject(dataObject("Foo") {})
            service = Service(
                common = Service.Common(parameters = Array.of(field("foo", TypeSpec.TypeRefSpec("Foo", context)))),
                methods = Array.empty()
            )
        }
        assertEquals(DataObjectClassification.ParameterOnly, DataObjectUsageClassifier(doc).classify("Foo"))
    }

    // -- cycle safety --

    @Test
    fun `self-referencing DataObject in parameter context does not cause infinite recursion`() {
        val doc = document("test") {
            val ctx = context
            addDataObject(dataObject("Foo") {
                add(Field(name = "child", longName = "child", type = TypeSpec.TypeRefSpec("Foo", ctx)))
            })
            service = Service(
                methods = Array.of(method(params = listOf(field("foo", TypeSpec.TypeRefSpec("Foo", ctx)))))
            )
        }
        assertEquals(DataObjectClassification.ParameterOnly, DataObjectUsageClassifier(doc).classify("Foo"))
    }

    // -- helpers --

    private fun field(name: String, type: TypeSpec) = Field(name = name, longName = name, type = type)

    private fun method(
        params: List<Field> = emptyList(),
        response: Response? = null,
        asyncResponse: Response? = null,
    ) = Method(
        method = Method.HTTPMethod.POST,
        id = 1,
        name = "testMethod",
        isAuthenticationRequired = false,
        parameterElementList = FieldElementList(Array.ofAll(params)),
        response = response,
        asyncResponse = asyncResponse,
    )
}
