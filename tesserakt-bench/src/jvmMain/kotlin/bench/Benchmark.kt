package bench

import dev.tesserakt.rdf.types.Quad

interface Benchmark {

    val queries: Iterable<String>

    val changes: Iterable<DataChange>

    interface DataChange {

        val insertions: Set<Quad>

        val deletions: Set<Quad>

    }

}
