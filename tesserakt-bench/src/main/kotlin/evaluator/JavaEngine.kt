package evaluator

import bench.Benchmark
import dev.tesserakt.rdf.types.Quad
import evaluator.JavaEngine.ExternalMethod.Companion.method
import java.io.File
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import java.util.jar.JarFile
import kotlin.reflect.KProperty
import kotlin.time.Duration.Companion.seconds


class JavaEngine(
    jar: File,
    query: String,
) : Engine {

    private val engine = getClassesFromJarFile(jar)
        .find { it.kotlin.qualifiedName == "Engine" }!!
        .constructors
        .find { it.parameters.size == 1 && it.parameters[0].type == String::class.java }!!
        .newInstance(query)

    // reflection methods

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

    // actual engine use

    private val cache = WeakHashMap<Quad.Element, Any>()

    override fun evaluate(): Results {
        run()
        val duration = getLastDuration() as Double
        val checksum = getLastChecksum() as Int
        val count = getLastCount() as Int
        return Results(
            count = count,
            checksum = checksum,
            duration = duration.seconds,
        )
    }

    override fun process(delta: Benchmark.DataChange) {
        delta.insertions.forEach { insert(it) }
        delta.deletions.forEach { remove(it) }
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

    private class ExternalMethod(private val engine: Any, private val args: Array<Class<*>>) {

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
                    throw Error("Failed to invoke ${method}", e)
                }
            }

        }

        operator fun getValue(thisRef: Any?, property: KProperty<*>): Invokable {
            val method = engine::class
                .java
                .methods
                .find {
                    it.name == property.name && it /*.also { println("$it\n${it.parameters.joinToString()}\n${it.parameterTypes.joinToString()}") } */.parameters.size == args.size && it.parameterTypes.contentEquals(
                        args
                    )
                }
                ?: throw NoSuchMethodError("Failed to find method `${property.name}(${args.joinToString { it.name }})` on class `${engine::class.qualifiedName}`")
            return Invokable(method = method)
        }

        companion object {
            fun Any.method(vararg args: Class<*>): ExternalMethod {
                return ExternalMethod(this, args.toList().toTypedArray())
            }
        }

    }

    companion object {

        // src: https://www.baeldung.com/jar-file-get-class-names
        private fun getClassNamesFromJarFile(givenFile: File): Set<String> {
            val classNames = mutableSetOf<String>()
            JarFile(givenFile).use { jar ->
                jar.entries().iterator().forEach { entry ->
                    if (entry.getName().endsWith(".class")) {
                        val className: String = entry.getName()
                            .replace("/", ".")
                            .replace(".class", "")
                        classNames.add(className)
                    }
                }
                return classNames
            }
        }

        private fun getClassesFromJarFile(jarFile: File): Set<Class<*>> {
            val classNames = getClassNamesFromJarFile(jarFile)
            val classes = mutableSetOf<Class<*>>()
            URLClassLoader.newInstance(
                arrayOf(URL("jar:file:$jarFile!/"))
            ).use { loader ->
                classNames.forEach { name ->
                    // skipping module files
                    if (!name.endsWith("module-info")) {
                        classes.add(loader.loadClass(name))
                    }
                }
            }
            return classes
        }

    }


}
