package writer

import evaluator.Results

fun IndexedResultEntry(index: Int, result: Results) =
    IndexedResultEntry(
        index = index,
        count = result.count,
        checksum = result.checksum,
        duration = result.duration
    )
