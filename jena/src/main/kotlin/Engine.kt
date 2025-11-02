import org.apache.jena.datatypes.BaseDatatype
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.*
import org.apache.jena.query.DatasetFactory
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.sparql.core.Quad
import kotlin.time.Duration
import kotlin.time.measureTime

class Engine(private val query: String) {

    private val store = DatasetFactory.create()

    private var duration = Duration.ZERO
    private var checksum = 0
    private var count = 0

    @JvmName("run")
    fun run() {
        count = 0
        checksum = 0
        duration = measureTime {
            QueryExecutionFactory.create(query, store).use { execution ->
                val solutions = execution.execSelect()
                while (solutions.hasNext()) {
                    val solution = solutions.nextSolution()
                    solution.varNames().forEach { name ->
                        checksum += solution[name]!!.checksumValue
                    }
                    ++count
                }
                solutions.close()
            }
        }
    }

    @JvmName("getLastDuration")
    fun getLastDuration() = duration.inWholeMicroseconds / 1_000_000.0

    @JvmName("getLastChecksum")
    fun getLastChecksum() = checksum

    @JvmName("getLastCount")
    fun getLastCount() = count

    @JvmName("createNamedNode")
    fun createNamedNode(uri: String) = NodeFactory.createURI(uri)

    @JvmName("createBlankNode")
    fun createBlankNode(id: Int) = NodeFactory.createBlankNode(id.toString())

    @JvmName("createTypedLiteralNode")
    fun createTypedLiteralNode(value: String, dtype: String) = NodeFactory.createLiteralDT(value, dtype.toDataType())

    @JvmName("createLangLiteralNode")
    fun createLangLiteralNode(value: String, tag: String) = NodeFactory.createLiteralLang(value, tag)

    @JvmName("insertQuad")
    fun insertQuad(s: Any?, p: Any?, o: Any?) {
        val graph = store.asDatasetGraph()
        graph.add(Quad.defaultGraphIRI, s as Node, p as Node, o as Node)
    }

    @JvmName("removeQuad")
    fun removeQuad(s: Any?, p: Any?, o: Any?) {
        val graph = store.asDatasetGraph()
        graph.delete(Quad.defaultGraphIRI, s as Node, p as Node, o as Node)
    }

}

private val RDFNode.checksumValue: Int get() = when (val variable = asNode()) {
    is Node_URI -> variable.uri.length
    is Node_Literal -> variable.literalValue.toString().length
    is Node_Blank -> 1
    else -> throw IllegalArgumentException("Unknown node type `${this::class.simpleName}`")
}

private fun String.toDataType() = when (this) {
    "http://www.w3.org/2001/XMLSchema#string" -> XSDDatatype.XSDstring
    "http://www.w3.org/2001/XMLSchema#boolean" -> XSDDatatype.XSDboolean
    "http://www.w3.org/2001/XMLSchema#int" -> XSDDatatype.XSDint
    "http://www.w3.org/2001/XMLSchema#integer" -> XSDDatatype.XSDinteger
    "http://www.w3.org/2001/XMLSchema#long" -> XSDDatatype.XSDlong
    "http://www.w3.org/2001/XMLSchema#float" -> XSDDatatype.XSDfloat
    "http://www.w3.org/2001/XMLSchema#double" -> XSDDatatype.XSDdouble
    "http://www.w3.org/2001/XMLSchema#duration" -> XSDDatatype.XSDduration
    "http://www.w3.org/2001/XMLSchema#dateTime" -> XSDDatatype.XSDdateTime
    "http://www.w3.org/2001/XMLSchema#time" -> XSDDatatype.XSDtime
    "http://www.w3.org/2001/XMLSchema#date" -> XSDDatatype.XSDdate
    else -> BaseDatatype(this)
}
