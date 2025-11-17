package writer

import Path

class FileOutputWriter(target: Path, type: ResultEntry.Type) : OutputWriter {

    private val writer = FileWriter(target)

    init {
        writer.append(type.CSV_HEADER)
    }

    override fun append(result: ResultEntry) {
        writer.append("\n${result.toCsv()}")
    }

    override fun close() {
        writer.close()
    }

}
