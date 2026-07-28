package evaluator

import bench.Benchmark

interface Engine: AutoCloseable {

    suspend fun process(delta: Benchmark.DataChange)

    suspend fun evaluate(): Results

    suspend fun beginReport() {
        // nothing to do
    }

    suspend fun buildReport(): String? {
        // nothing to do
        return null
    }

    override fun close() {
        // no default implementation
    }

}
