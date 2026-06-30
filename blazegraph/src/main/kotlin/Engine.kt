
import com.bigdata.rdf.sail.BigdataSail
import com.bigdata.rdf.sail.BigdataSailRepository
import org.openrdf.model.*
import org.openrdf.model.impl.BNodeImpl
import org.openrdf.model.impl.LiteralImpl
import org.openrdf.model.impl.StatementImpl
import org.openrdf.model.impl.URIImpl
import org.openrdf.query.QueryLanguage
import java.util.*
import kotlin.time.Duration
import kotlin.time.measureTime

class Engine(private val query: String) {

    private var duration = Duration.ZERO
    private var checksum = 0
    private var count = 0

    private val conn = try {
        repo
            .connection
    } catch (e: Throwable) {
        e.printStackTrace()
        throw e
    }

    init {
        // making sure there's no data in the repository
        conn.clear()
    }

    @JvmName("run")
    fun run() {
        conn.commit()
        count = 0
        checksum = 0
        duration = measureTime {
            val eval = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
            while (eval.hasNext()) {
                val results = eval.next()
                ++count
                results.forEach {
                    checksum += it.value.checksumValue
                }
            }
            eval.close()
        }
    }

    @JvmName("close")
    fun close() {
        conn.clear()
        conn.commit()
        conn.close()
    }

    @JvmName("getLastDuration")
    fun getLastDuration() = duration.inWholeMicroseconds / 1_000_000.0

    @JvmName("getLastChecksum")
    fun getLastChecksum() = checksum

    @JvmName("getLastCount")
    fun getLastCount() = count

    @JvmName("createNamedNode")
    fun createNamedNode(uri: String) = URIImpl(uri)

    @JvmName("createBlankNode")
    fun createBlankNode(id: Int) = BNodeImpl(id.toString())

    @JvmName("createTypedLiteralNode")
    fun createTypedLiteralNode(value: String, dtype: String) = LiteralImpl(value, URIImpl(dtype))

    @JvmName("createLangLiteralNode")
    fun createLangLiteralNode(value: String, tag: String) = LiteralImpl(value, tag)

    @JvmName("insertQuad")
    fun insertQuad(s: Any?, p: Any?, o: Any?) {
        val quad = StatementImpl(
            s as Resource,
            p as URIImpl,
            o as Value,
        )
        conn.add(quad)
    }

    @JvmName("removeQuad")
    fun removeQuad(s: Any?, p: Any?, o: Any?) {
        val quad = StatementImpl(
            s as Resource,
            p as URIImpl,
            o as Value,
        )
        conn.remove(quad)
    }

    companion object {
        init {
             System.setProperty("log4j.configuration", "log4j.properties")
        }
    }

}

private val properties = Properties().apply {
    // # changing the axiom model to none essentially disables all inference
    // com.bigdata.rdf.store.AbstractTripleStore.axiomsClass=com.bigdata.rdf.axioms.NoAxioms
    set("com.bigdata.rdf.store.AbstractTripleStore.axiomsClass", "com.bigdata.rdf.axioms.NoAxioms")
    // # RWStore (scalable single machine backend)
    // com.bigdata.journal.AbstractJournal.bufferMode=DiskRW
    set("com.bigdata.journal.AbstractJournal.bufferMode", "DiskRW")
    set("com.bigdata.journal.AbstractJournal.file", "/tmp/blazegraph/test.jnl")
    // # turn off automatic inference in the SAIL
    // com.bigdata.rdf.sail.truthMaintenance=false
    set("com.bigdata.rdf.sail.truthMaintenance", "false")
    // # don't store justification chains, meaning retraction requires full manual
    // # re-closure of the database
    // com.bigdata.rdf.store.AbstractTripleStore.justify=false
    set("com.bigdata.rdf.store.AbstractTripleStore.justify", "false")
    // # turn off the statement identifiers feature for provenance
    // com.bigdata.rdf.store.AbstractTripleStore.statementIdentifiers=false
    set("com.bigdata.rdf.store.AbstractTripleStore.statementIdentifiers", "false")
    // # turn off the free text index
    // com.bigdata.rdf.store.AbstractTripleStore.textIndex=false
    set("com.bigdata.rdf.store.AbstractTripleStore.textIndex", "false")
}

// keeping a single, reusable, repository active
private val repo by lazy {
//    BigdataSailRepository(BigdataSail("kb", Journal(properties).also { println(it.resourceLocator::class.java.name) }))
    BigdataSailRepository(BigdataSail(properties))
        .also { it.initialize() }
}

private val Value.checksumValue: Int
    get() = when (this) {
        is BNode -> 1
        is URI -> stringValue().length
        is Literal -> stringValue().length
        else -> throw IllegalArgumentException("Unknown value type ${this::class.simpleName}")
    }
