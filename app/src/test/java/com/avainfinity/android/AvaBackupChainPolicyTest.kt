package com.avainfinity.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvaBackupChainPolicyTest {
    @Test fun forcesFullWhenBaselineIsMissingOrTwentyFourHoursOld() {
        assertTrue(AvaBackupChainPolicy.shouldCreateFull(null, 0, 0, 10_000))
        assertTrue(AvaBackupChainPolicy.shouldCreateFull(
            AvaBackupChainPolicy.MAX_BASE_AGE_MILLIS, 0, 0, 10_000
        ))
    }

    @Test fun forcesFullWhenDeviceClockMovesBehindTheBaseTimestamp() {
        assertTrue(AvaBackupChainPolicy.shouldCreateFull(-1L, 0, 0, 10_000))
    }

    @Test fun forcesFullAtOneHundredEventDeltas() {
        assertTrue(AvaBackupChainPolicy.shouldCreateFull(1_000, 100, 1, 10_000))
        assertFalse(AvaBackupChainPolicy.shouldCreateFull(1_000, 99, 1, 10_000))
    }

    @Test fun forcesFullWhenEventDeltasReachQuarterOfBaselineSize() {
        assertTrue(AvaBackupChainPolicy.shouldCreateFull(1_000, 2, 2_500, 10_000))
        assertFalse(AvaBackupChainPolicy.shouldCreateFull(1_000, 2, 2_499, 10_000))
    }

    @Test fun smallBaselineThresholdRoundsUpToTheFirstByteAtOrAboveQuarter() {
        assertFalse(AvaBackupChainPolicy.shouldCreateFull(1_000, 2, 2, 10))
        assertTrue(AvaBackupChainPolicy.shouldCreateFull(1_000, 2, 3, 10))
    }

    @Test fun emptyDeltaIsDetectedWithoutTimestampNoise() {
        assertTrue(AvaBackupChainPolicy.isEmptyDelta(0, 0))
        assertFalse(AvaBackupChainPolicy.isEmptyDelta(1, 0))
        assertFalse(AvaBackupChainPolicy.isEmptyDelta(0, 1))
    }
}
