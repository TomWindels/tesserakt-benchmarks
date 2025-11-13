package evaluator

interface EngineFactory {

    suspend fun new(query: String): Engine

}
