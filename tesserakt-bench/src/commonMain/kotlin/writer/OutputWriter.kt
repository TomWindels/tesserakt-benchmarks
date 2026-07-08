package writer

import Path

interface OutputWriter : AutoCloseable {

    fun append(result: ResultEntry)

    fun writeReport(report: String)

}

fun OutputWriter(
    target: Path?,
    type: ResultEntry.Type,
) = if (target != null) {
    FileOutputWriter(target, type)
} else {
    NoOpOutputWriter
}
