package university.cli.service.configuration

import kotlinx.serialization.json.Json
import university.cli.model.QueryProfile
import university.cli.util.JsonUtil
import university.cli.util.loadResource
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path

class QueryProfileService(
    private val json: Json = JsonUtil.json,
) {
    private val configurations by lazy(::loadConfigurations)

    private companion object {
        const val CONFIGURATION_DIRECTORY = "configurations/query"
        const val JSON_EXTENSION = ".json"
    }

    fun getAll(): Map<String, QueryProfile> = configurations

    fun get(id: Long): QueryProfile = checkNotNull(configurations.values.find { it.id == id }) {
        "Query profile not found: $id"
    }

    private fun loadConfigurations(): Map<String, QueryProfile> {
        val files = configurationFiles()
        check(files.isNotEmpty()) { "No query profiles found in $CONFIGURATION_DIRECTORY" }

        val loaded = files.associate { fileName ->
            val name = fileName.removeSuffix(JSON_EXTENSION)
            val content = QueryProfileService::class.loadResource("$CONFIGURATION_DIRECTORY/$fileName")
            name to json.decodeFromString<QueryProfile>(content)
        }
        val duplicateIds = loaded.values.groupBy(QueryProfile::id).filterValues { it.size > 1 }.keys
        check(duplicateIds.isEmpty()) { "Duplicate query profile ids: ${duplicateIds.sorted()}" }
        return loaded.entries.sortedBy { it.value.id }.associate { it.toPair() }
    }

    private fun configurationFiles(): List<String> {
        val resources = QueryProfileService::class.java.classLoader.getResources(CONFIGURATION_DIRECTORY)
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
