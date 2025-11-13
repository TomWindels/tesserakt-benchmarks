package evaluator

import bench.Benchmark

interface Engine: AutoCloseable {

    suspend fun process(delta: Benchmark.DataChange)

    suspend fun evaluate(): Results

    override fun close() {
        // no default implementation
    }

}
