package evaluator

import bench.Benchmark
import java.io.File

class ExternalEngine(
    file: File,
    query: String,
): Engine {

    private val inner = if (file.extension == "jar") JavaEngine(file, query) else NativeEngine(file).Evaluator(query)

    override fun process(delta: Benchmark.DataChange) {
        return inner.process(delta)
    }

    override fun evaluate(): Results {
        return inner.evaluate()
    }

    override fun close() {
        inner.close()
    }

}
