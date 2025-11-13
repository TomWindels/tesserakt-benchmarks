private val fs = js("require(\"fs\")")

private val ENCODING = run {
    val a: dynamic = Any()
    a.encoding = "utf8"
    a
}

actual fun List<String>.readContents(): List<String> {
    return map { fs.readFileSync(it, ENCODING) }
}
