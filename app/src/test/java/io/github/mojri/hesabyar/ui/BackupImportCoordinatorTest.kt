package io.github.mojri.hesabyar.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.RestoreMode
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Restore-execution cancellation tests for [BackupImportCoordinator]. Verifies
 * that a coroutine cancellation does not leave [operationState] stuck in
 * [BackupOperationState.Importing] (the in-flight guard would otherwise block retry).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class BackupImportCoordinatorTest {
  private val testDispatcher = StandardTestDispatcher()

  private lateinit var context: Context
  private lateinit var operationState: androidx.compose.runtime.MutableState<BackupOperationState>
  private lateinit var passphraseDialogState: androidx.compose.runtime.MutableState<PassphraseDialogState>
  private lateinit var isCryptoInProgress: androidx.compose.runtime.MutableState<Boolean>

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    context = RuntimeEnvironment.getApplication()
    operationState = mutableStateOf(BackupOperationState.Idle)
    passphraseDialogState = mutableStateOf(PassphraseDialogState.Hidden)
    isCryptoInProgress = mutableStateOf(false)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun executeRestoreOnCancellationResetsOperationStateToIdle() =
    runTest {
      val fakeRepo = FakeRepository()
      val scope = CoroutineScope(testDispatcher)
      val useCase = ManageBackupUseCase(fakeRepo, testDispatcher)
      val coordinator =
        BackupImportCoordinator(
          application = context,
          manageBackupUseCase = useCase,
          scope = scope,
          operationState = operationState,
          passphraseDialogState = passphraseDialogState,
          isCryptoInProgress = isCryptoInProgress
        )

      coordinator.pendingRestoreBackup.value = BackupPayload(accounts = emptyList())
      coordinator.selectedRestoreMode.value = RestoreMode.REPLACE

      // Simulate the restore coroutine being cancelled mid-flight (e.g. ViewModel
      // cleared during a long-running restore). CancellationException is what
      // Kotlin coroutines throw at a cancellation point.
      fakeRepo.importShouldThrow = CancellationException("simulated scope cancellation")
      coordinator.executeRestore()
      advanceUntilIdle()

      assertTrue(
        "After cancellation, operationState must be Idle, got ${operationState.value}",
        operationState.value is BackupOperationState.Idle
      )
    }
}
