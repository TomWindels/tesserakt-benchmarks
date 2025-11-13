package writer

import Path

expect class FileOutputWriter(target: Path, type: ResultEntry.Type) : OutputWriter {

    override fun append(result: ResultEntry)

    override fun close()

}
