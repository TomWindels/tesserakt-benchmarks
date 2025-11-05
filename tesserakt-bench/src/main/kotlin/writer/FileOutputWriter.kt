package writer

import java.io.File
import java.io.FileOutputStream

class FileOutputWriter(target: File, type: ResultEntry.Type) : OutputWriter {

    private val stream = FileOutputStream(
        target
            .also {
                check(!it.exists()) { "Tried to write to a file that already exists: `${it.absolutePath}`"}
                it.parentFile.mkdirs()
            }
    ).bufferedWriter()

    init {
        stream.write(type.CSV_HEADER)
    }

    override fun append(result: ResultEntry) {
        stream.write("\n")
        stream.write(result.toCsv())
    }

    override fun close() {
        stream.close()
    }

}
