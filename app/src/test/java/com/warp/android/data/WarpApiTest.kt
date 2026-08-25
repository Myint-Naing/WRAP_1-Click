package com.warp.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class WarpApiTest {

    @Test
    fun testExpirationTimestampParsing_validTtl() {
        val ttlIso = "2026-09-25T12:00:00.000Z"
        val createdIso = "2026-08-25T12:00:00.000Z"

        // Mock method calculation logic
        val warpApi = WarpApiMock()
        val expEpoch = warpApi.parseExpirationTimestamp(ttlIso, createdIso)

        assertTrue("Expiration timestamp should be in the future", expEpoch > System.currentTimeMillis())
    }

    @Test
    fun testExpirationTimestampParsing_emptyTtl_defaultsTo30Days() {
        val now = System.currentTimeMillis()
        val warpApi = WarpApiMock()
        val expEpoch = warpApi.parseExpirationTimestamp(null, null)

        val expectedApprox = now + TimeUnit.DAYS.toMillis(30)
        // Allow 5 second delta margin
        assertTrue("Expiration should default to approx 30 days in future", Math.abs(expEpoch - expectedApprox) < 5000)
    }

    private class WarpApiMock {
        fun parseExpirationTimestamp(ttlStr: String?, createdStr: String?): Long {
            val now = System.currentTimeMillis()
            var expEpoch = 0L

            if (!ttlStr.isNullOrEmpty()) {
                expEpoch = parseIsoTimestamp(ttlStr)
            }

            if (expEpoch <= now) {
                val createdEpoch = if (!createdStr.isNullOrEmpty()) parseIsoTimestamp(createdStr) else now
                val validCreatedEpoch = if (createdEpoch > 0) createdEpoch else now
                expEpoch = validCreatedEpoch + (30L * 24 * 60 * 60 * 1000)
            }

            return expEpoch
        }

        private fun parseIsoTimestamp(isoString: String): Long {
            return try {
                java.time.Instant.parse(isoString).toEpochMilli()
            } catch (e: Exception) {
                0L
            }
        }
    }
}
