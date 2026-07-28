package evaluator

interface EngineFactory : AutoCloseable {

    suspend fun new(query: String): Engine

    override fun close() {
        /* nothing to do */
    }

}
