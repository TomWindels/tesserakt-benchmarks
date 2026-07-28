package evaluator

import bench.Benchmark
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import dev.tesserakt.rdf.types.Quad
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class NativeEngineFactory(lib: File): EngineFactory {

    @Suppress("FunctionName")
    private interface NativeImplementation : Library {

        fun create_evaluator(query: String): Pointer

        fun exec_evaluator(evaluator: Pointer)

        fun get_last_duration(evaluator: Pointer): Double

        fun get_last_checksum(evaluator: Pointer): Int

        fun get_last_count(evaluator: Pointer): Int

        fun create_named_node(uri: String): Pointer

        fun create_blank_node(id: Int): Pointer

        fun create_typed_literal_node(value: String, dtype: String): Pointer

        fun create_lang_literal_node(value: String, tag: String): Pointer

        fun dispose_node(node: Pointer)

        fun dispose_evaluator(evaluator: Pointer)

        fun insert_quad(evaluator: Pointer, s: Pointer, p: Pointer, o: Pointer)

        fun remove_quad(evaluator: Pointer, s: Pointer, p: Pointer, o: Pointer)

    }

    private val impl: NativeImplementation = run {
        System.setProperty("jna.library.path", lib.parentFile.absolutePath)
        if (Platform.isWindows()) {
            Native.load(lib.nameWithoutExtension, NativeImplementation::class.java)
        } else {
            Native.load(lib.nameWithoutExtension.drop(3), NativeImplementation::class.java)
        }
    }

    override suspend fun new(query: String): Engine = Instance(query = query)

    private inner class Instance(query: String) : Engine {

        private var sinceLastDataChange = TimeSource.Monotonic.markNow()
        private val ptr = impl.create_evaluator(query)

        private val cache = mutableMapOf<Quad.Element, Pointer>()

        override suspend fun process(delta: Benchmark.DataChange) {
            // making sure all terms we could need during this evaluation has crossed the JNI border first
            delta.insertions.forEach { quad ->
                quad.s.toNativeElement()
                quad.p.toNativeElement()
                quad.o.toNativeElement()
            }
            delta.deletions.forEach { quad ->
                quad.s.toNativeElement()
                quad.p.toNativeElement()
                quad.o.toNativeElement()
            }
            // only now we start constructing terms & measure the time it takes
            sinceLastDataChange = TimeSource.Monotonic.markNow()
            delta.insertions.forEach { insert(it) }
            delta.deletions.forEach { remove(it) }
        }

        override suspend fun evaluate(): Results {
            impl.exec_evaluator(ptr)
            val duration = impl.get_last_duration(ptr).seconds
            val checksum = impl.get_last_checksum(ptr)
            val count = impl.get_last_count(ptr)
            return Results(
                queryEvaluationDuration = duration,
                checksum = checksum,
                count = count,
                roundTripTime = sinceLastDataChange.elapsedNow(),
            )
        }

        override fun close() {
            cache.values.forEach { impl.dispose_node(it) }
            cache.clear()
            impl.dispose_evaluator(ptr)
        }

        private fun insert(quad: Quad) {
            impl.insert_quad(
                evaluator = ptr,
                s = quad.s.toNativeElement(),
                p = quad.p.toNativeElement(),
                o = quad.o.toNativeElement(),
            )
        }

        private fun remove(quad: Quad) {
            impl.remove_quad(
                evaluator = ptr,
                s = quad.s.toNativeElement(),
                p = quad.p.toNativeElement(),
                o = quad.o.toNativeElement(),
            )
        }

        private fun Quad.Element.toNativeElement(): Pointer {
            return cache.getOrPut(this) {
                when (this) {
                    is Quad.BlankTerm -> impl.create_blank_node(id)
                    is Quad.NamedTerm -> impl.create_named_node(value)
                    is Quad.LangString -> impl.create_lang_literal_node(value, language)
                    is Quad.Literal -> impl.create_typed_literal_node(value, type.value)
                    Quad.DefaultGraph -> throw UnsupportedOperationException()
                }
            }
        }

    }

}
