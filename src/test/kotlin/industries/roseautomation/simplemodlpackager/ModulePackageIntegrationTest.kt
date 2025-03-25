package industries.roseautomation.simplemodlpackager

import industries.roseautomation.simplemodlpackager.model.DependsDefinition
import industries.roseautomation.simplemodlpackager.model.HookDefinition
import industries.roseautomation.simplemodlpackager.model.JarDefinition
import industries.roseautomation.simplemodlpackager.model.ModuleDefinition
import org.apache.maven.project.MavenProject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.*
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the ModulePackageMojo.
 *
 * This test class verifies the end-to-end functionality of the plugin
 * using either programmatically created test files or resources from
 * src/test/resources.
 */
class ModulePackageIntegrationTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var mojo: ModulePackageMojo
    private lateinit var mockProject: MavenProject
    private lateinit var jarsDir: File
    private lateinit var docDir: File

    @BeforeEach
    fun setUp() {
        // Setup directory structure
        jarsDir = File(tempDir, "jars")
        jarsDir.mkdirs()

        docDir = File(tempDir, "doc")
        docDir.mkdirs()

        // Create test JAR files
        createDummyJar(File(jarsDir, "module-gateway-1.0.0.jar"))
        createDummyJar(File(jarsDir, "module-client-1.0.0.jar"))
        createDummyJar(File(jarsDir, "module-designer-1.0.0.jar"))

        // Setup project
        mockProject = mock(MavenProject::class.java)
        `when`(mockProject.basedir).thenReturn(tempDir)
        `when`(mockProject.artifactId).thenReturn("test-module")
        `when`(mockProject.version).thenReturn("1.0.0")

        // Setup mojo
        mojo = ModulePackageMojo()
        setField(mojo, "project", mockProject)

        val outputDir = File(tempDir, "target")
        outputDir.mkdirs()
        setField(mojo, "outputDirectory", outputDir)

        // Copy test resources if available
        copyResourceIfExists("/license.html", File(tempDir, "license.html"))
        copyResourceIfExists("/doc/readme.html", File(docDir, "readme.html"))
    }

    @Test
    fun `full integration test with all module elements`() {
        // Setup complete module definition
        val module = ModuleDefinition(
            id = "com.example.test",
            name = "Test Module",
            description = "This is a test module for integration testing",
            version = "1.0.0",
            requiredIgnitionVersion = "8.1.45",
            requiredFrameworkVersion = "8",
            license = "license.html",
            documentation = "doc/readme.html",
            jars = listOf(
                JarDefinition("jars/module-gateway-1.0.0.jar", "G"),
                JarDefinition("jars/module-client-1.0.0.jar", "C"),
                JarDefinition("jars/module-designer-1.0.0.jar", "D")
            ),
            hooks = listOf(
                HookDefinition("com.example.GatewayHook", "G"),
                HookDefinition("com.example.ClientHook", "C"),
                HookDefinition("com.example.DesignerHook", "D")
            ),
            depends = listOf(
                DependsDefinition("com.inductiveautomation.vision", "G"),
                DependsDefinition("com.inductiveautomation.perspective", "C")
            )
        )

        // Create license.html and readme.html if they don't exist already
        if (!File(tempDir, "license.html").exists()) {
            createDummyFile(File(tempDir, "license.html"), "<html><body>License</body></html>")
        }
        if (!File(docDir, "readme.html").exists()) {
            createDummyFile(File(docDir, "readme.html"), "<html><body>Documentation</body></html>")
        }

        setField(mojo, "module", module)

        // Execute
        mojo.execute()

        // Verify modl file was created
        val modlFile = File(tempDir, "target/test-module-1.0.0.modl")
        assertTrue(modlFile.exists())

        // Verify modl file contents
        ZipFile(modlFile).use { zip ->
            // Verify module.xml
            val moduleXmlEntry = zip.getEntry("module.xml")
            assertNotNull(moduleXmlEntry)

            val moduleXmlContent = readEntryContent(zip, moduleXmlEntry)
            val document = parseXml(moduleXmlContent)

            // Check basic elements
            assertEquals("com.example.test", getElementValue(document, "id"))
            assertEquals("Test Module", getElementValue(document, "name"))
            assertEquals("This is a test module for integration testing", getElementValue(document, "description"))
            assertEquals("1.0.0", getElementValue(document, "version"))
            assertEquals("8.1.45", getElementValue(document, "requiredignitionversion"))
            assertEquals("8", getElementValue(document, "requiredframeworkversion"))
            assertEquals("license.html", getElementValue(document, "license"))
            assertEquals("doc/readme.html", getElementValue(document, "documentation"))

            // Check jar entries - verify only filenames are included (not paths)
            val jarElements = document.getElementsByTagName("jar")
            assertEquals(3, jarElements.length)

            // Verify that jar paths have been simplified to just filenames
            val jarNames = (0 until jarElements.length)
                .map { jarElements.item(it).textContent }
                .toSet()
            assertTrue(jarNames.contains("module-gateway-1.0.0.jar"))
            assertTrue(jarNames.contains("module-client-1.0.0.jar"))
            assertTrue(jarNames.contains("module-designer-1.0.0.jar"))

            // Check hook entries
            val hookElements = document.getElementsByTagName("hook")
            assertEquals(3, hookElements.length)

            // Check depends entries
            val dependsElements = document.getElementsByTagName("depends")
            assertEquals(2, dependsElements.length)

            // Check actual JAR files exist at the root level (not in subdirectories)
            assertNotNull(zip.getEntry("module-gateway-1.0.0.jar"))
            assertNotNull(zip.getEntry("module-client-1.0.0.jar"))
            assertNotNull(zip.getEntry("module-designer-1.0.0.jar"))
        }
    }

    @Test
    fun `integration test with minimal required fields and no optional elements`() {
        // Setup minimal module definition
        val module = ModuleDefinition(
            id = "com.example.minimal",
            name = "Minimal Module",
            version = "1.0.0",
            jars = listOf(
                JarDefinition("jars/module-gateway-1.0.0.jar", "G")
            ),
            hooks = listOf(
                HookDefinition("com.example.GatewayHook", "G")
            )
        )

        setField(mojo, "module", module)

        // Execute
        mojo.execute()

        // Verify modl file was created
        val modlFile = File(tempDir, "target/test-module-1.0.0.modl")
        assertTrue(modlFile.exists())

        // Verify modl file contents
        ZipFile(modlFile).use { zip ->
            // Verify module.xml
            val moduleXmlEntry = zip.getEntry("module.xml")
            assertNotNull(moduleXmlEntry)

            val moduleXmlContent = readEntryContent(zip, moduleXmlEntry)
            val document = parseXml(moduleXmlContent)

            // Check required elements
            assertEquals("com.example.minimal", getElementValue(document, "id"))
            assertEquals("Minimal Module", getElementValue(document, "name"))
            assertEquals("1.0.0", getElementValue(document, "version"))
            assertEquals("8.1.45", getElementValue(document, "requiredignitionversion"))
            assertEquals("8", getElementValue(document, "requiredframeworkversion"))

            // Check optional elements are not included
            assertFalse(moduleXmlContent.contains("<license>"))
            assertFalse(moduleXmlContent.contains("<documentation>"))

            // Check single JAR file
            assertNotNull(zip.getEntry("module-gateway-1.0.0.jar"))
        }
    }

    @Test
    fun `verify jar paths are properly extracted to filenames`() {
        // Setup module with complex jar paths
        val module = ModuleDefinition(
            id = "com.example.paths",
            name = "Path Test Module",
            version = "1.0.0",
            jars = listOf(
                JarDefinition("jars/module-gateway-1.0.0.jar", "G"),
                JarDefinition("some/deep/nested/path/with/subdirectories/module-client-1.0.0.jar", "C")
                // This path doesn't need to exist for the test - we just need to check the XML output
            ),
            hooks = listOf(
                HookDefinition("com.example.GatewayHook", "G")
            )
        )

        // Create a mock JAR file for the second entry to avoid file not found error
        val nestedDir = File(tempDir, "some/deep/nested/path/with/subdirectories")
        nestedDir.mkdirs()
        createDummyJar(File(nestedDir, "module-client-1.0.0.jar"))

        setField(mojo, "module", module)

        // Execute
        mojo.execute()

        // Verify modl file was created
        val modlFile = File(tempDir, "target/test-module-1.0.0.modl")
        assertTrue(modlFile.exists())

        // Verify modl file contents
        ZipFile(modlFile).use { zip ->
            val moduleXmlEntry = zip.getEntry("module.xml")
            assertNotNull(moduleXmlEntry)

            val moduleXmlContent = readEntryContent(zip, moduleXmlEntry)

            // Verify JAR entries only contain filenames, not paths
            assertTrue(moduleXmlContent.contains("<jar scope=\"G\">module-gateway-1.0.0.jar</jar>"))
            assertTrue(moduleXmlContent.contains("<jar scope=\"C\">module-client-1.0.0.jar</jar>"))

            // Verify the directory structure was not preserved in the modl file
            assertFalse(moduleXmlContent.contains("some/deep"))
            assertFalse(moduleXmlContent.contains("subdirectories"))

            // Verify JAR files exist at the root level
            assertNotNull(zip.getEntry("module-gateway-1.0.0.jar"))
            assertNotNull(zip.getEntry("module-client-1.0.0.jar"))
        }
    }

    // Helper methods
    private fun createDummyJar(file: File) {
        file.parentFile.mkdirs()
        JarOutputStream(file.outputStream()).use { jos ->
            // Add a dummy file to the JAR
            jos.putNextEntry(ZipEntry("dummy.txt"))
            jos.write("Dummy content".toByteArray())
            jos.closeEntry()
        }
    }

    private fun createDummyFile(file: File, content: String) {
        file.parentFile.mkdirs()
        OutputStreamWriter(file.outputStream()).use { writer ->
            writer.write(content)
        }
    }

    private fun setField(target: Any, fieldName: String, value: Any) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun readEntryContent(zipFile: ZipFile, entry: ZipEntry): String {
        val buffer = ByteArrayOutputStream()
        zipFile.getInputStream(entry).use { input ->
            input.copyTo(buffer)
        }
        return String(buffer.toByteArray())
    }

    private fun parseXml(xmlContent: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        return builder.parse(xmlContent.byteInputStream())
    }

    private fun getElementValue(document: org.w3c.dom.Document, tagName: String): String? {
        val elements = document.getElementsByTagName(tagName)
        return if (elements.length > 0) {
            elements.item(0).textContent
        } else {
            null
        }
    }

    /**
     * Copies a resource from the classpath to a file if the resource exists.
     * This allows tests to use pre-defined resources when available, but fall back
     * to programmatically created ones if not.
     */
    private fun copyResourceIfExists(resourcePath: String, destination: File) {
        val resourceUrl = javaClass.getResource(resourcePath)
        if (resourceUrl != null) {
            destination.parentFile.mkdirs()
            resourceUrl.openStream().use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}