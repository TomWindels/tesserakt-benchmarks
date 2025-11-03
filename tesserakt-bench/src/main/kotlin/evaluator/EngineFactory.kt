package evaluator

interface EngineFactory {

    fun new(query: String): Engine

}
