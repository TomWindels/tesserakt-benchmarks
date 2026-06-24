package writer

import kotlin.time.Duration

data class IndexedResultEntry(
    val index: Int,
    val count: Int,
    val checksum: Int,
    val queryEvaluationDuration: Duration,
    val roundTripTime: Duration,
): ResultEntry {

    companion object: ResultEntry.Type {
        override val CSV_HEADER: String = "index,query_ms,roundtrip_ms,total,checksum"
    }

    override fun toCsv(): String = "$index,${queryEvaluationDuration.inWholeMicroseconds / 1000.0},${roundTripTime.inWholeMicroseconds / 1000.0},$count,$checksum"

}
