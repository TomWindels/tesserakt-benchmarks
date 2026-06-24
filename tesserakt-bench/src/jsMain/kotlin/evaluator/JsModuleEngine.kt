@file:OptIn(ExperimentalWasmJsInterop::class)

package evaluator

import await
import bench.Benchmark
import dev.tesserakt.rdf.types.Quad
import kotlin.js.Promise
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class JsModuleEngine(private val instance: dynamic): Engine {

    private var sinceLastDataChange = TimeSource.Monotonic.markNow()

    override suspend fun process(delta: Benchmark.DataChange) {
        sinceLastDataChange = TimeSource.Monotonic.markNow()
        delta.insertions.forEach { insert(it) }
        delta.deletions.forEach { remove(it) }
    }

    override suspend fun evaluate(): Results {
        (instance.run() as Promise<dynamic>).await()
        val duration = (instance.getLastDuration() as JsNumber).milliseconds
        val checksum = (instance.getLastChecksum() as JsNumber).toInt()
        val count = (instance.getLastCount() as JsNumber).toInt()
        return Results(
            count = count,
            checksum = checksum,
            queryEvaluationDuration = duration,
            roundTripTime = sinceLastDataChange.elapsedNow(),
        )
    }

    private fun insert(quad: Quad) {
        instance.insertQuad(quad.s.getTerm(), quad.p.getTerm(), quad.o.getTerm())
    }

    private fun remove(quad: Quad) {
        instance.removeQuad(quad.s.getTerm(), quad.p.getTerm(), quad.o.getTerm())
    }

    private val _cache = mutableMapOf<Quad.Element, JsAny>()

    private fun Quad.Element.getTerm(): JsAny {
        return _cache.getOrPut(this) {
            when (this) {
                is Quad.BlankTerm -> instance.createBlankNode(id)
                is Quad.NamedTerm -> instance.createNamedNode(value)
                is Quad.LangString -> instance.createLangLiteralNode(value, language)
                is Quad.Literal -> instance.createTypedLiteralNode(value, type.value)
                Quad.DefaultGraph -> throw UnsupportedOperationException()
            }
        }
    }

}
