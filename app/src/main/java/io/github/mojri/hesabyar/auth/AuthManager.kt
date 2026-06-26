package io.github.mojri.hesabyar.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor() {
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val lockHandler = Handler(Looper.getMainLooper())
    private var lockTimeoutMs = 30 * 60 * 1000L

    private val lockRunnable = Runnable { lock() }

    fun authenticateWithBiometric(activity: FragmentActivity, context: Context) {
        BiometricHelper.authenticate(
            activity = activity,
            onSuccess = { unlock() },
            onError = { },
            onFailed = { }
        )
    }

    fun authenticateWithPin(context: Context, pin: String): Boolean {
        if (PinStorage.verifyPin(context, pin)) {
            unlock()
            return true
        }
        return false
    }

    fun unlock() {
        _isLocked.value = false
        startLockTimer()
    }

    fun lock() {
        _isLocked.value = true
        cancelLockTimer()
    }

    fun onUserInteraction() {
        lockHandler.removeCallbacks(lockRunnable)
        if (!_isLocked.value) {
            startLockTimer()
        }
    }

    fun isAuthEnabled(context: Context): Boolean {
        return PinStorage.isPinSet(context) || BiometricHelper.isBiometricAvailable(context)
    }

    fun setLockTimeout(minutes: Int) {
        lockTimeoutMs = minutes * 60 * 1000L
    }

    private fun startLockTimer() {
        lockHandler.removeCallbacks(lockRunnable)
        lockHandler.postDelayed(lockRunnable, lockTimeoutMs)
    }

    private fun cancelLockTimer() {
        lockHandler.removeCallbacks(lockRunnable)
    }

    fun shouldShowAuth(context: Context): Boolean {
        return isAuthEnabled(context)
    }

    fun needsBiometricOrPin(context: Context): Boolean {
        return PinStorage.isPinSet(context)
    }

    fun hasBiometric(context: Context): Boolean {
        return BiometricHelper.isBiometricAvailable(context)
    }
}
