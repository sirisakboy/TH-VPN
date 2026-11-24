package com.example.h25vpn.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Finite sequential attempts. A cancelled/failed attempt can never become a winner. */
object RouteScanRunner {
    suspend fun <T> firstPassing(
        routes: List<T>,
        attempt: suspend (T, Int) -> Unit,
        failed: suspend (T, Int, Exception) -> Unit
    ): T? {
        for ((index, route) in routes.withIndex()) {
            currentCoroutineContext().ensureActive()
            try {
                attempt(route, index)
                currentCoroutineContext().ensureActive()
                return route
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(route, index, e)
            }
        }
        return null
    }
}
