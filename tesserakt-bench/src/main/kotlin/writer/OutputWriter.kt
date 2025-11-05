package writer

import java.io.File

interface OutputWriter : AutoCloseable {

    fun append(result: ResultEntry)

}

fun OutputWriter(target: File?, type: ResultEntry.Type) = if (target != null) {
    FileOutputWriter(target, type)
} else {
    NoOpOutputWriter
}
