package org.jmouse.project.kitsu.tv.update

import org.jmouse.project.kitsu.tv.text.t
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * What the system installer says about an update this app asked for.
 *
 * ⚠️ PENDING_USER_ACTION is the ordinary case, not an error: an app that is not the device's owner
 * cannot install without the person confirming, so the installer hands back its own confirmation
 * screen and this opens it. Success closes this app — the new version is what opens next.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> AppUpdater.report(AppUpdater.State.UpToDate(null))
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                AppUpdater.report(
                    AppUpdater.State.Failed(
                        when (status) {
                            PackageInstaller.STATUS_FAILURE_ABORTED -> t("tv.update.cancelled", "The install was cancelled.")
                            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                                t("tv.update.signedDifferently", "The new build is signed differently from this one, so it cannot replace it.")
                            PackageInstaller.STATUS_FAILURE_STORAGE -> t("tv.update.noSpace", "There is not enough space on this television.")
                            else -> message ?: t("tv.update.failed", "The install failed.")
                        },
                    ),
                )
            }
        }
    }
}
