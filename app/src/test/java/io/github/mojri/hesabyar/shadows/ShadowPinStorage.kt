package io.github.mojri.hesabyar.shadows

import android.content.Context
import io.github.mojri.hesabyar.auth.PinStorage
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(PinStorage::class)
class ShadowPinStorage {
    companion object {
        private val store = mutableMapOf<String, String>()
    }

    @Implementation
    fun isPinSet(context: Context): Boolean =
        store.containsKey(key(context))

    @Implementation
    fun setPin(context: Context, pin: String) {
        store[key(context)] = pin
    }

    @Implementation
    fun verifyPin(context: Context, pin: String): Boolean =
        store[key(context)] == pin

    @Implementation
    fun clearPin(context: Context) {
        store.remove(key(context))
    }

    private fun key(context: Context) = System.identityHashCode(context).toString()

    fun reset() {
        store.clear()
    }
}
