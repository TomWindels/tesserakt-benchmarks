package evaluator

import Path
import await
import kotlin.js.Promise

actual class ExternalEngineFactory actual constructor(path: Path) : EngineFactory {

    private val module = run {
        val moduleName = path.absolutePath
        js("require(moduleName)")
    }

    actual override suspend fun new(query: String): Engine {
        val enginePromise = module.create(query) as Promise<dynamic>
        return JsModuleEngine(enginePromise.await())
    }

}
