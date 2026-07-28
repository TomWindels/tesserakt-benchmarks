import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

suspend fun <T> Promise<T>.await(): T = suspendCoroutine { continuation ->
    this.then { continuation.resume(it) }
    this.catch { continuation.resumeWithException(it) }
}
