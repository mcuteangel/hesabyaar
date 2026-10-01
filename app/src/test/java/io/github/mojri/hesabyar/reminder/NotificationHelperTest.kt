package io.github.mojri.hesabyar.reminder

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.DEEP_LINK_PERSONS
import io.github.mojri.hesabyar.OPEN_TAB_EXTRA
import io.github.mojri.hesabyar.TAB_DEBTS
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.resolveInitialNavigation
import io.github.mojri.hesabyar.ui.screens.DebtSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Unit tests for [NotificationHelper] deep link intents.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NotificationHelperTest {
  @Test
  fun showLoanReminderIntentCarriesPersonsDeepLinkAndResolvesToPersonsLedger() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val notificationManager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val shadowNotificationManager = Shadows.shadowOf(notificationManager)
    NotificationHelper.createNotificationChannels(context)

    val loanId = 42L
    NotificationHelper.showLoanReminder(
      context = context,
      loanId = loanId,
      personName = "Ali",
      remainingAmount = 250_000L,
      loanType = LoanType.DEBTOR
    )

    val notification = shadowNotificationManager.allNotifications.firstOrNull()
    assertNotNull("Loan reminder notification must be posted", notification)

    val pendingIntent = notification?.contentIntent
    assertNotNull("Content intent must be set", pendingIntent)

    val shadowPendingIntent = Shadows.shadowOf(pendingIntent)
    val savedIntent = shadowPendingIntent.savedIntent
    assertNotNull("Saved intent must be extractable from PendingIntent", savedIntent)

    val openTabExtra = savedIntent.getStringExtra(OPEN_TAB_EXTRA)
    assertEquals("Intent must carry DEEP_LINK_PERSONS", DEEP_LINK_PERSONS, openTabExtra)

    // Verify end-to-end mapping through resolveInitialNavigation
    val (tab, section) = resolveInitialNavigation(openTabExtra)
    assertEquals("Deep link routes to DEBTS tab", TAB_DEBTS, tab)
    assertEquals("Deep link routes to PERSONS section", DebtSection.PERSONS, section)
  }
}
