package writer

import Path

class FileOutputWriter(private val target: Path, type: ResultEntry.Type) : OutputWriter {

    private val writer = FileWriter(target)

    init {
        writer.append(type.CSV_HEADER)
    }

    override fun writeReport(report: String) {
        FileWriter(
            target = Path(target.parentPath, "${target.nameWithoutExtension}-report.txt")
        ).use { writer ->
            writer.append(report)
        }
    }

    override fun append(result: ResultEntry) {
        writer.append("\n${result.toCsv()}")
    }

    override fun close() {
        writer.close()
    }

}
