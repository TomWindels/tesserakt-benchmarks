package bench.replay

import bench.Benchmark
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark

class ReplayBench(filepath: String) : Benchmark {

    private val bench = ReplayBenchmark.from(TriGSerializer.deserialize(FileDataSource(filepath)).consume()).single()

    override val queries: List<String> get() = bench.queries

    override val changes: Iterable<Benchmark.DataChange> = bench.store.diffs.map { (insertions, deletions) ->
        object : Benchmark.DataChange {
            override val insertions: Set<Quad> = insertions
            override val deletions: Set<Quad> = deletions
        }
    }

}
