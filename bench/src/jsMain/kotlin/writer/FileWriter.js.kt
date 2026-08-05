package writer

import Path
import js.FileSystem

private val APPEND_MODE = run {
    val a: dynamic = Any()
    a.flag = "a"
    a
}

actual class FileWriter actual constructor(private val target: Path) : AutoCloseable {

    init {
        check(!target.exists()) { "Tried to write to a file that already exists: `${target.absolutePath}`"}
        target.parentPath.mkdirs()
    }

    actual fun append(text: String) {
        FileSystem.writeFileSync(target.absolutePath, text, APPEND_MODE)
    }

    actual override fun close() {
        // nothing to do
    }

}
