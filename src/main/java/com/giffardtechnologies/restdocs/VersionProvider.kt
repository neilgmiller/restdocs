package com.giffardtechnologies.restdocs

import picocli.CommandLine
import java.util.Properties

class VersionProvider : CommandLine.IVersionProvider {
    override fun getVersion(): Array<String> {
        val properties = Properties()
        VersionProvider::class.java.getResourceAsStream("/version.properties")?.use {
            properties.load(it)
        }
        return arrayOf(properties.getProperty("version", "unknown"))
    }
}
