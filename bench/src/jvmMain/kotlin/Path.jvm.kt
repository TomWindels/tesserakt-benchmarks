import java.io.File

actual class Path(val asFile: File) {

    actual constructor(absolutePath: String): this(asFile = File(absolutePath))

    actual constructor(parent: Path, child: String): this(asFile = File(parent.absolutePath, child))

    actual val absolutePath: String
        get() = asFile.absolutePath
    actual val parentPath: Path
        get() = Path(asFile.parent)
    actual val isDirectory: Boolean
        get() = asFile.isDirectory
    actual val name: String
        get() = asFile.name
    actual val nameWithoutExtension: String
        get() = asFile.nameWithoutExtension

    actual fun canWrite() = (!asFile.exists() || asFile.canWrite())

    actual fun exists() = asFile.exists()

    actual fun mkdirs() {
        asFile.mkdirs()
    }

    override fun toString() = "`${absolutePath}`"
}
