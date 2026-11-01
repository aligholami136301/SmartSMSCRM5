package ir.smartsmscrm.v4.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import ir.smartsmscrm.v4.data.ImportResult
import ir.smartsmscrm.v4.data.Store

/**
 * Explicit one-shot import AFTER user grants READ_CONTACTS.
 * All supported Iranian mobile numbers are copied to one local group "مخاطبین گوشی".
 * Re-running is idempotent because recipient list/phone is unique.
 * No address book edits, background syncing, or external transfers.
 */
object PhoneContactsImporter {
    private const val LIST_NAME = "مخاطبین گوشی"

    fun importAll(context:Context,store:Store): ImportResult {
        check(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED) {
            "مجوز مخاطبین گوشی فعال نیست"
        }
        val resolver=context.contentResolver
        val addressById=HashMap<Long,String>()
        resolver.query(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS),null,null,null)?.use { c ->
            while(c.moveToNext()) {
                val id=c.getLong(0)
                val addr=c.getString(1).orEmpty().trim()
                if(addr.isNotBlank() && id !in addressById) addressById[id]=addr
            }
        }
        val companyById=HashMap<Long,String>()
        resolver.query(ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.Organization.COMPANY),
            ContactsContract.Data.MIMETYPE+"=?",
            arrayOf(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),null)?.use { c ->
            while(c.moveToNext()) {
                val id=c.getLong(0)
                val company=c.getString(1).orEmpty().trim()
                if(company.isNotBlank() && id !in companyById) companyById[id]=company
            }
        }
        val groupId=store.createList(LIST_NAME)
        var added=0;var duplicates=0;var invalid=0
        val buffer=ArrayList<ImportedPerson>(250)
        fun flush() {
            if(buffer.isNotEmpty()) {
                val r=store.importPeople(groupId,buffer)
                added+=r.added;duplicates+=r.duplicates;invalid+=r.invalid
                buffer.clear()
            }
        }
        resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY),null,null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY+" ASC")?.use { c ->
            while(c.moveToNext()) {
                val id=c.getLong(0)
                val phone=c.getString(1).orEmpty()
                val name=c.getString(2).orEmpty()
                buffer.add(ImportedPerson(name,"",phone,companyById[id].orEmpty(),addressById[id].orEmpty()))
                if(buffer.size>=250) flush()
            }
        } ?: error("دفترچه مخاطبین گوشی قابل خواندن نیست")
        flush()
        return ImportResult(added,duplicates,invalid)
    }
}
