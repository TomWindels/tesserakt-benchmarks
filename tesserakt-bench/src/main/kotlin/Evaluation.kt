import bench.replay.ReplayBench
import evaluator.Engine
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
    val implementation = Engine(lib)
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
                    implementation.Evaluator(query).use { evaluator ->
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

private fun report(
    implementation: File,
    input: File,
    index: Int,
) {
    println("${implementation.nameWithoutExtension}, ${input.name} #${index}")
}
