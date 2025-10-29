package evaluator

import bench.Benchmark

interface Engine: AutoCloseable {

    fun process(delta: Benchmark.DataChange)

    fun evaluate(): Results

    override fun close() {
        // no default implementation
    }

}
