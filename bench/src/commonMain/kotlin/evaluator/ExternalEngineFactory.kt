package evaluator

import Path

expect class ExternalEngineFactory(path: Path): EngineFactory {

    override suspend fun new(query: String): Engine

    override fun close()

}
