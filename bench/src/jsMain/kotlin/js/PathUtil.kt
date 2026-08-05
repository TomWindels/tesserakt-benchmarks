package js

@JsModule("path")
external object PathUtil {

    fun join(parent: String, child: String): String

    fun resolve(path: String): String

    fun dirname(path: String): String

    fun basename(path: String): String

    fun parse(path: String): ParsedPath

    class ParsedPath {
        val name: String
    }

}
