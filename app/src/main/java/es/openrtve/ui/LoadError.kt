package es.openrtve.ui

import es.openrtve.data.HttpStatusException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface LoadError {
    data object Offline : LoadError
    data object Timeout : LoadError
    data class Http(val statusCode: Int) : LoadError
    data object Unknown : LoadError
}

fun Throwable.toLoadError(): LoadError = when (this) {
    is UnknownHostException -> LoadError.Offline
    is SocketTimeoutException -> LoadError.Timeout
    is HttpStatusException -> LoadError.Http(statusCode)
    else -> LoadError.Unknown
}
