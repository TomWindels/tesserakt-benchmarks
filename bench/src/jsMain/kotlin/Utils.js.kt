private val fs = js("require(\"fs\")")

private val ENCODING = run {
    val a: dynamic = Any()
    a.encoding = "utf8"
    a
}

actual fun List<String>.readContents(): List<String> {
    return map { fs.readFileSync(it, ENCODING) }
}

private val crypto = js("require(\"crypto\")")

actual fun String.md5(): String {
    return crypto.createHash("md5").update(this).digest("hex")
}
