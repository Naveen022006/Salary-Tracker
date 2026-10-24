package com.local.salarytracker

import com.local.salarytracker.location.PlaceRecorder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceRecorderTest {
    private val now = 1_800_000_000_000L

    @Test fun recordsOnlyWhenOnAndTheEntryIsFromNow() {
        assertTrue(PlaceRecorder.shouldRecord(true, now - 60_000, now))
        assertTrue(PlaceRecorder.shouldRecord(true, now + 60_000, now)) // phone clock a little behind the SMS time
        assertFalse(PlaceRecorder.shouldRecord(false, now, now))
        // Imported old SMS or an entry dated yesterday must never get today's place.
        assertFalse(PlaceRecorder.shouldRecord(true, now - PlaceRecorder.MAX_AGE_MS - 1, now))
        assertFalse(PlaceRecorder.shouldRecord(true, now - 86_400_000L, now))
    }
}
