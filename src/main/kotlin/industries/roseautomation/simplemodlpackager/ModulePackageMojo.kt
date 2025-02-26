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

@Mojo(name = "package-module", defaultPhase = LifecyclePhase.PACKAGE)
class ModulePackageMojo : AbstractMojo() {
    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    private lateinit var project: MavenProject

    @Parameter(required = true)
    private lateinit var module: ModuleDefinition

    @Parameter(defaultValue = "\${project.build.directory}")
    private lateinit var outputDirectory: File

    @Throws(MojoExecutionException::class)
    override fun execute() {
        try {
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
}
