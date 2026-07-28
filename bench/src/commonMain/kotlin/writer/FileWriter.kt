package writer

import Path


expect class FileWriter(target: Path) : AutoCloseable {

    fun append(text: String)

    override fun close()

}
