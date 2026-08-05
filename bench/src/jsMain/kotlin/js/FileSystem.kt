package js

@JsModule("fs")
external object FileSystem {

    @JsName("constants")
    object Constants {
        @JsName("W_OK")
        val writeOk: dynamic
    }

    fun lstatSync(path: String): LstatResult

    fun accessSync(path: String, opts: dynamic): Boolean

    fun mkdirSync(path: String, opts: dynamic)

    fun existsSync(path: String): Boolean

    fun readFileSync(path: String, opts: dynamic): String

    fun writeFileSync(path: String, content: String, mode: dynamic)

    class LstatResult {
        fun isDirectory(): Boolean
    }

}

