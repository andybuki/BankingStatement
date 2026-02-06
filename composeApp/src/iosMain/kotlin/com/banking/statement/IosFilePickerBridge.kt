@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.banking.statement

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * Singleton bridge for file picker callbacks between Swift and Kotlin.
 * This object is accessed from Swift to pass file data back to Kotlin.
 */
object FilePickerCallbackHolder {
    private var callback: ((ByteArray?, String?) -> Unit)? = null

    fun setCallback(cb: (ByteArray?, String?) -> Unit) {
        callback = cb
    }

    fun onFileSelected(data: NSData?, fileName: String?) {
        val byteArray = data?.toByteArraySafe()
        callback?.invoke(byteArray, fileName)
        callback = null
    }

    fun onFileCancelled() {
        callback?.invoke(null, null)
        callback = null
    }

    private fun NSData.toByteArraySafe(): ByteArray {
        val length = this.length.toInt()
        if (length == 0) return ByteArray(0)

        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
        return bytes
    }
}

/**
 * Function to trigger file picker - called from Kotlin Compose code
 */
fun triggerFilePicker(callback: (ByteArray?, String?) -> Unit) {
    FilePickerCallbackHolder.setCallback(callback)
    // Post notification to Swift to show file picker
    platform.Foundation.NSNotificationCenter.defaultCenter.postNotificationName(
        "ShowFilePicker",
        `object` = null
    )
}

/**
 * Functions exposed to Swift for callbacks
 */
fun onFilePickerResult(data: NSData?, fileName: String?) {
    FilePickerCallbackHolder.onFileSelected(data, fileName)
}

fun onFilePickerCancelled() {
    FilePickerCallbackHolder.onFileCancelled()
}
