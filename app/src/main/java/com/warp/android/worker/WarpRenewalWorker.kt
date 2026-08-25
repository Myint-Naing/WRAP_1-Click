package com.warp.android.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.warp.android.data.WarpApi
import com.warp.android.vpn.VpnManager
import java.util.concurrent.TimeUnit

class WarpRenewalWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting automated silent WARP key renewal...")
        val warpApi = WarpApi(appContext)
        val vpnManager = VpnManager(appContext)

        return try {
            val keyPair = vpnManager.generateKeyPair()
            val newPrivateKey = keyPair.privateKey.toBase64()
            val newPublicKey = keyPair.publicKey.toBase64()

            val updatedCredentials = warpApi.registerOrRenewKey(newPrivateKey, newPublicKey)
            Log.d(TAG, "Successfully renewed WARP registration. New exp: ${updatedCredentials.expirationTimestampEpochMs}")

            vpnManager.updateTunnelConfig(updatedCredentials)

            scheduleNextRenewal(appContext, updatedCredentials.expirationTimestampEpochMs)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to renew WARP key silently", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "WarpRenewalWorker"
        const val WORK_NAME = "warp_key_renewal_work"

        fun scheduleNextRenewal(context: Context, expirationTimestampEpochMs: Long) {
            val now = System.currentTimeMillis()
            // Schedule 48 hours (2 days) prior to key expiration date
            val targetTime = expirationTimestampEpochMs - TimeUnit.DAYS.toMillis(2)
            var initialDelay = targetTime - now

            if (initialDelay < 0) {
                // If expiration is sooner than 48h, trigger renewal as soon as possible (1 minute)
                initialDelay = TimeUnit.MINUTES.toMillis(1)
            }

            Log.d(TAG, "Scheduling next WARP key renewal in $initialDelay ms (${TimeUnit.MILLISECONDS.toHours(initialDelay)} hours)")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val renewalRequest = OneTimeWorkRequestBuilder<WarpRenewalWorker>()
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                renewalRequest
            )
        }
    }
}
