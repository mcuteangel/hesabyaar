package io.github.mojri.hesabyar.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.mojri.hesabyar.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MarkPaidReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val installmentId = intent.getLongExtra(EXTRA_INSTALLMENT_ID, -1)
        if (installmentId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val installment = database.installmentDao().getAllInstallmentsSync()
                    .firstOrNull { it.id == installmentId }
                if (installment != null && !installment.isPaid) {
                    database.installmentDao().updateInstallment(
                        installment.copy(isPaid = true)
                    )
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(installmentId.toInt())
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_PAID = "io.github.mojri.hesabyar.ACTION_MARK_PAID"
        const val EXTRA_INSTALLMENT_ID = "installment_id"

        fun createIntent(context: Context, installmentId: Long): Intent {
            return Intent(context, MarkPaidReceiver::class.java).apply {
                action = ACTION_MARK_PAID
                putExtra(EXTRA_INSTALLMENT_ID, installmentId)
            }
        }
    }
}
