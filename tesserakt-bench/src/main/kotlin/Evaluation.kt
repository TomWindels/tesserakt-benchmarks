import bench.Benchmark
import bench.replay.ReplayBench
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.Quad
import evaluator.ExternalEngineFactory
import writer.IndexedResultEntry
import writer.OutputWriter
import writer.inputToOutputDir
import java.io.File

fun evaluateReplay(
    lib: File,
    inputs: List<File>,
    output: File,
    iterations: Int,
) {
    val factory = ExternalEngineFactory(lib)
    inputs.forEach { input ->
        val bench = ReplayBench(input)
        bench.queries.forEachIndexed { qi, query ->
            report(
                implementation = lib,
                input = input,
                index = qi,
            )
            OutputWriter(
                target = inputToOutputDir(
                    outputFolder = output,
                    inputFile = input,
                    implementation = lib,
                    index = qi
                ),
                type = IndexedResultEntry,
            ).use { writer ->
                repeat(iterations) {
                    val engine = factory.new(query)
                    engine.use { evaluator ->
                        bench.changes.forEachIndexed { di, diff ->
                            evaluator.process(diff)
                            val results = evaluator.evaluate()
                            val entry = IndexedResultEntry(di, results)
                            writer.append(entry)
                        }
                    }
                }
            }
        }
    }
}

fun evaluateStream(
    lib: File,
    query: String,
    inputs: List<File>,
    output: File,
    updateSize: Int,
    iterations: Int,
) {
    val factory = ExternalEngineFactory(lib)
    inputs.forEach { input ->
        report(lib, input)
        OutputWriter(
            target = inputToOutputDir(
                outputFolder = output,
                inputFile = input,
                implementation = lib,
            ),
            type = IndexedResultEntry,
        ).use { writer ->
            repeat(iterations) {
                val triples = TriGSerializer.deserialize(FileDataSource(input))
                factory.new(query).use { evaluator ->
                    var index = 0
                    while (triples.hasNext()) {
                        val update = triples.take(updateSize).toSet()
                        val change = object: Benchmark.DataChange {
                            override val insertions: Set<Quad>
                                get() = update
                            override val deletions: Set<Quad>
                                get() = emptySet()
                        }
                        evaluator.process(change)
                        val results = evaluator.evaluate()
                        val entry = IndexedResultEntry(index, results)
                        writer.append(entry)
                        ++index
                    }
                }
            }
        }
    }
}

private fun <T> Iterator<T>.take(size: Int): List<T> {
    return buildList(size) {
        repeat(size) {
            if (!hasNext()) {
                return@buildList
            }
            add(next())
        }
    }
}

private fun report(
    implementation: File,
    input: File,
) {
    println("${implementation.nameWithoutExtension}, ${input.name}")
}

private fun report(
    implementation: File,
    input: File,
    index: Int,
) {
    println("${implementation.nameWithoutExtension}, ${input.name} #${index}")
}
