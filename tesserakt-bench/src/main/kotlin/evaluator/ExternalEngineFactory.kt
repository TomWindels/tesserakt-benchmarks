package evaluator

import java.io.File

class ExternalEngineFactory(
    file: File,
): EngineFactory {

    private val inner = if (file.extension == "jar") JavaEngineFactory(file) else NativeEngineFactory(file)

    override fun new(query: String): Engine = inner.new(query)

}
