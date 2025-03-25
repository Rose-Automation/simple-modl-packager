package industries.roseautomation.simplemodlpackager

import industries.roseautomation.simplemodlpackager.model.DependsDefinition
import industries.roseautomation.simplemodlpackager.model.HookDefinition
import industries.roseautomation.simplemodlpackager.model.JarDefinition
import industries.roseautomation.simplemodlpackager.model.ModuleDefinition
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ModuleXmlGeneratorTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `generateModuleXml creates basic module xml with required fields`() {
        // Setup
        val module = ModuleDefinition(
            id = "com.example.module",
            name = "Example Module",
            version = "1.0.0"
        )
        val outputFile = File(tempDir, "module.xml")
        val generator = ModuleXmlGenerator()

        // Execute
        generator.generateModuleXml(module, outputFile)

        // Verify
        assertTrue(outputFile.exists())
        val document = parseXml(outputFile)

        // Check required elements
        assertEquals("com.example.module", getElementValue(document, "id"))
        assertEquals("Example Module", getElementValue(document, "name"))
        assertEquals("1.0.0", getElementValue(document, "version"))
        assertEquals("8.1.45", getElementValue(document, "requiredignitionversion"))
        assertEquals("8", getElementValue(document, "requiredframeworkversion"))

        // Check optional elements are not included when not specified
        assertNull(getElementValue(document, "license"))
        assertNull(getElementValue(document, "documentation"))
    }

    @Test
    fun `generateModuleXml includes optional fields when specified`() {
        // Setup
        val module = ModuleDefinition(
            id = "com.example.module",
            name = "Example Module",
            version = "1.0.0",
            description = "This is a test module",
            license = "license.html",
            documentation = "doc/readme.html"
        )
        val outputFile = File(tempDir, "module.xml")
        val generator = ModuleXmlGenerator()

        // Execute
        generator.generateModuleXml(module, outputFile)

        // Verify
        assertTrue(outputFile.exists())
        val document = parseXml(outputFile)

        // Check optional elements
        assertEquals("This is a test module", getElementValue(document, "description"))
        assertEquals("license.html", getElementValue(document, "license"))
        assertEquals("doc/readme.html", getElementValue(document, "documentation"))
    }

    @Test
    fun `generateModuleXml should include jar elements with scope attributes`() {
        // Setup
        val module = ModuleDefinition(
            id = "com.example.module",
            name = "Example Module",
            version = "1.0.0",
            jars = listOf(
                JarDefinition("path/to/gateway.jar", "G"),
                JarDefinition("path/to/client.jar", "C"),
                JarDefinition("path/to/designer.jar", "D")
            )
        )
        val outputFile = File(tempDir, "module.xml")
        val generator = ModuleXmlGenerator()

        // Execute
        generator.generateModuleXml(module, outputFile)

        // Verify
        assertTrue(outputFile.exists())
        val document = parseXml(outputFile)

        // Check jar elements
        val jarElements = document.getElementsByTagName("jar")
        assertEquals(3, jarElements.length)

        // Check the first jar
        assertEquals("G", jarElements.item(0).attributes.getNamedItem("scope").textContent)
        assertEquals("gateway.jar", jarElements.item(0).textContent)

        // Check the second jar
        assertEquals("C", jarElements.item(1).attributes.getNamedItem("scope").textContent)
        assertEquals("client.jar", jarElements.item(1).textContent)

        // Check the third jar
        assertEquals("D", jarElements.item(2).attributes.getNamedItem("scope").textContent)
        assertEquals("designer.jar", jarElements.item(2).textContent)
    }

    @Test
    fun `generateModuleXml should include hook elements with scope attributes`() {
        // Setup
        val module = ModuleDefinition(
            id = "com.example.module",
            name = "Example Module",
            version = "1.0.0",
            hooks = listOf(
                HookDefinition("com.example.hooks.GatewayHook", "G"),
                HookDefinition("com.example.hooks.ClientHook", "C"),
                HookDefinition("com.example.hooks.DesignerHook", "D")
            )
        )
        val outputFile = File(tempDir, "module.xml")
        val generator = ModuleXmlGenerator()

        // Execute
        generator.generateModuleXml(module, outputFile)

        // Verify
        assertTrue(outputFile.exists())
        val document = parseXml(outputFile)

        // Check hook elements
        val hookElements = document.getElementsByTagName("hook")
        assertEquals(3, hookElements.length)

        // Check the first hook
        assertEquals("G", hookElements.item(0).attributes.getNamedItem("scope").textContent)
        assertEquals("com.example.hooks.GatewayHook", hookElements.item(0).textContent)

        // Check the second hook
        assertEquals("C", hookElements.item(1).attributes.getNamedItem("scope").textContent)
        assertEquals("com.example.hooks.ClientHook", hookElements.item(1).textContent)

        // Check the third hook
        assertEquals("D", hookElements.item(2).attributes.getNamedItem("scope").textContent)
        assertEquals("com.example.hooks.DesignerHook", hookElements.item(2).textContent)
    }

    @Test
    fun `generateModuleXml should include depends elements with scope attributes`() {
        // Setup
        val module = ModuleDefinition(
            id = "com.example.module",
            name = "Example Module",
            version = "1.0.0",
            depends = listOf(
                DependsDefinition("com.inductiveautomation.vision", "G"),
                DependsDefinition("com.inductiveautomation.opc", "C"),
                DependsDefinition("com.inductiveautomation.perspective", "D")
            )
        )
        val outputFile = File(tempDir, "module.xml")
        val generator = ModuleXmlGenerator()

        // Execute
        generator.generateModuleXml(module, outputFile)

        // Verify
        assertTrue(outputFile.exists())
        val document = parseXml(outputFile)

        // Check depends elements
        val dependsElements = document.getElementsByTagName("depends")
        assertEquals(3, dependsElements.length)

        // Check the first depends
        assertEquals("G", dependsElements.item(0).attributes.getNamedItem("scope").textContent)
        assertEquals("com.inductiveautomation.vision", dependsElements.item(0).textContent)

        // Check the second depends
        assertEquals("C", dependsElements.item(1).attributes.getNamedItem("scope").textContent)
        assertEquals("com.inductiveautomation.opc", dependsElements.item(1).textContent)

        // Check the third depends
        assertEquals("D", dependsElements.item(2).attributes.getNamedItem("scope").textContent)
        assertEquals("com.inductiveautomation.perspective", dependsElements.item(2).textContent)
    }

    @Test
    fun `generateModuleXml should extract only jar filename from path`() {
        // Setup
        val module = ModuleDefinition(
            id = "com.example.module",
            name = "Example Module",
            version = "1.0.0",
            jars = listOf(
                JarDefinition("target/path/to/complex/directory/structure/mymodule-1.0.0.jar", "G"),
                JarDefinition("relative/path/client-module.jar", "C")
            )
        )
        val outputFile = File(tempDir, "module.xml")
        val generator = ModuleXmlGenerator()

        // Execute
        generator.generateModuleXml(module, outputFile)

        // Verify
        assertTrue(outputFile.exists())
        val document = parseXml(outputFile)

        // Check jar elements
        val jarElements = document.getElementsByTagName("jar")
        assertEquals(2, jarElements.length)

        // Verify only the JAR filenames are included, not the full paths
        assertEquals("mymodule-1.0.0.jar", jarElements.item(0).textContent)
        assertEquals("client-module.jar", jarElements.item(1).textContent)
    }

    @Test
    fun `generateModuleXml should throw exception when required fields are missing`() {
        // Setup
        val moduleWithoutId = ModuleDefinition(
            name = "Example Module",
            version = "1.0.0"
        )
        val outputFile = File(tempDir, "module.xml")
        val generator = ModuleXmlGenerator()

        // Execute and Verify
        assertThrows(IllegalArgumentException::class.java) {
            generator.generateModuleXml(moduleWithoutId, outputFile)
        }

        // Try with name missing
        val moduleWithoutName = ModuleDefinition(
            id = "com.example.module",
            version = "1.0.0"
        )
        assertThrows(IllegalArgumentException::class.java) {
            generator.generateModuleXml(moduleWithoutName, outputFile)
        }

        // Try with version missing
        val moduleWithoutVersion = ModuleDefinition(
            id = "com.example.module",
            name = "Example Module"
        )
        assertThrows(IllegalArgumentException::class.java) {
            generator.generateModuleXml(moduleWithoutVersion, outputFile)
        }
    }

    // Helper methods to parse XML and extract values
    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(file)

    private fun getElementValue(document: org.w3c.dom.Document, tagName: String): String? {
        val elements = document.getElementsByTagName(tagName)
        return if (elements.length > 0) {
            elements.item(0).textContent
        } else {
            null
        }
    }
}