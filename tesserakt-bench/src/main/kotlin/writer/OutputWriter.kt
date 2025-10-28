package writer

import java.io.File
import java.io.FileOutputStream

class OutputWriter(target: File, type: ResultEntry.Type) : AutoCloseable {

    private val stream = FileOutputStream(
        target
            .also { check(!it.exists()) { "Tried to write to a file that already exists: `${it.absolutePath}`"} }
    ).bufferedWriter()

    init {
        stream.write(type.CSV_HEADER)
    }

    fun append(result: ResultEntry) {
        stream.write("\n")
        stream.write(result.toCsv())
    }

    override fun close() {
        stream.close()
    }

}
