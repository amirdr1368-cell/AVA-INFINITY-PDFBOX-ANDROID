package com.avainfinity.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AvaBackupChainTest {
    private fun payload(note: String, tasks: Int = 1) = JSONObject()
        .put("format", "AVA-INFINITY-MOBILE-BACKUP-1")
        .put("createdAt", 100L)
        .put("memories", JSONArray().put(JSONObject().put("text", note)))
        .put("memories_amir", JSONArray())
        .put("vocabulary", JSONArray())
        .put("tasks", JSONArray().put(JSONObject().put("id", "task-1").put("value", tasks)))
        .put("profiles", JSONArray())
        .put("personMemories", JSONObject())
        .put("pendingRelationshipMemories", JSONArray())
        .put("settings", JSONObject().put("voice", JSONObject()).put("ui", JSONObject()))

    private val valid: (JSONObject) -> Boolean = {
        it.optString("format") == "AVA-INFINITY-MOBILE-BACKUP-1" &&
            it.optJSONArray("memories") != null && it.optJSONArray("tasks") != null
    }

    @Test fun dailyFullArchiveStartsAnIndependentChain() {
        val source = payload("daily")
        val full = AvaBackupChain.createFull(
            source, "daily", "11111111-1111-4111-8111-111111111111", 1000
        )
        assertEquals("full", full.optString("archiveType"))
        assertEquals(0, full.optInt("sequence", -1))
        assertTrue(full.isNull("parentArchiveId"))
        assertTrue(full.optString("chainHash").length == 64)
        assertEquals(AvaBackupChain.sha256(source),
            AvaBackupChain.sha256(AvaBackupChain.materializeFull(full, valid)))
    }

    @Test fun rejectsUnknownOrMismatchedFullCheckpointLabels() {
        val source = payload("labels")
        assertThrows(IllegalArgumentException::class.java) {
            AvaBackupChain.createFull(
                source, "daily", "11111111-1111-4111-8111-111111111111", 1000,
                listOf("daily", "unknown")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AvaBackupChain.createFull(
                source, "daily", "11111111-1111-4111-8111-111111111111", 1000,
                listOf("weekly")
            )
        }
    }

    @Test fun eventDeltasCarryContiguousSequenceAndParentChainHash() {
        val first = payload("one")
        val full = AvaBackupChain.createFull(
            first, "daily", "11111111-1111-4111-8111-111111111111", 1000
        )
        val second = payload("two")
        val delta1 = AvaBackupChain.createDelta(
            full, first, second, "event", "22222222-2222-4222-8222-222222222222", 2000
        )
        val third = payload("three")
        val delta2 = AvaBackupChain.createDelta(
            delta1, second, third, "event", "33333333-3333-4333-8333-333333333333", 3000
        )
        assertEquals(1, delta1.optInt("sequence"))
        assertEquals(2, delta2.optInt("sequence"))
        assertEquals(full.optString("chainHash"), delta1.optString("previousChainHash"))
        assertEquals(delta1.optString("chainHash"), delta2.optString("previousChainHash"))
    }

    @Test fun unchangedPayloadProducesEmptyDeltaThatReconstructsIdentically() {
        val source = payload("unchanged")
        val full = AvaBackupChain.createFull(
            source, "daily", "11111111-1111-4111-8111-111111111111", 1000
        )
        val delta = AvaBackupChain.createDelta(
            full, source, JSONObject(source.toString()), "event",
            "22222222-2222-4222-8222-222222222222", 2000
        )
        assertEquals(0, delta.optJSONObject("changes")?.length())
        assertEquals(0, delta.optJSONArray("removedKeys")?.length())
        val reconstructed = AvaBackupChain.applyDelta(full, source, delta, valid)
        assertEquals(AvaBackupChain.sha256(source), AvaBackupChain.sha256(reconstructed))
    }

    @Test fun refusesDeltaTimestampEarlierThanItsParent() {
        val first = payload("one")
        val full = AvaBackupChain.createFull(
            first, "daily", "11111111-1111-4111-8111-111111111111", 2000
        )
        assertThrows(IllegalArgumentException::class.java) {
            AvaBackupChain.createDelta(
                full, first, payload("two"), "event",
                "22222222-2222-4222-8222-222222222222", 1000
            )
        }
    }

    @Test fun refusesDeltaBuiltFromPayloadThatDoesNotMatchParentArchive() {
        val first = payload("one")
        val full = AvaBackupChain.createFull(
            first, "daily", "11111111-1111-4111-8111-111111111111", 1000
        )
        assertThrows(IllegalArgumentException::class.java) {
            AvaBackupChain.createDelta(full, payload("not-the-parent"), payload("two"))
        }
    }

    @Test fun rejectsTamperedChainHeaderDigest() {
        val full = AvaBackupChain.createFull(
            payload("one"), "daily", "11111111-1111-4111-8111-111111111111", 1000
        )
        full.put("sequence", 4)
        assertThrows(IllegalArgumentException::class.java) {
            AvaBackupChain.materializeFull(full, valid)
        }
    }

    @Test fun fullArchiveRoundTrips() {
        val source = payload("first")
        val full = AvaBackupChain.createFull(source, "weekly", "11111111-1111-4111-8111-111111111111", 1000)
        val restored = AvaBackupChain.materializeFull(full, valid)
        assertEquals(AvaBackupChain.sha256(source), AvaBackupChain.sha256(restored))
    }

    @Test fun deltaReconstructsChangedSections() {
        val first = payload("first")
        val full = AvaBackupChain.createFull(first, "weekly", "11111111-1111-4111-8111-111111111111", 1000)
        val second = payload("second", 2)
        val delta = AvaBackupChain.createDelta(full, first, second, "event",
            "22222222-2222-4222-8222-222222222222", 2000)
        val restored = AvaBackupChain.applyDelta(full, first, delta, valid)
        assertEquals(AvaBackupChain.sha256(second), AvaBackupChain.sha256(restored))
    }

    @Test fun fullAndMultipleDeltasReconstructTarget() {
        val first = payload("one")
        val full = AvaBackupChain.createFull(first, "weekly", "11111111-1111-4111-8111-111111111111", 1000)
        val second = payload("two", 2)
        val delta1 = AvaBackupChain.createDelta(full, first, second, "event",
            "22222222-2222-4222-8222-222222222222", 2000)
        val third = payload("three", 3)
        val delta2 = AvaBackupChain.createDelta(delta1, second, third, "event",
            "33333333-3333-4333-8333-333333333333", 3000)
        val restored = AvaBackupChain.reconstruct(listOf(full, delta1, delta2),
            delta2.getString("archiveId"), valid)
        assertEquals(AvaBackupChain.sha256(third), AvaBackupChain.sha256(restored))
    }

    @Test fun rejectsWrongParentDigest() {
        val first = payload("one")
        val full = AvaBackupChain.createFull(first, "weekly", "11111111-1111-4111-8111-111111111111", 1000)
        val delta = AvaBackupChain.createDelta(full, first, payload("two"), "event",
            "22222222-2222-4222-8222-222222222222", 2000)
        assertThrows(IllegalArgumentException::class.java) {
            AvaBackupChain.applyDelta(full, payload("tampered"), delta, valid)
        }
    }

    @Test fun rejectsTamperedResultDigest() {
        val first = payload("one")
        val full = AvaBackupChain.createFull(first, "weekly", "11111111-1111-4111-8111-111111111111", 1000)
        val delta = AvaBackupChain.createDelta(full, first, payload("two"), "event",
            "22222222-2222-4222-8222-222222222222", 2000)
        delta.put("resultPayloadSha256", "00")
        assertThrows(IllegalArgumentException::class.java) {
            AvaBackupChain.applyDelta(full, first, delta, valid)
        }
    }

    @Test fun rejectsForkedChain() {
        val first = payload("one")
        val full = AvaBackupChain.createFull(first, "weekly", "11111111-1111-4111-8111-111111111111", 1000)
        val one = AvaBackupChain.createDelta(full, first, payload("two"), "event",
            "22222222-2222-4222-8222-222222222222", 2000)
        val fork = AvaBackupChain.createDelta(full, first, payload("three"), "event",
            "33333333-3333-4333-8333-333333333333", 2100)
        assertThrows(IllegalArgumentException::class.java) {
            AvaBackupChain.reconstruct(listOf(full, one, fork), one.getString("archiveId"), valid)
        }
    }

    @Test fun rejectsMissingParent() {
        val first = payload("one")
        val full = AvaBackupChain.createFull(first, "weekly", "11111111-1111-4111-8111-111111111111", 1000)
        val delta = AvaBackupChain.createDelta(full, first, payload("two"), "event",
            "22222222-2222-4222-8222-222222222222", 2000)
        assertThrows(IllegalArgumentException::class.java) {
            AvaBackupChain.reconstruct(listOf(delta), delta.getString("archiveId"), valid)
        }
    }

    @Test fun deltaDoesNotMutateInputs() {
        val first = payload("one")
        val before = first.toString()
        val full = AvaBackupChain.createFull(first, "weekly", "11111111-1111-4111-8111-111111111111", 1000)
        AvaBackupChain.createDelta(full, first, payload("two"), "event",
            "22222222-2222-4222-8222-222222222222", 2000)
        assertEquals(before, first.toString())
        assertFalse(full.has("changes"))
    }
}
