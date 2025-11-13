package writer

import Path
import java.io.FileOutputStream

actual class FileOutputWriter actual constructor(target: Path, type: ResultEntry.Type) : OutputWriter {

    private val stream = FileOutputStream(
        target.asFile
            .also {
                check(!it.exists()) { "Tried to write to a file that already exists: `${it.absolutePath}`"}
                it.parentFile.mkdirs()
            }
    ).bufferedWriter()

    init {
        stream.write(type.CSV_HEADER)
    }

    actual override fun append(result: ResultEntry) {
        stream.write("\n")
        stream.write(result.toCsv())
    }

    actual override fun close() {
        stream.close()
    }

}
