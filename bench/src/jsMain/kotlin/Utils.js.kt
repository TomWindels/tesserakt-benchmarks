import js.Crypto
import js.FileSystem

private val ENCODING = run {
    val a: dynamic = Any()
    a.encoding = "utf8"
    a
}

actual fun List<String>.readContents(): List<String> {
    return map { FileSystem.readFileSync(it, ENCODING) }
}

actual fun String.md5(): String {
    return Crypto.createHash("md5").update(this).digest("hex")
}
