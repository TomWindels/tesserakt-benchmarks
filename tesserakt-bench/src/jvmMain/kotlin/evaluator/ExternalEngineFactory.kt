package evaluator

import Path
import java.io.File

actual class ExternalEngineFactory(
    file: File,
): EngineFactory {

    actual constructor(path: Path): this(file = path.asFile)

    private val inner = if (file.extension == "jar") JavaEngineFactory(file) else NativeEngineFactory(file)

    actual override suspend fun new(query: String): Engine = inner.new(query)

}
