package university.cli.util

import kotlin.reflect.KClass

fun KClass<*>.loadResource(filePath: String): String =
    this.java.classLoader.getResource(filePath)?.readText()
        ?: throw IllegalArgumentException("File not found: $filePath")
