package writer

import Path
import java.io.FileOutputStream

actual class FileWriter actual constructor(target: Path) : AutoCloseable {

    private val stream = FileOutputStream(
        target.asFile
            .also {
                check(!it.exists()) { "Tried to write to a file that already exists: `${it.absolutePath}`"}
                it.parentFile.mkdirs()
            }
    ).bufferedWriter()

    actual fun append(text: String) {
        stream.write(text)
    }

    actual override fun close() {
        stream.close()
    }

}
