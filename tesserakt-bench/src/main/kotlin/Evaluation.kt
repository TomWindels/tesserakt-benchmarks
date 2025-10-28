import bench.replay.ReplayBench
import evaluator.Engine
import writer.IndexedResultEntry
import writer.OutputWriter
import writer.inputToOutputDir
import java.io.File

fun evaluate(
    lib: File,
    replay: File,
    output: File,
) {
    val bench = ReplayBench(replay)
    val implementation = Engine(lib)
    bench.queries.forEachIndexed { qi, query ->
        OutputWriter(
            target = inputToOutputDir(
                outputFolder = output,
                inputFile = replay,
                index = qi
            ),
            type = IndexedResultEntry,
        ).use { writer ->
            repeat(10) {
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
