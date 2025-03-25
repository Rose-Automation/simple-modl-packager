package industries.roseautomation.simplemodlpackager

import industries.roseautomation.simplemodlpackager.model.ModuleDefinition
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Maven Mojo for packaging an Ignition module (.modl file) for Inductive Automation's Ignition platform.
 * This plugin will create a .modl file (a ZIP file with module.xml and required JARs) based on the provided configuration.
 */
@Mojo(name = "package-module", defaultPhase = LifecyclePhase.PACKAGE)
class ModulePackageMojo : AbstractMojo() {
    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    private lateinit var project: MavenProject

    /**
     * The module definition containing information about the module to be packaged.
     */
    @Parameter(required = true)
    private lateinit var module: ModuleDefinition

    /**
     * The directory where the module package will be generated.
     */
    @Parameter(defaultValue = "\${project.build.directory}")
    private lateinit var outputDirectory: File

    /**
     * Executes the plugin to package the module.
     */
    @Throws(MojoExecutionException::class)
    override fun execute() {
        try {
            // Validate required fields
            validateRequiredFields()

            // Validate scopes
            validateScopes()

            // Ensure output directory exists
            outputDirectory.mkdirs()

            // Create temporary directory for module files
            val moduleDir = Files.createTempDirectory("ignition-module").toFile()
            moduleDir.deleteOnExit()

            // Generate module.xml
            val moduleXml = File(moduleDir, "module.xml")
            ModuleXmlGenerator().generateModuleXml(module, moduleXml)

            // Copy jar files to module directory
            module.jars.forEach { jarDef ->
                jarDef.path?.let { jarPath ->
                    val sourceJar = File(project.basedir, jarPath)
                    if (sourceJar.exists()) {
                        Files.copy(sourceJar.toPath(), File(moduleDir, sourceJar.name).toPath())
                    } else {
                        throw MojoExecutionException("Jar file not found: $jarPath")
                    }
                }
            }

            // Create .modl file (ZIP with different extension)
            val modlFile = File(outputDirectory, "${project.artifactId}-${project.version}.modl")
            ZipOutputStream(FileOutputStream(modlFile)).use { zos ->
                moduleDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val entryName = file.relativeTo(moduleDir).path
                        zos.putNextEntry(ZipEntry(entryName))
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }

            log.info("Created module package: ${modlFile.absolutePath}")
        } catch (e: Exception) {
            throw MojoExecutionException("Failed to package module", e)
        }
    }

    /**
     * Validates that all required fields are present in the module definition.
     */
    private fun validateRequiredFields() {
        if (module.id.isNullOrBlank()) {
            throw MojoExecutionException("Module id is required")
        }
        if (module.name.isNullOrBlank()) {
            throw MojoExecutionException("Module name is required")
        }
        if (module.version.isNullOrBlank()) {
            throw MojoExecutionException("Module version is required")
        }
        // requiredIgnitionVersion and requiredFrameworkVersion have default values so no need to check
    }

    /**
     * Validates that all scopes used in the module definition are valid.
     * Valid scopes are: G (Gateway), D (Designer), and C (Client).
     */
    private fun validateScopes() {
        val validScopes = setOf("G", "D", "C")

        // Check jar scopes
        module.jars.forEach { jar ->
            jar.scope?.let { scope ->
                if (scope !in validScopes) {
                    log.warn("Invalid scope '$scope' for jar ${jar.path}. Valid scopes are: $validScopes")
                }
            } ?: log.warn("No scope specified for jar ${jar.path}. Valid scopes are: $validScopes")
        }

        // Check hook scopes
        module.hooks.forEach { hook ->
            hook.scope?.let { scope ->
                if (scope !in validScopes) {
                    log.warn("Invalid scope '$scope' for hook ${hook.className}. Valid scopes are: $validScopes")
                }
            } ?: log.warn("No scope specified for hook ${hook.className}. Valid scopes are: $validScopes")
        }

        // Check depends scopes
        module.depends.forEach { depend ->
            depend.scope?.let { scope ->
                if (scope !in validScopes) {
                    log.warn("Invalid scope '$scope' for dependency ${depend.id}. Valid scopes are: $validScopes")
                }
            } ?: log.warn("No scope specified for dependency ${depend.id}. Valid scopes are: $validScopes")
        }
    }
}