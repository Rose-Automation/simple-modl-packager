package industries.roseautomation.simplemodlpackager

import industries.roseautomation.simplemodlpackager.model.DependsDefinition
import industries.roseautomation.simplemodlpackager.model.HookDefinition
import industries.roseautomation.simplemodlpackager.model.JarDefinition
import industries.roseautomation.simplemodlpackager.model.ModuleDefinition
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.logging.Log
import org.apache.maven.project.MavenProject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.contains
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile

class ModulePackageMojoTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var mojo: ModulePackageMojo
    private lateinit var mockLog: Log
    private lateinit var mockProject: MavenProject

    @BeforeEach
    fun setUp() {
        mojo = ModulePackageMojo()

        mockLog = mock(Log::class.java)
        injectField(mojo, "log", mockLog)

        mockProject = mock(MavenProject::class.java)
        `when`(mockProject.basedir).thenReturn(tempDir)
        `when`(mockProject.artifactId).thenReturn("test-module")
        `when`(mockProject.version).thenReturn("1.0.0")
        injectField(mojo, "project", mockProject)

        injectField(mojo, "outputDirectory", tempDir)

        // Create test JAR files
        val gatewayJar = File(tempDir, "gateway.jar")
        JarOutputStream(gatewayJar.outputStream()).close()

        val clientJar = File(tempDir, "client.jar")
        JarOutputStream(clientJar.outputStream()).close()
    }

    @Test
    fun `validateRequiredFields should throw exception when module id is missing`() {
        // Set up a module with missing id
        val module = ModuleDefinition(
            name = "Test Module",
            version = "1.0.0"
        )
        injectField(mojo, "module", module)

        // Execute and verify
        val exception = assertThrows<MojoExecutionException> {
            try {
                callPrivateMethod(mojo, "validateRequiredFields")
            } catch (e: InvocationTargetException) {
                // Unwrap the exception to get the actual cause
                throw e.targetException
            }
        }

        assertTrue(exception.message?.contains("Module id is required") == true)
    }

    @Test
    fun `validateRequiredFields should throw exception when module name is missing`() {
        // Set up a module with missing name
        val module = ModuleDefinition(
            id = "com.example.test",
            version = "1.0.0"
        )
        injectField(mojo, "module", module)

        // Execute and verify
        val exception = assertThrows<MojoExecutionException> {
            try {
                callPrivateMethod(mojo, "validateRequiredFields")
            } catch (e: InvocationTargetException) {
                // Unwrap the exception to get the actual cause
                throw e.targetException
            }
        }

        assertTrue(exception.message?.contains("Module name is required") == true)
    }

    @Test
    fun `validateRequiredFields should throw exception when module version is missing`() {
        // Set up a module with missing version
        val module = ModuleDefinition(
            id = "com.example.test",
            name = "Test Module"
        )
        injectField(mojo, "module", module)

        // Execute and verify
        val exception = assertThrows<MojoExecutionException> {
            try {
                callPrivateMethod(mojo, "validateRequiredFields")
            } catch (e: InvocationTargetException) {
                // Unwrap the exception to get the actual cause
                throw e.targetException
            }
        }

        assertTrue(exception.message?.contains("Module version is required") == true)
    }

    @Test
    fun `validateScopes should log warnings for invalid or missing scopes`() {
        // Set up a module with invalid and missing scopes
        val module = ModuleDefinition(
            id = "com.example.test",
            name = "Test Module",
            version = "1.0.0",
            jars = listOf(
                JarDefinition("gateway.jar", "G"),  // Valid
                JarDefinition("client.jar", "X"),   // Invalid
                JarDefinition("designer.jar", null) // Missing
            ),
            hooks = listOf(
                HookDefinition("com.example.GatewayHook", "G"),  // Valid
                HookDefinition("com.example.ClientHook", "Y"),   // Invalid
                HookDefinition("com.example.DesignerHook", null) // Missing
            ),
            depends = listOf(
                DependsDefinition("com.inductiveautomation.vision", "G"),  // Valid
                DependsDefinition("com.inductiveautomation.opc", "Z"),     // Invalid
                DependsDefinition("com.inductiveautomation.perspective", null) // Missing
            )
        )
        injectField(mojo, "module", module)

        // Execute
        try {
            callPrivateMethod(mojo, "validateScopes")
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }

        // Verify warnings for invalid scopes
        verify(mockLog).warn(contains("Invalid scope 'X' for jar"))
        verify(mockLog).warn(contains("Invalid scope 'Y' for hook"))
        verify(mockLog).warn(contains("Invalid scope 'Z' for dependency"))

        // Verify warnings for missing scopes
        verify(mockLog).warn(contains("No scope specified for jar"))
        verify(mockLog).warn(contains("No scope specified for hook"))
        verify(mockLog).warn(contains("No scope specified for dependency"))
    }

    @Test
    fun `execute should create modl file with module xml and jars`() {
        // Set up
        val module = ModuleDefinition(
            id = "com.example.test",
            name = "Test Module",
            version = "1.0.0",
            jars = listOf(
                JarDefinition("gateway.jar", "G"),
                JarDefinition("client.jar", "C")
            )
        )
        injectField(mojo, "module", module)

        // Execute
        mojo.execute()

        // Verify modl file was created
        val modlFile = File(tempDir, "test-module-1.0.0.modl")
        assertTrue(modlFile.exists())

        // Verify modl file contents
        ZipFile(modlFile).use { zip ->
            // Check module.xml exists
            assertNotNull(zip.getEntry("module.xml"))

            // Check jar files exist
            assertNotNull(zip.getEntry("gateway.jar"))
            assertNotNull(zip.getEntry("client.jar"))
        }
    }

    @Test
    fun `execute should throw exception when jar file not found`() {
        // Setup
        val module = ModuleDefinition(
            id = "com.example.test",
            name = "Test Module",
            version = "1.0.0",
            jars = listOf(
                JarDefinition("gateway.jar", "G"),
                JarDefinition("nonexistent.jar", "C")
            )
        )
        injectField(mojo, "module", module)

        // Execute and verify
        val exception = assertThrows<MojoExecutionException> {
            mojo.execute()
        }

        assertTrue(exception.message?.contains("Failed to package module") == true)
        assertTrue(exception.cause?.message?.contains("Jar file not found") == true)
    }

    // Helper methods for reflection and testing private methods
    private fun injectField(target: Any, fieldName: String, value: Any) {
        val field = findField(target.javaClass, fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName)
            } catch (e: NoSuchFieldException) {
                currentClass = currentClass.superclass
            }
        }
        throw NoSuchFieldException("Field $fieldName not found in class hierarchy of ${clazz.name}")
    }

    private fun callPrivateMethod(target: Any, methodName: String, vararg args: Any): Any? {
        val method = target.javaClass.getDeclaredMethod(methodName)
        method.isAccessible = true
        return method.invoke(target, *args)
    }

    // Utility assertion methods
    private fun assertTrue(condition: Boolean) {
        if (!condition) {
            throw AssertionError("Expected condition to be true")
        }
    }

    private fun assertNotNull(obj: Any?) {
        if (obj == null) {
            throw AssertionError("Expected non-null value")
        }
    }
}