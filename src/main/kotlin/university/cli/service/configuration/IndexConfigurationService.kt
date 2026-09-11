package university.cli.service.configuration

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import university.cli.model.IndexConfiguration
import university.cli.util.FileUtil
import university.cli.util.JsonUtil
import university.cli.util.loadResource
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path

class IndexConfigurationService(
    private val json: Json = JsonUtil.json,
) {
    private val configurations by lazy(::loadConfigurations)

    private companion object {
        const val CONFIGURATION_DIRECTORY = "configurations/indexing"
        const val DEFAULT_CONFIGURATION = "default"
        const val JSON_EXTENSION = ".json"
    }

    fun getAll(): Map<String, IndexConfiguration> = configurations

    fun get(id: Long): IndexConfiguration = checkNotNull(configurations.values.find { it.id == id }) {
        "Indexing configuration not found: $id"
    }

    fun findByHash(hash: String): IndexConfiguration? = configurations.values.find { it.hash == hash }

    fun default(): IndexConfiguration = checkNotNull(configurations[DEFAULT_CONFIGURATION]) {
        "Default indexing configuration not found: $DEFAULT_CONFIGURATION$JSON_EXTENSION"
    }

    private fun loadConfigurations(): Map<String, IndexConfiguration> {
        val files = configurationFiles()
        check(files.isNotEmpty()) { "No indexing configurations found in $CONFIGURATION_DIRECTORY" }

        val loaded = files.associate { fileName ->
            val name = fileName.removeSuffix(JSON_EXTENSION)
            val content = IndexConfigurationService::class.loadResource("$CONFIGURATION_DIRECTORY/$fileName")
            val configuration = json.decodeFromString<IndexConfiguration>(content)
                .copy(hash = FileUtil.sha256(content))
            name to configuration
        }

        val duplicateIds = loaded.values.groupBy(IndexConfiguration::id).filterValues { it.size > 1 }.keys
        check(duplicateIds.isEmpty()) { "Duplicate indexing configuration ids: ${duplicateIds.sorted()}" }
        return loaded.entries.sortedBy { it.value.id }.associate { it.toPair() }
    }

    private fun configurationFiles(): List<String> {
        val resources = IndexConfigurationService::class.java.classLoader.getResources(CONFIGURATION_DIRECTORY)
        return buildSet {
            while (resources.hasMoreElements()) {
                val resource = resources.nextElement()
                when (resource.protocol) {
                    "file" -> Files.list(Path.of(resource.toURI())).use { paths ->
                        paths.filter(Files::isRegularFile)
                            .map { it.fileName.toString() }
                            .filter { it.endsWith(JSON_EXTENSION, ignoreCase = true) }
                            .forEach(::add)
                    }

                    "jar" -> {
                        val connection = resource.openConnection() as JarURLConnection
                        connection.jarFile.entries().asSequence()
                            .map { it.name }
                            .filter { it.startsWith("$CONFIGURATION_DIRECTORY/") }
                            .map { it.removePrefix("$CONFIGURATION_DIRECTORY/") }
                            .filter { !it.contains('/') && it.endsWith(JSON_EXTENSION, ignoreCase = true) }
                            .forEach(::add)
                    }
                }
            }
        }.toList()
    }
}
