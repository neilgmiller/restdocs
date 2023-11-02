package com.giffardtechnologies.restdocs

import com.giffardtechnologies.restdocs.domain.DataObject
import com.giffardtechnologies.restdocs.domain.Document
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.gson.GsonFactory
import org.apache.velocity.Template
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.tools.generic.EscapeTool
import org.yaml.snakeyaml.Yaml
import picocli.CommandLine
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.IOException
import java.util.*
import java.util.concurrent.Callable

@CommandLine.Command(
    description = ["Generates documents or code based for a given API descriptor"],
    name = "doc_generator",
    mixinStandardHelpOptions = true,
    version = ["DocGenerator 1.0"]
)
class DocGenerator : Callable<Void?> {
    //	@Parameters(index = "0", hidden = true, description = "The executable directory, passed by the wrapper script.")
    private var mExecutableDir: File? = null

    @CommandLine.Option(
        names = ["-f", "-p", "--properties"],
        description = ["The properties file describing the generation."]
    )
    private var mPropertiesFile: File? = null

    @CommandLine.Option(
        names = ["-c", "--code"],
        description = ["Flag indicating that the command should generate Java code."]
    )
    private val mGenCode = false

    @CommandLine.Option(names = ["-v", "--verbose"], description = ["Turn on verbose output"])
    private val mVerbose = false
    var sourceFile: File? = null
    var outputFile: File? = null
    var templateDir: File? = null
    var templateFileName: String? = null
    private var mProperties: Properties? = null
    @Throws(Exception::class)
    override fun call(): Void? {
//		System.out.println(System.getProperty("execdir"));
//		System.out.println(System.getProperty("progdir"));
        mExecutableDir = File(System.getProperty("execdir"))
        if (mPropertiesFile == null) {
            mPropertiesFile = File("docbuild.properties")
        }
        mPropertiesFile = mPropertiesFile!!.absoluteFile
        mProperties = Properties()
        val propsInStream = BufferedInputStream(FileInputStream(mPropertiesFile))
        mProperties!!.load(propsInStream)
        propsInStream.close()
        val templateFile: File
        val templateFilePath = mProperties!!.getProperty("templateFile")
        if (templateFilePath == null) {
            templateDir = mExecutableDir
        } else {
            templateFile = File(mPropertiesFile.getParentFile(), templateFilePath)
            if (!templateFile.exists()) {
                System.err.println("No template file at: " + templateFile.absolutePath)
                return null
            }
            val parentFile = templateFile.parentFile
            if (parentFile == null) {
                templateDir = mExecutableDir
            } else {
                templateDir = parentFile.absoluteFile
            }
            templateFileName = templateFile.name
        }
        if (mVerbose) {
            val file = File(templateDir, templateFileName)
            println("Template file is: " + file.absolutePath)
        }
        sourceFile = File(mPropertiesFile.getParentFile(), mProperties!!.getProperty("sourceFile"))
        outputFile = File(mPropertiesFile.getParentFile(), mProperties!!.getProperty("outputFile"))
        if (mGenCode) {
            generateCode()
        } else {
            generate()
        }
        return null
    }

    @Throws(IOException::class)
    private fun generate() {
        val doc = parseDocument()

        /*
		 *  create a new instance of the engine
		 */
        val ve = VelocityEngine()

        /*
		 *  initialize the engine
		 */
        val t: Template
        val p = Properties()
        p.setProperty("file.resource.loader.path", templateDir!!.absolutePath)
        ve.init(p)
        t = if (templateFileName == null) {
            ve.getTemplate("rest_api_doc.vm")
        } else {
            ve.getTemplate(templateFileName)
        }
        val context = VelocityContext()
        context.put("document", doc)
        context.put("esc", EscapeTool())
        context.put("link", LinkTool(doc))
        context.put("text", PlainTextTool(doc))

//		StringWriter sw = new StringWriter();
//		t.merge(context, sw);
//		
//		output.println(sw.getBuffer().toString());
        outputFile!!.parentFile.mkdirs()
        val fileWriter = FileWriter(outputFile)
        t.merge(context, fileWriter)
        fileWriter.close()
    }

