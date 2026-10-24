package com.local.salarytracker.cloud

/**
 * Pure helpers that read PostgREST error answers (no Android / supabase code, so they are unit tested).
 * [text] is everything the error says (message, description, code ...).
 */
object SyncErrors {
    /** A network timeout (Ktor HttpRequestTimeoutException / ConnectTimeoutException, java.net.SocketTimeoutException) anywhere in the cause chain. */
    fun isTimeout(t: Throwable): Boolean {
        var c: Throwable? = t
        var depth = 0
        while (c != null && depth++ < 8) {
            if (c is java.net.SocketTimeoutException) return true
            val n = c.javaClass.simpleName
            if (n == "HttpRequestTimeoutException" || n == "ConnectTimeoutException" || n == "SocketTimeoutException") return true
            c = c.cause
        }
        return false
    }

    /** After a failed sync: keep the "Last synced" text (no problem) when nothing is waiting and the last good sync was under [recentMs] ago. */
    fun hideProblemAfterFailure(unsynced: Int, lastSyncMs: Long, nowMs: Long, recentMs: Long = 2 * 60_000L): Boolean =
        unsynced <= 0 && lastSyncMs > 0 && nowMs - lastSyncMs in 0 until recentMs

    /** The lending tables are not on the server yet (0003_lending.sql was not run): 404 / PGRST205 / 42P01. */
    fun isTableMissing(statusCode: Int, text: String): Boolean =
        statusCode == 404 || text.contains("PGRST205") || text.contains("42P01") ||
            (text.contains("schema cache", ignoreCase = true) && text.contains("Could not find", ignoreCase = true))

    /** A lending entry points at a person the server does not have (yet): FK violation 23503 (HTTP 409). */
    fun isForeignKeyViolation(statusCode: Int, text: String): Boolean =
        text.contains("23503") || (statusCode == 409 && !text.contains("23505"))

    /** The same row already exists (unique violation 23505). */
    fun isUniqueViolation(statusCode: Int, text: String): Boolean =
        statusCode == 409 || text.contains("23505")
}
