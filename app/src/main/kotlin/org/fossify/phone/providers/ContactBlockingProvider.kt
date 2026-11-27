package org.fossify.phone.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers

class ContactBlockingProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val app = requireNotNull(context)
        val caller = callingPackage
        val trustedContacts = caller == "org.fossify.contacts" || caller == "org.fossify.contacts.dev"
        if (!trustedContacts || app.packageManager.checkSignatures(app.applicationInfo.uid, Binder.getCallingUid()) != PackageManager.SIGNATURE_MATCH) {
            throw SecurityException("Only Fossify Contacts signed with the same certificate can manage blocked numbers")
        }

        require(method in setOf("status", "block", "unblock"))
        val numbers = extras?.getStringArrayList("numbers").orEmpty().distinct()
        require(numbers.size <= 1000 && numbers.all { it.isNotBlank() && it.length <= 256 })

        val identity = Binder.clearCallingIdentity()
        try {
            numbers.forEach { number ->
                when {
                    method == "block" && !BlockedNumberContract.isBlocked(app, number) -> {
                        app.contentResolver.insert(
                            BlockedNumbers.CONTENT_URI,
                            ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
                        )
                    }
                    method == "unblock" -> BlockedNumberContract.unblock(app, number)
                }
            }
            return Bundle().apply {
                putInt("blocked", numbers.count { BlockedNumberContract.isBlocked(app, it) })
            }
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
        throw UnsupportedOperationException()

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}
