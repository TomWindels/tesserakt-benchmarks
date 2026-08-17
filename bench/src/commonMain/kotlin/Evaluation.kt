import bench.Benchmark
import bench.replay.ReplayBench
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.serialization.trig.TriG
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
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
                    // on the final iteration, we can also try to get a report written
                    // the report only uses 1 .. N iterations as comparison point, so we can only write one if there
                    //  are more than one deltas to write about
                    if (bench.changes.size < 2) {
                        return@use
                    }
                    factory.new(query).use { reportingEngine ->
                        reportingEngine.process(bench.changes.first())
                        reportingEngine.evaluate()
                        reportingEngine.beginReport()
                        (1 ..< bench.changes.size).forEach { changeIndex ->
                            reportingEngine.process(bench.changes[changeIndex])
                            reportingEngine.evaluate()
                        }
                        val report = reportingEngine.buildReport() ?: return@use
                        writer.writeReport(report)
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
                    val triples = Store(FileDataSource(input.absolutePath), TriG)
                    repeat(iterations) {
                        factory.new(query).use { evaluator ->
                            var index = 0
                            val iter = triples.iterator()
                            while (iter.hasNext()) {
                                val update = buildSet(updateSize) {
                                    var i = 0
                                    while (i < updateSize && iter.hasNext()) {
                                        add(iter.next())
                                        ++i
                                    }
                                }
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

                    factory.new(query).use { reportingEngine ->
                        reportingEngine.evaluate()
                        reportingEngine.beginReport()
                        val iter = triples.iterator()
                        while (iter.hasNext()) {
                            val update = buildSet(updateSize) {
                                var i = 0
                                while (i < updateSize && iter.hasNext()) {
                                    add(iter.next())
                                    ++i
                                }
                            }
                            val change = object: Benchmark.DataChange {
                                override val insertions: Set<Quad>
                                    get() = update
                                override val deletions: Set<Quad>
                                    get() = emptySet()
                            }
                            reportingEngine.process(change)
                            // goes unused as we only care about the report
                            reportingEngine.evaluate()
                        }
                        val report = reportingEngine.buildReport() ?: return@use
                        writer.writeReport(report)
                    }
                }
            }
        }
    }
    factory.close()
}

suspend fun evaluateRegular(
    lib: Path,
    queries: List<String>,
    inputs: List<Path>,
    output: Path?,
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
                    val triples = Store(FileDataSource(input.absolutePath), TriG)
                    repeat(iterations) {
                        factory.new(query).use { evaluator ->
                            val change = object: Benchmark.DataChange {
                                override val insertions: Set<Quad>
                                    get() = triples
                                override val deletions: Set<Quad>
                                    get() = emptySet()
                            }
                            evaluator.process(change)
                            val results = evaluator.evaluate()
                            // NOTE: could be a non-indexed version as it's always 0
                            val entry = IndexedResultEntry(0, results)
                            writer.append(entry)
                        }
                    }

                    factory.new(query).use { reportingEngine ->
                        val change = object: Benchmark.DataChange {
                            override val insertions: Set<Quad>
                                get() = triples
                            override val deletions: Set<Quad>
                                get() = emptySet()
                        }
                        reportingEngine.process(change)
                        // goes unused as we only care about the report
                        reportingEngine.evaluate()
                        // we intentionally delayed it until now - even though change reporting is all wrong,
                        //  cardinalities are correct & used to form the actual join tree
                        reportingEngine.beginReport()
                        val report = reportingEngine.buildReport() ?: return@use
                        writer.writeReport(report)
                    }
                }
            }
        }
    }
    factory.close()
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
        it.printStackTrace()
        if (failFast) {
            throw it
        }
    }
}
