import js.FileSystem
import js.PathUtil

private val RECURSIVE = run {
    val a: dynamic = Any()
    a.recursive = true
    a
}

actual class Path actual constructor(absolutePath: String) {

    actual constructor(parent: Path, child: String): this(absolutePath = PathUtil.join(parent.absolutePath, child))

    actual val absolutePath: String = PathUtil.resolve(absolutePath)

    actual val parentPath: Path
        get() = Path(PathUtil.dirname(absolutePath))

    actual val isDirectory: Boolean
        get() = FileSystem.lstatSync(absolutePath).isDirectory()

    actual val name: String
        get() = PathUtil.basename(absolutePath)

    actual val nameWithoutExtension: String
        get() = PathUtil.parse(absolutePath).name

    actual fun canWrite() = runCatching { FileSystem.accessSync(absolutePath, FileSystem.Constants.writeOk) }.isSuccess

    actual fun mkdirs() {
        FileSystem.mkdirSync(absolutePath, RECURSIVE)
    }

    actual fun exists(): Boolean {
        return FileSystem.existsSync(absolutePath)
    }

    override fun toString() = "`${absolutePath}`"
}