    @Throws(IOException::class)
    private fun generateCode() {
        val doc = parseDocument()

        /*
		 *  create a new instance of the engine
		 */
        val ve = VelocityEngine()

        /*
		 *  initialize the engine
		 */
        var t: Template
        val p = Properties()
        //		p.setProperty("file.resource.loader.path", mTemplateDir.getAbsolutePath());
        p.setProperty("globbing.resource.loader.path", templateDir!!.absolutePath)
        p.setProperty("resource.loader", "globbing,string")
        p.setProperty(
            "globbing.resource.loader.class",
            "org.apache.velocity.tools.view.StructuredGlobbingResourceLoader"
        )
        p.setProperty(
            "string.resource.loader.class",
            "org.apache.velocity.runtime.resource.loader.StringResourceLoader"
        )
        ve.init(p)
        val codeDir = File(mProperties!!.getProperty("codeDir"))
        // TODO check this exists
        val dtoTemplateFileName = mProperties!!.getProperty("dtoTemplateFile", "rest_api_dto.vm")
        t = ve.getTemplate(dtoTemplateFileName)
        val context = VelocityContext()
        val javaTool = JavaTool(doc)
        context.put("java", javaTool)
        val dtoPackage = mProperties!!.getProperty("dtoPackage")
        val dtoPath = dtoPackage.replace("\\.".toRegex(), "/")
        val dtoDir = File(codeDir, dtoPath)
        dtoDir.mkdirs()
        context.put("destinationPackage", dtoPackage)
        for (dataObject in doc.dataObjects) {
            for (field in dataObject.fields) {
                if (field.longName.equals(dataObject.name + "id", ignoreCase = true)) {
                    field.longName = "id"
                }
                if (field.type == DataType.OBJECT) {
                    val fieldDataObject = DataObject()
                    fieldDataObject.fields = field.fields
                    fieldDataObject.name = javaTool.fieldToClassStyle(field)
                    context.put("dataObject", fieldDataObject)
                    val fileWriter = FileWriter(File(dtoDir, fieldDataObject.name + ".java"))
                    t.merge(context, fileWriter)
                    fileWriter.close()
                }
                if (field.type == DataType.ARRAY && field.items.type == DataType.OBJECT) {
                    val fieldDataObject = DataObject()
                    fieldDataObject.fields = field.items.fields
                    fieldDataObject.name = javaTool.fieldToClassStyle(field)
                    context.put("dataObject", fieldDataObject)
                    val fileWriter = FileWriter(File(dtoDir, fieldDataObject.name + ".java"))
                    t.merge(context, fileWriter)
                    fileWriter.close()
                }
            }
            context.put("dataObject", dataObject)
            val fileWriter = FileWriter(File(dtoDir, dataObject.name + ".java"))
            t.merge(context, fileWriter)
            fileWriter.close()
        }
        val enumTemplateFileName = mProperties!!.getProperty("enumTemplateFile", "rest_api_enum.vm")
        t = ve.getTemplate(enumTemplateFileName)
        for (namedEnumeration in doc.enumerations) {
            context.put("enumeration", namedEnumeration)
            val fileWriter = FileWriter(File(dtoDir, namedEnumeration.name + ".java"))
            t.merge(context, fileWriter)
            fileWriter.close()
        }

        // add the DTO package for future reference
        context.put("dtoPackage", dtoPackage)
        t = ve.getTemplate(dtoTemplateFileName)

        // create the responses
        val responsePackage = mProperties!!.getProperty("responsePackage")
        val responsePath = responsePackage.replace("\\.".toRegex(), "/")
        val responseDir = File(codeDir, responsePath)
        responseDir.mkdirs()
        context.put("destinationPackage", responsePackage)
        for (method in doc.service.methods) {
            if (method.response != null) {
                if (method.response.type == DataType.OBJECT) {
                    val responseClassName = method.name.substring(0, 1)
                        .uppercase(Locale.getDefault()) + method.name.substring(1) + "Response"

                    // setup a temp DataObject
                    val dataObject = DataObject()
                    dataObject.name = responseClassName
                    dataObject.fields = method.response.fields
                    context.put("dataObject", dataObject)
                    val fileWriter = FileWriter(File(responseDir, "$responseClassName.java"))
                    t.merge(context, fileWriter)
                    fileWriter.close()
                }
            }
        }
        val requestTemplateFileName = mProperties!!.getProperty("requestTemplateFile", "rest_api_req.vm")
        t = ve.getTemplate(requestTemplateFileName)
        val requestPackage = mProperties!!.getProperty("requestPackage")
        val requestPath = requestPackage.replace("\\.".toRegex(), "/")
        val requestDir = File(codeDir, requestPath)
        requestDir.mkdirs()
        context.put("destinationPackage", requestPackage)
        for (method in doc.service.methods) {
            context.put("method", method)
            val fileWriter = FileWriter(
                File(
                    requestDir,
                    method.name.substring(0, 1)
                        .uppercase(Locale.getDefault()) + method.name.substring(1) + "Request.java"
                )
            )
            t.merge(context, fileWriter)
            fileWriter.close()
        }
    }

    @Throws(IOException::class)
    private fun parseDocument(): Document {
        val input = BufferedInputStream(FileInputStream(sourceFile))
        val yaml = Yaml()
        //		YamlReader yamlReader = new YamlReader(new InputStreamReader(input));

//		yamlReader.read();
        val map = yaml.load<Any>(input) as Map<*, *>
        //		Document doc = yaml.loadAs(input, Document.class);
        input.close()
        val gsonBuilder = GsonFactory.getGsonBuilder()
        val gsonForPrinting = gsonBuilder.create()
        val json = gsonForPrinting.toJson(map)
        //		System.out.println(json);
        val output = System.out
        //		for (DataObject dataObject : doc.getDataObjects()) {
//			output.println(dataObject.getName());
//		}
        val document = gsonForPrinting.fromJson(json, Document::class.java)
        document.buildMappings()
        return document
    }

    companion object {
        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
//		for (String arg : args) {
//			System.out.print(arg);
//			System.out.print(", ");
//		}
//		System.out.println();
            val docGenerator = DocGenerator()
            CommandLine.call(docGenerator, *args)
        }
    }
}
