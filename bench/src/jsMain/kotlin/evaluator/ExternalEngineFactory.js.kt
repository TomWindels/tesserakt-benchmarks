package evaluator

import Path
import await
import kotlin.js.Promise

actual class ExternalEngineFactory actual constructor(private val path: Path) : EngineFactory {

    actual override suspend fun new(query: String): Engine {
        val modulePath = path.absolutePath
        val factory = (js("import(modulePath)") as Promise<dynamic>).await().create
        val enginePromise = factory(query) as Promise<dynamic>
        return JsModuleEngine(enginePromise.await())
    }

}
