package org.fossify.phone.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import android.provider.CallLog.Calls
import android.telephony.PhoneNumberUtils
import org.fossify.phone.extensions.getAvailableSIMCardLabels

class ContactBlockingProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val app = requireNotNull(context)
        val caller = callingPackage
        val trustedContacts = caller == "org.fossify.contacts" || caller == "org.fossify.contacts.dev"
        if (!trustedContacts || app.packageManager.checkSignatures(app.applicationInfo.uid, Binder.getCallingUid()) != PackageManager.SIGNATURE_MATCH) {
            throw SecurityException("Only Fossify Contacts signed with the same certificate can manage blocked numbers")
        }

        require(method in setOf("status", "block", "unblock", "call_history"))
        val numbers = extras?.getStringArrayList("numbers").orEmpty().distinct()
        require(numbers.size <= 1000 && numbers.all { it.isNotBlank() && it.length <= 256 })

        val identity = Binder.clearCallingIdentity()
        try {
            if (method == "call_history") {
                return readCallHistory(app, numbers, extras?.getInt("offset") ?: 0)
            }
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

    @Suppress("DEPRECATION")
    private fun readCallHistory(app: Context, numbers: List<String>, offset: Int): Bundle {
        require(offset >= 0)
        val dates = mutableListOf<Long>()
        val durations = mutableListOf<Long>()
        val types = mutableListOf<Int>()
        val simNames = arrayListOf<String>()
        val simColors = mutableListOf<Int>()
        val accounts = app.getAvailableSIMCardLabels().associateBy { it.handle.id }
        var total = 0
        val columns = arrayOf(Calls.NUMBER, Calls.DATE, Calls.DURATION, Calls.TYPE, Calls.PHONE_ACCOUNT_ID)
        app.contentResolver.query(
            Calls.CONTENT_URI, columns, "${Calls.NUMBER_PRESENTATION} = ?",
            arrayOf(Calls.PRESENTATION_ALLOWED.toString()), "${Calls.DATE} DESC, ${Calls._ID} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(0) ?: continue
                if (numbers.none { it == number || PhoneNumberUtils.compare(app, it, number) }) continue
                if (total >= offset && dates.size < 5) {
                    dates.add(cursor.getLong(1))
                    durations.add(cursor.getLong(2))
                    types.add(cursor.getInt(3))
                    val account = accounts[cursor.getString(4)]
                    simNames.add(account?.displayName.orEmpty())
                    simColors.add(account?.color ?: 0)
                }
                total++
            }
        }
        return Bundle().apply {
            putInt("total", total)
            putLongArray("dates", dates.toLongArray())
            putLongArray("durations", durations.toLongArray())
            putIntArray("types", types.toIntArray())
            putStringArrayList("sim_names", simNames)
            putIntArray("sim_colors", simColors.toIntArray())
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
