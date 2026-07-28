package evaluator

import kotlin.time.Duration

data class Results(
    val count: Int,
    val checksum: Int,
    val queryEvaluationDuration: Duration,
    val roundTripTime: Duration,
)
