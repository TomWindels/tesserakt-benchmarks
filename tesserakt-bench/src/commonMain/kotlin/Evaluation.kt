import bench.Benchmark
import bench.replay.ReplayBench
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.Quad
import evaluator.ExternalEngineFactory
import writer.*
import kotlin.time.measureTime

suspend fun evaluateReplay(
    lib: Path,
    inputs: List<Path>,
    output: Path?,
    iterations: Int,
    failFast: Boolean,
) {
    val factory = ExternalEngineFactory(lib)
    inputs.forEach { input ->
        val bench = try {
            ReplayBench(input.absolutePath)
        } catch(t: Throwable) {
            println("Failed to use input file `${input.absolutePath}`: caught ${t::class.simpleName}\n${t.stackTraceToString()}")
            return@forEach
        }
        bench.queries.forEach { query ->
            val code = query.md5()
            report(
                implementation = lib,
                input = input,
                code = code,
                failFast = failFast,
            ) {
                OutputWriter(
                    target = inputToOutputDir(
                        outputFolder = output,
                        inputFile = input,
                        implementation = lib,
                        code = code,
                        metadata = ReplayEvaluationMetadata(query = query, diffs = bench.changes)
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
    factory.close()
}

class ReplayEvaluationMetadata(
    val query: String,
    val diffs: Iterable<Benchmark.DataChange>,
) : Metadata {
    override fun MetadataWriteContext.write() {
        file("query.rq") {
            append(query)
        }
        file("diffs.csv") {
            append("additions,deletions")
            diffs.forEach { diff ->
                append("\n${diff.insertions.size},${diff.deletions.size}")
            }
        }
    }
}

suspend fun evaluateStream(
    lib: Path,
    queries: List<String>,
    inputs: List<Path>,
    output: Path?,
    updateSize: Int,
    iterations: Int,
    failFast: Boolean,
) {
    val factory = ExternalEngineFactory(lib)
    inputs.forEach { input ->
        queries.forEach { query ->
            // documented algorithm, and thus consistent hash codes can be expected
            val code = query.md5()
            report(
                implementation = lib,
                input = input,
                code = code,
                failFast = failFast
            ) {
                OutputWriter(
                    target = inputToOutputDir(
                        outputFolder = output,
                        inputFile = input,
                        implementation = lib,
                        code = code,
                        metadata = NoMetadata,
                    ),
                    type = IndexedResultEntry,
                ).use { writer ->
                    repeat(iterations) {
                        val triples = TriGSerializer.deserialize(FileDataSource(input.absolutePath))
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
    }
    factory.close()
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

private suspend inline fun report(
    implementation: Path,
    input: Path,
    failFast: Boolean,
    crossinline block: suspend () -> Unit,
) {
    report(
        text = "${implementation.nameWithoutExtension}, ${input.name}...",
        failFast = failFast,
        block = block
    )
}

private suspend inline fun report(
    implementation: Path,
    input: Path,
    code: String,
    failFast: Boolean,
    crossinline block: suspend () -> Unit,
) {
    report(
        text = "${implementation.nameWithoutExtension}, ${input.name}, $code...",
        failFast = failFast,
        block = block
    )
}

private suspend inline fun report(
    implementation: Path,
    input: Path,
    index: Int,
    failFast: Boolean,
    crossinline block: suspend () -> Unit,
) {
    report(
        text = "${implementation.nameWithoutExtension}, ${input.name} #${index}...",
        failFast = failFast,
        block = block
    )
}

private suspend inline fun report(
    text: String,
    failFast: Boolean,
    crossinline block: suspend () -> Unit,
) {
    print(text)
    val result: Result<Unit>
    val duration = measureTime {
        result = runCatching {
            block()
        }
    }
    result.onSuccess {
        println(" ok (took $duration)")
    }
    result.onFailure {
        println(" failed [${it::class.simpleName} - ${it.message}] (took $duration)")
        if (failFast) {
            throw it
        }
    }
}
