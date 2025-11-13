private val path = js("require(\"path\")")
private val fs = js("require(\"fs\")")

private val RECURSIVE = run {
    val a: dynamic = Any()
    a.recursive = true
    a
}

actual class Path actual constructor(absolutePath: String) {

    actual constructor(parent: Path, child: String): this(absolutePath = path.join(parent.absolutePath, child))

    actual val absolutePath: String = path.resolve(absolutePath)

    actual val parentPath: Path
        get() = Path(path.dirname(absolutePath))

    actual val isDirectory: Boolean
        get() = fs.lstatSync(absolutePath).isDirectory()

    actual val name: String
        get() = path.basename(absolutePath)

    actual val nameWithoutExtension: String
        get() = path.parse(absolutePath).name

    actual fun canWrite() = runCatching { fs.accessSync(absolutePath, fs.constants.W_OK) }.isSuccess

    actual fun mkdirs() {
        fs.mkdirSync(absolutePath, RECURSIVE)
    }

    actual fun exists(): Boolean {
        return fs.existsSync(absolutePath)
    }

    override fun toString() = "`${absolutePath}`"
}
