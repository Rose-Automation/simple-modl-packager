package industries.roseautomation.simplemodlpackager

import industries.roseautomation.simplemodlpackager.model.ModuleDefinition
import java.io.File
import java.io.FileOutputStream
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

class ModuleXmlGenerator {
    fun generateModuleXml(module: ModuleDefinition, outputFile: File) {
        FileOutputStream(outputFile).use { fos ->
            val factory = XMLOutputFactory.newInstance()
            val writer = factory.createXMLStreamWriter(fos, "UTF-8")

            try {
                // Write XML structure
                writer.writeStartDocument("UTF-8", "1.0")
                writer.writeStartElement("modules")
                writer.writeStartElement("module")

                // Write required elements
                module.id?.let { writeElement(writer, "id", it) }
                module.name?.let { writeElement(writer, "name", it) }
                module.description?.let { writeElement(writer, "description", it) }
                module.version?.let { writeElement(writer, "version", it) }
                writeElement(writer, "requiredignitionversion", module.requiredIgnitionVersion)
                writeElement(writer, "requiredframeworkversion", module.requiredFrameworkVersion)
                writeElement(writer, "license", module.license)
                writeElement(writer, "documentation", module.documentation)

                // Write jar entries
                module.jars.forEach { jar ->
                    writer.writeStartElement("jar")
                    jar.scope?.let { writer.writeAttribute("scope", it) }
                    jar.path?.let { writer.writeCharacters(it) }
                    writer.writeEndElement()
                }

                // Write hook entries
                module.hooks.forEach { hook ->
                    writer.writeStartElement("hook")
                    hook.scope?.let { writer.writeAttribute("scope", it) }
                    hook.className?.let { writer.writeCharacters(it) }
                    writer.writeEndElement()
                }

                writer.writeEndElement() // module
                writer.writeEndElement() // modules
                writer.writeEndDocument()
            } finally {
                writer.close()
            }
        }
    }

    private fun writeElement(writer: XMLStreamWriter, name: String, value: String) {
        writer.writeStartElement(name)
        writer.writeCharacters(value)
        writer.writeEndElement()
    }
}