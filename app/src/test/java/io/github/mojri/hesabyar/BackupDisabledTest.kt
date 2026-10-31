package io.github.mojri.hesabyar

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for issue #624: Auto Backup must stay disabled, so the
 * financial Room database never becomes eligible for cloud backup again.
 * Robolectric builds ApplicationInfo from the manifest, so flipping
 * android:allowBackup back to true would set FLAG_ALLOW_BACKUP and fail
 * this test (sourcery thread 4433478827).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupDisabledTest {

  @Test
  fun applicationInfoHasBackupFlagClear() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val flags = context.packageManager.getApplicationInfo(context.packageName, 0).flags
    assertTrue(
      "allowBackup must stay false (issue #624)",
      flags and ApplicationInfo.FLAG_ALLOW_BACKUP == 0
    )
  }
}
