package evaluator

import bench.Benchmark
import dev.tesserakt.rdf.types.Quad
import java.io.File
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import kotlin.reflect.KProperty
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource


class JavaEngineFactory(jar: File) : EngineFactory {

    private val loader = URLClassLoader(arrayOf(URL("jar:file:$jar!/")))

    private val engineConstructor = loader.loadClass("Engine")
        .constructors
        .find { it.parameters.size == 1 && it.parameters[0].type == String::class.java }!!

    override suspend fun new(query: String): Engine {
        return Instance(engine = engineConstructor.newInstance(query))
    }

    override fun close() {
        super.close()
        loader.close()
    }

    private class Instance(engine: Any): Engine {

        // using reflection only once, and storing these methods by their name
        private val methods = engine::class
            .java
            .methods
            // ensuring we keep all overloads, just in case
            .groupBy { it.name }

        // reflected methods

        private val run by engine.method()
        private val getLastDuration by engine.method()
        private val getLastChecksum by engine.method()
        private val getLastCount by engine.method()
        private val createNamedNode by engine.method(String::class.java)
        private val createBlankNode by engine.method(Int::class.java)
        private val createTypedLiteralNode by engine.method(String::class.java, String::class.java)
        private val createLangLiteralNode by engine.method(String::class.java, String::class.java)
        private val insertQuad by engine.method(Any::class.java, Any::class.java, Any::class.java)
        private val removeQuad by engine.method(Any::class.java, Any::class.java, Any::class.java)
        // optional methods, so may throw 'no such method error's when used that we can safely ignore
        private val startReport by engine.method()
        private val createReport by engine.method()
        // it's not necessary to have this method defined, so it's wrapped in a try-catch in case it's not defined
        private val close = runCatching { val close by engine.method(); close }.getOrNull()

        // actual engine use

        private var sinceLastDataChange = TimeSource.Monotonic.markNow()
        private val cache = WeakHashMap<Quad.Element, Any>()

        override suspend fun evaluate(): Results {
            run()
            val duration = getLastDuration() as Double
            val checksum = getLastChecksum() as Int
            val count = getLastCount() as Int
            return Results(
                count = count,
                checksum = checksum,
                queryEvaluationDuration = duration.seconds,
                roundTripTime = sinceLastDataChange.elapsedNow()
            )
        }

        override suspend fun process(delta: Benchmark.DataChange) {
            // making sure all terms we could need during this evaluation has been sent to the external JAR first
            // unlikely this will make a noticable difference in measured performance compared to the JNI version,
            //  but this keeps it fair
            delta.insertions.forEach { quad ->
                getTerm(quad.s)
                getTerm(quad.p)
                getTerm(quad.o)
            }
            delta.deletions.forEach { quad ->
                getTerm(quad.s)
                getTerm(quad.p)
                getTerm(quad.o)
            }
            // only now we start constructing terms & measure the time it takes
            sinceLastDataChange = TimeSource.Monotonic.markNow()
            delta.insertions.forEach { insert(it) }
            delta.deletions.forEach { remove(it) }
        }

        override suspend fun beginReport() {
            try {
                startReport()
            } catch (_: NoSuchMethodError) {
                // can be ignored - not supported by the engine implementation
            }
        }

        override suspend fun buildReport(): String? {
            return try {
                createReport()
            } catch (_: NoSuchMethodError) {
                // can be ignored - not supported by the engine implementation
                return null
            } as String
        }

        override fun close() {
            super.close()
            close?.invoke()
        }

        private fun insert(quad: Quad) {
            insertQuad(getTerm(quad.s), getTerm(quad.p), getTerm(quad.o))
        }

        private fun remove(quad: Quad) {
            removeQuad(getTerm(quad.s), getTerm(quad.p), getTerm(quad.o))
        }

        private fun getTerm(term: Quad.Element): Any {
            return cache.getOrPut(term) {
                when (term) {
                    is Quad.BlankTerm -> createBlankNode(term.id)
                        ?: throw IllegalStateException("Failed to create blank node for $term")

                    is Quad.NamedTerm -> createNamedNode(term.value)
                        ?: throw IllegalStateException("Failed to create named node for $term")

                    is Quad.LangString -> createLangLiteralNode(term.value, term.language)
                        ?: throw IllegalStateException("Failed to create literal (with language term) for $term")

                    is Quad.Literal -> createTypedLiteralNode(term.value, term.type.value)
                        ?: throw IllegalStateException("Failed to create literal (with data type) for $term")

                    Quad.DefaultGraph -> throw UnsupportedOperationException()
                }
            }
        }

        private fun Any.method(vararg args: Class<*>): ExternalMethod {
            return ExternalMethod(this, args.toList().toTypedArray())
        }

        private inner class ExternalMethod(private val engine: Any, private val args: Array<Class<*>>) {

            inner class Invokable(
                private val method: Method,
            ) {

                operator fun invoke(vararg arg: Any): Any? {
                    if (arg.size != args.size) {
                        throw IllegalArgumentException("Mismatching number of arguments supplied: ${arg.size} received, ${args.size} expected")
                    }
                    arg.forEachIndexed { i, arg ->
                        if (!args[i].isAssignableFrom(arg::class.java)) {
                            throw IllegalArgumentException("Argument $i ($arg) is of incorrect type: ${arg::class.qualifiedName} received, ${args[i].name} expected")
                        }
                    }
                    return try {
                        method.invoke(engine, *arg)
                    } catch (e: Throwable) {
                        throw Error("Failed to invoke ${method} with arguments ${arg.joinToString()}", e)
                    }
                }

            }

            operator fun getValue(thisRef: Any?, property: KProperty<*>): Invokable {
                val method = methods[property.name]
                    ?.find { method ->
                        method.parameters.size == args.size &&
                        method.parameterTypes.contentEquals(args)
                    }
                    ?: throw NoSuchMethodError("Failed to find method `${property.name}(${args.joinToString { it.name }})` on class `${engine::class.qualifiedName}`")
                return Invokable(method = method)
            }

        }
    }

}
