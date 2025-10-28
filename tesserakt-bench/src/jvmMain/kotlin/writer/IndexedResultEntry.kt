package writer

import kotlin.time.Duration

data class IndexedResultEntry(
    val index: Int,
    val count: Int,
    val checksum: Int,
    val duration: Duration
): ResultEntry {

    companion object: ResultEntry.Type {
        override val CSV_HEADER: String = "index,duration(ms),total,checksum"
    }

    override fun toCsv(): String = "$index,${duration.inWholeMicroseconds / 1000.0},$count,$checksum"

}
