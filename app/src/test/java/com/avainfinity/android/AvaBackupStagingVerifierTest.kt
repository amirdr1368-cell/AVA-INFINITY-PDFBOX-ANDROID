package com.avainfinity.android

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvaBackupStagingVerifierTest {
    @Test fun validPayloadSurvivesIsolatedStagingAndComparison() {
        val payload = JSONObject()
            .put("format", "AVA-INFINITY-MOBILE-BACKUP-1")
            .put("createdAt", 123L)
            .put("memories", org.json.JSONArray().put(JSONObject().put("text", "sample")))
        assertTrue(AvaBackupStagingVerifier.verify(payload) {
            it.optString("format") == "AVA-INFINITY-MOBILE-BACKUP-1" &&
                it.optJSONArray("memories")?.length() == 1
        })
    }

    @Test fun invalidStagedPayloadIsRejected() {
        val payload = JSONObject().put("format", "wrong")
        assertFalse(AvaBackupStagingVerifier.verify(payload) {
            it.optString("format") == "AVA-INFINITY-MOBILE-BACKUP-1"
        })
    }

    @Test fun validatorFailureDoesNotMutateOriginalPayload() {
        val payload = JSONObject().put("format", "AVA-INFINITY-MOBILE-BACKUP-1")
        val before = payload.toString()
        assertFalse(AvaBackupStagingVerifier.verify(payload) { false })
        assertTrue(payload.toString() == before)
    }
}
