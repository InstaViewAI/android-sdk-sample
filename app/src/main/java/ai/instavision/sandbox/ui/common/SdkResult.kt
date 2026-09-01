package ai.instavision.sandbox.ui.common

import ai.instavision.network.data.entity.ApiError
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Fallback text for a failure that arrived without a message of its own. */
private const val GENERIC_FAILURE = "Something went wrong"

/** Carries the SDK's [ApiError] through [Result.failure], which can only hold a [Throwable]. */
class SdkException(val error: ApiError) : Exception(error.message)

/**
 * Suspends until the SDK's callback fires, turning any callback-shaped SDK method into a
 * coroutine that returns a [Result].
 *
 * Guardian's services all take the same `onSuccess` / `onError` pair, so a whole ViewModel can be
 * written against this one helper:
 *
 * ```
 * viewModelScope.launch {
 *   sdkCall<List<Space>> { onSuccess, onError ->
 *     InstaVision.spaceServices.getSpaces(onSuccess = onSuccess, onError = onError)
 *   }
 *     .onSuccess { SessionStore.putSpaces(it) }
 *     .onFailure { error = it.userMessage() }
 * }
 * ```
 *
 * The failure is an [SdkException] wrapping the SDK's own [ApiError], so nothing is lost on the
 * way through [Result]. A service that calls back more than once settles the coroutine exactly
 * once and drops the rest, and cancelling the calling scope abandons the wait without resuming.
 */
suspend fun <T> sdkCall(
  block: (onSuccess: (T) -> Unit, onError: (ApiError) -> Unit) -> Unit,
): Result<T> = suspendCancellableCoroutine { continuation ->
  val settled = AtomicBoolean(false)
  block(
    { value -> if (settled.compareAndSet(false, true)) continuation.resume(Result.success(value)) },
    { apiError ->
      if (settled.compareAndSet(false, true)) {
        continuation.resume(Result.failure(SdkException(apiError)))
      }
    },
  )
}

/** The text to put in front of the user for a failure, preferring the backend's own wording. */
fun Throwable.userMessage(): String = when (this) {
  is SdkException -> error.message
  else -> message ?: GENERIC_FAILURE
}
