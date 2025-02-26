package industries.roseautomation.simplemodlpackager.model

// Data classes for jar and hook definitions
data class JarDefinition(
    var path: String? = null,
    var scope: String? = null
)

data class HookDefinition(
    var className: String? = null,
    var scope: String? = null
)

// Main module definition with default values
data class ModuleDefinition(
    var id: String? = null,
    var name: String? = null,
    var description: String? = null,
    var version: String? = null,
    var requiredIgnitionVersion: String = "8.1.45",  // Latest LTS version as default
    var requiredFrameworkVersion: String = "8",     // Compatible with Ignition 8.x
    var license: String = "license.html",
    var documentation: String = "readme.html",
    var jars: List<JarDefinition> = emptyList(),
    var hooks: List<HookDefinition> = emptyList()
)