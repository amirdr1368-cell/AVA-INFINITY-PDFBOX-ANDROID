package com.avainfinity.android

import org.json.JSONObject

/**
 * Builds an isolated in-memory restore candidate and validates it without touching live stores.
 */
object AvaBackupStagingVerifier {
    fun verify(payload: JSONObject, validator: (JSONObject) -> Boolean): Boolean = try {
        val source = JSONObject(payload.toString())
        val staged = JSONObject(source.toString())
        validator(staged) && staged.toString() == source.toString()
    } catch (_: Exception) {
        false
    }
}
