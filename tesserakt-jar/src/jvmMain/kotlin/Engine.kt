import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.queryDeferred
import kotlin.time.Duration
import kotlin.time.measureTime

class Engine(query: String) {

    private val store = ObservableStore()
    private val evaluation = store.queryDeferred(Query.Select(query))

    private var duration = Duration.ZERO
    private var checksum = 0
    private var count = 0

    @JvmName("run")
    fun run() {
        val results: List<Bindings>
        duration = measureTime {
            results = evaluation.results.toList()
        }
        checksum = results.sumOf { binding -> binding.sumOf { it.second.checksumValue } }
        count = results.size
    }

    @JvmName("getLastDuration")
    fun getLastDuration() = duration.inWholeMicroseconds / 1_000_000.0

    @JvmName("getLastChecksum")
    fun getLastChecksum() = checksum

    @JvmName("getLastCount")
    fun getLastCount() = count

    @OptIn(ExperimentalStdlibApi::class)
    // making sure we return the value class, not its inlined representation
    @JvmExposeBoxed
    @JvmName("createNamedNode")
    fun createNamedNode(uri: String) = Quad.NamedTerm(uri)

    @OptIn(ExperimentalStdlibApi::class)
    // making sure we return the value class, not its inlined representation
    @JvmExposeBoxed
    @JvmName("createBlankNode")
    fun createBlankNode(id: Int) = Quad.BlankTerm(id)

    @JvmName("createTypedLiteralNode")
    fun createTypedLiteralNode(value: String, dtype: String) = Quad.Literal(value, Quad.NamedTerm(dtype))

    @JvmName("createLangLiteralNode")
    fun createLangLiteralNode(value: String, tag: String) = Quad.Literal(value, tag)

    @JvmName("insertQuad")
    fun insertQuad(s: Any?, p: Any?, o: Any?) {
        store.add(
            Quad(
                s = s as Quad.Subject,
                p = p as Quad.Predicate,
                o = o as Quad.Object,
            )
        )
    }

    @JvmName("removeQuad")
    fun removeQuad(s: Any?, p: Any?, o: Any?) {
        store.remove(
            Quad(
                s = s as Quad.Subject,
                p = p as Quad.Predicate,
                o = o as Quad.Object,
            )
        )
    }

}

private val Quad.Element.checksumValue: Int get() = when (this) {
    is Quad.BlankTerm -> 1
    is Quad.NamedTerm -> value.length
    is Quad.LangString -> value.length
    is Quad.Literal -> value.length
    Quad.DefaultGraph -> throw UnsupportedOperationException()
}
