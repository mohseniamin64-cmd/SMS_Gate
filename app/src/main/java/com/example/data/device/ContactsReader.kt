package com.example.data.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactInfo(
    val contactId: Long,
    val displayName: String,
    val phoneNumber: String
) {
    val normalizedPhone: String
        get() = PhoneNumberNormalizer.normalize(phoneNumber)
}

/** Reads contacts only from the device. No contact data is sent to the API. */
class ContactsReader(private val context: Context) {
    fun read(): List<ContactInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("READ_CONTACTS permission is not granted")
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val contacts = mutableListOf<ContactInfo>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex)?.trim().orEmpty()
                if (number.isBlank()) continue
                val name = cursor.getString(nameIndex)?.trim().orEmpty()
                contacts += ContactInfo(
                    contactId = cursor.getLong(idIndex),
                    displayName = name,
                    phoneNumber = number
                )
            }
        }

        return contacts
            .distinctBy { "${it.contactId}:${it.normalizedPhone}" }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName.ifBlank { it.phoneNumber } })
    }
}
