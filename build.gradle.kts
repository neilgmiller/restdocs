import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "org.giffardtechnologies"
version = "2.3-SNAPSHOT"

kotlin {
    jvmToolchain(11)
}

val homePath: String = System.getProperty("user.home")

var installDir: String? = null
val locPropertiesFile: File = project.rootProject.file("local.properties")
if (locPropertiesFile.exists()) {
    val locProperties = Properties()
    locPropertiesFile.inputStream().use { locProperties.load(it) }
    installDir = locProperties.getProperty("installDir")
}

repositories {
    mavenCentral()
    ivy {
        url = uri("$homePath/.ivy/repo")
    }
    ivy {
        credentials {
            username = property("allegoArtifactoryUser") as String?
            password = property("allegoArtifactoryPassword") as String?
        }
        url = uri("https://dev-artifactory.allego-dev.com/artifactory/${property("allegoAndroidArtifactoryRepo")}/")
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(fileTree(mapOf("dir" to "manual-libs", "include" to listOf("*.jar"))))

    implementation(libs.snakeyaml)
    implementation(libs.velocity.engine)
    implementation(libs.velocity.tools)
    implementation(libs.gson)
    implementation(libs.picocli)
    implementation(libs.findbugs.jsr305)
    compileOnly(libs.jetbrains.annotations)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.commons.io)
    implementation(libs.commons.lang3)
    implementation(libs.commons.text)

    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    implementation(libs.vavr)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.javapoet)
    implementation(libs.guava)
    implementation(libs.futureproofenum)

    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)
}

application {
    mainClass.set("com.giffardtechnologies.restdocs.DocGeneratorCommand")
    applicationName = "doc_generator"
}

distributions {
    main {
        distributionBaseName = project.name
    }
}

val createKotlinGeneratorStartScript by tasks.registering(Copy::class) {
    dependsOn(tasks.named("startScripts"))
    from(layout.buildDirectory.file("scripts/doc_generator"))
    rename { "kotlin_generator" }
    filter { line: String -> line.replace("DocGenerator", "KotlinGenerator") }
    into(layout.buildDirectory.dir("scripts"))
}

val createValidatorStartScript by tasks.registering(Copy::class) {
    dependsOn(tasks.named("startScripts"), tasks.named("createKotlinGeneratorStartScript"))
    from(layout.buildDirectory.file("scripts/doc_generator"))
    rename { "doc_validator" }
    filter { line: String -> line.replace("(.*)DocGenerator(.*)".toRegex(), "$1DocValidator$2") }
    into(layout.buildDirectory.dir("scripts"))
}
tasks.named("startScripts") {
    finalizedBy(createKotlinGeneratorStartScript)
    finalizedBy(createValidatorStartScript)
}

tasks.named<Sync>("installDist") {
    dependsOn(tasks.named("createValidatorStartScript"), tasks.named("createKotlinGeneratorStartScript"))
    mustRunAfter(tasks.named("createValidatorStartScript"))
    installDir?.let {
        destinationDir = file(it)
    }
}

tasks.forEach { task ->
    if (task.name.startsWith("dist")) {
        task.dependsOn(createValidatorStartScript)
    }
}

val processResources = tasks.named<ProcessResources>("processResources") {
    val versionValue = project.version.toString()
    inputs.property("version", versionValue)
    doLast {
        val file = destinationDir.resolve("version.properties")
        println("Properties: ${file.absolutePath}")
        file.writeText("version=$versionValue\n")
    }
}

tasks.named("compileKotlin") {
    dependsOn(processResources)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Copy>("getDeps") {
    from(sourceSets.main.get().runtimeClasspath)
    into("runtime-libs/")
}
