package writer

import Path

private val fs = js("require(\"fs\")")

private val APPEND_MODE = run {
    val a: dynamic = Any()
    a.flag = "a"
    a
}


actual class FileOutputWriter actual constructor(
    private val target: Path,
    type: ResultEntry.Type,
) : OutputWriter {


    init {
        check(!target.exists()) { "Tried to write to a file that already exists: `${target.absolutePath}`"}
        target.parentPath.mkdirs()
        fs.writeFileSync(target.absolutePath, type.CSV_HEADER)
    }

    actual override fun append(result: ResultEntry) {
        fs.writeFileSync(target.absolutePath, "\n" + result.toCsv(), APPEND_MODE)
    }

    actual override fun close() {
        // nothing to do
    }

}
