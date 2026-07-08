package bench.replay

import bench.Benchmark
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.serialization.trig.TriG
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark

class ReplayBench(filepath: String) : Benchmark {

    private val bench = ReplayBenchmark.from(Store(FileDataSource(filepath), TriG)).single()

    override val queries: List<String> get() = bench.queries

    override val changes: List<Benchmark.DataChange> = bench.store.diffs.map { (insertions, deletions) ->
        object : Benchmark.DataChange {
            override val insertions: Set<Quad> = insertions
            override val deletions: Set<Quad> = deletions
        }
    }

}
