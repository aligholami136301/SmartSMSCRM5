package ir.smartsmscrm.v4.data

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ir.smartsmscrm.v4.util.ImportedPerson
import ir.smartsmscrm.v4.util.PasteParser
import ir.smartsmscrm.v4.util.Phone
import java.time.LocalDate
import org.json.JSONObject
import org.json.JSONArray
import android.database.Cursor.FIELD_TYPE_INTEGER
import android.database.Cursor.FIELD_TYPE_FLOAT
import android.database.Cursor.FIELD_TYPE_NULL

const val WAITING = "WAITING"
const val SENDING = "SENDING"
const val SENT = "SENT"
const val FAILED = "FAILED"
const val UNKNOWN = "UNKNOWN"
const val DEFERRED = "DEFERRED"

private fun values(vararg pairs: Pair<String, Any?>) = ContentValues().apply {
    pairs.forEach { (k, v) -> when(v) {
        null -> putNull(k)
        is Int -> put(k, v)
        is Long -> put(k, v)
        is Boolean -> put(k, if(v) 1 else 0)
        else -> put(k, v.toString())
    } }
}
private fun Cursor.str(key: String): String = getString(getColumnIndexOrThrow(key)) ?: ""
private fun Cursor.num(key: String): Int = getInt(getColumnIndexOrThrow(key))
private fun Cursor.long(key: String): Long = getLong(getColumnIndexOrThrow(key))

data class NumberList(val id: Long, val name: String, val count: Int)
data class Recipient(val id: Long, val listId: Long, val phone: String, val name: String, val product: String, val notes: String, val company: String = "", val address: String = "")
data class MessageTemplate(val id: Long, val title: String, val body: String)
data class Campaign(val id: Long, val title: String, val message: String, val simId: Int, val dailyLimit: Int,
                    val spacing: Int, val state: String, val created: Long, val total: Int, val sent: Int, val failed: Int,
                    val waiting: Int, val unknown: Int, val deferred: Int, val archived: Boolean, val removed: Boolean, val lastReason: String, val delivered: Int)
data class QueueItem(val id: Long, val campaignId: Long, val phone: String, val name: String, val status: String,
                     val parts: Int, val sentAt: Long, val note: String = "", val delivered: Boolean = false)
data class ImportResult(val added: Int, val duplicates: Int, val invalid: Int)

class Store(context: Context) : SQLiteOpenHelper(context.applicationContext, "campaign_v4.sqlite", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE lists(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL, deleted INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE recipients(id INTEGER PRIMARY KEY AUTOINCREMENT, list_id INTEGER NOT NULL, phone TEXT NOT NULL, name TEXT NOT NULL DEFAULT '', company TEXT NOT NULL DEFAULT '', address TEXT NOT NULL DEFAULT '', product TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', deleted INTEGER NOT NULL DEFAULT 0, UNIQUE(list_id,phone))")
        db.execSQL("CREATE INDEX rec_list ON recipients(list_id,deleted)")
        db.execSQL("CREATE TABLE templates(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, body TEXT NOT NULL)")
        db.execSQL("CREATE TABLE blocked(phone TEXT PRIMARY KEY, note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE campaigns(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, body TEXT NOT NULL, sim_id INTEGER NOT NULL, daily_limit INTEGER NOT NULL, spacing INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'DRAFT', created INTEGER NOT NULL, archived INTEGER NOT NULL DEFAULT 0, removed INTEGER NOT NULL DEFAULT 0, last_reason TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE queue(id INTEGER PRIMARY KEY AUTOINCREMENT, campaign_id INTEGER NOT NULL, phone TEXT NOT NULL, name TEXT NOT NULL DEFAULT '', product TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'WAITING', parts INTEGER NOT NULL DEFAULT 0, attempt_id INTEGER NOT NULL DEFAULT 0, callback_count INTEGER NOT NULL DEFAULT 0, callback_failures INTEGER NOT NULL DEFAULT 0, delivery_count INTEGER NOT NULL DEFAULT 0, delivery_failures INTEGER NOT NULL DEFAULT 0, attempted_at INTEGER NOT NULL DEFAULT 0, sent_at INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '', UNIQUE(campaign_id,phone))")
        db.execSQL("CREATE INDEX queue_status ON queue(campaign_id,status,id)")
        db.execSQL("CREATE TABLE attempts(id INTEGER PRIMARY KEY AUTOINCREMENT, queue_id INTEGER NOT NULL, campaign_id INTEGER NOT NULL, sim_id INTEGER NOT NULL, day TEXT NOT NULL, segments INTEGER NOT NULL, at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX attempts_day ON attempts(day,sim_id)")
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("INSERT INTO settings(key,value) VALUES('allow_repeat_within_3_days','0')")
        db.execSQL("INSERT INTO lists(id,name) VALUES(1,'بدون گروه')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Non-destructive migrations; V4 v1 -> v3, and optional V4.1 v2 -> v3.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE campaigns ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE campaigns ADD COLUMN last_reason TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE campaigns ADD COLUMN removed INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 4) {
            db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO settings(key,value) VALUES('allow_repeat_within_3_days','0')")
        }
    }
    /** Global opt-in. Default OFF. It never overrides UNKNOWN, SENDING or opt-out. */
    @Synchronized fun allowResendWithinThreeDays(): Boolean =
        rows("SELECT value FROM settings WHERE key='allow_repeat_within_3_days'") { it.str("value") }.firstOrNull() == "1"

    @Synchronized fun setAllowResendWithinThreeDays(allow: Boolean) {
        writableDatabase.insertWithOnConflict("settings", null,
            values("key" to "allow_repeat_within_3_days", "value" to if (allow) "1" else "0"),
            SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun count(sql: String, vararg args: String) = DatabaseUtils.longForQuery(readableDatabase, sql, args).toInt()
    private fun <T> rows(sql: String, args: Array<String> = emptyArray(), map: (Cursor) -> T): List<T> {
        val results = mutableListOf<T>()
        readableDatabase.rawQuery(sql, args).use { while(it.moveToNext()) results.add(map(it)) }
        return results
    }
    @Synchronized fun lists(): List<NumberList> = rows("SELECT l.id,l.name,(SELECT COUNT(*) FROM recipients r WHERE r.list_id=l.id AND r.deleted=0) AS n FROM lists l WHERE l.deleted=0 ORDER BY l.id") {
        NumberList(it.long("id"), it.str("name"), it.num("n"))
    }
    @Synchronized fun createList(name: String): Long {
        require(name.trim().isNotEmpty())
        val db = writableDatabase
        val existing = rows("SELECT id FROM lists WHERE name=?", arrayOf(name.trim())) { it.long("id") }.firstOrNull()
        if(existing!=null) { db.update("lists", values("deleted" to 0), "id=?", arrayOf(existing.toString())); return existing }
        return db.insertOrThrow("lists", null, values("name" to name.trim()))
    }
    @Synchronized fun renameList(id: Long, name: String) {
        require(id!=1L && name.isNotBlank())
        writableDatabase.update("lists", values("name" to name.trim()), "id=?", arrayOf(id.toString()))
    }
    /** groupOnly transfers numbers to the default list; other mode soft-deletes members too. Existing campaign snapshots are untouched. */
    @Synchronized fun deleteList(id: Long, groupOnly: Boolean) {
        require(id!=1L)
        val db=writableDatabase; db.beginTransaction()
        try {
            if(groupOnly) {
                // Skip duplicates in default list, never destroy an existing default-list record.
                db.execSQL("INSERT OR IGNORE INTO recipients(list_id,phone,name,company,address,product,notes,deleted) SELECT 1,phone,name,company,address,product,notes,deleted FROM recipients WHERE list_id=? AND deleted=0",arrayOf(id))
            }
            if(groupOnly) db.delete("recipients","list_id=?",arrayOf(id.toString()))
            else db.update("recipients",values("deleted" to 1),"list_id=?",arrayOf(id.toString()))
            db.update("lists",values("deleted" to 1),"id=?",arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun recipients(id: Long, filter: String = ""): List<Recipient> {
        val query="%${filter.trim()}%"
        return rows("SELECT * FROM recipients WHERE list_id=? AND deleted=0 AND (phone LIKE ? OR name LIKE ? OR company LIKE ? OR product LIKE ? OR address LIKE ?) ORDER BY id DESC", arrayOf(id.toString(),query,query,query,query,query)) {
            Recipient(it.long("id"),it.long("list_id"),it.str("phone"),it.str("name"),it.str("product"),it.str("notes"),it.str("company"),it.str("address"))
        }
    }
    /** Paged contact views: stable ordering, no fixed 1000-row ceiling. */
    /** All contacts across all active lists. Search covers address and group name too. */
    private fun contactGlobalWhere(): String = """r.deleted=0 AND l.deleted=0 AND
        (r.phone LIKE ? OR r.name LIKE ? OR r.company LIKE ? OR
         r.address LIKE ? OR r.product LIKE ? OR l.name LIKE ?)""".trimIndent()

    @Synchronized fun globalRecipientCount(filter:String=""):Int {
        val q="%${filter.trim()}%"
        return count("SELECT COUNT(*) FROM recipients r JOIN lists l ON l.id=r.list_id WHERE ${contactGlobalWhere()}", q,q,q,q,q,q)
    }
    @Synchronized fun globalRecipientIds(filter:String=""):List<Long> {
        val q="%${filter.trim()}%"
        return rows("SELECT r.id FROM recipients r JOIN lists l ON l.id=r.list_id WHERE ${contactGlobalWhere()} ORDER BY r.id DESC",
            arrayOf(q,q,q,q,q,q)) { it.long("id") }
    }
    @Synchronized fun globalRecipientPage(filter:String="",limit:Int=100,offset:Int=0):List<Recipient> {
        require(limit in 1..200 && offset>=0)
        val q="%${filter.trim()}%"
        return rows("SELECT r.* FROM recipients r JOIN lists l ON l.id=r.list_id WHERE ${contactGlobalWhere()} ORDER BY r.id DESC LIMIT ? OFFSET ?",
            arrayOf(q,q,q,q,q,q,limit.toString(),offset.toString())) {
            Recipient(it.long("id"),it.long("list_id"),it.str("phone"),it.str("name"),it.str("product"),it.str("notes"),it.str("company"),it.str("address"))
        }
    }
    @Synchronized fun recipientCount(id:Long,filter:String=""):Int {
        val q="%${filter.trim()}%"
        return count("SELECT COUNT(*) FROM recipients WHERE list_id=? AND deleted=0 AND (phone LIKE ? OR name LIKE ? OR company LIKE ? OR product LIKE ? OR address LIKE ?)",id.toString(),q,q,q,q,q)
    }
    @Synchronized fun recipientIds(id:Long,filter:String=""):List<Long> {
        val q="%${filter.trim()}%"
        return rows("SELECT id FROM recipients WHERE list_id=? AND deleted=0 AND (phone LIKE ? OR name LIKE ? OR company LIKE ? OR product LIKE ? OR address LIKE ?) ORDER BY id DESC",arrayOf(id.toString(),q,q,q,q,q)){it.long("id")}
    }
    @Synchronized fun recipientPage(id:Long,filter:String="",limit:Int=100,offset:Int=0):List<Recipient> {
        require(limit in 1..200 && offset>=0)
        val q="%${filter.trim()}%"
        return rows("SELECT * FROM recipients WHERE list_id=? AND deleted=0 AND (phone LIKE ? OR name LIKE ? OR company LIKE ? OR product LIKE ? OR address LIKE ?) ORDER BY id DESC LIMIT ? OFFSET ?",
            arrayOf(id.toString(),q,q,q,q,q,limit.toString(),offset.toString())) {
            Recipient(it.long("id"),it.long("list_id"),it.str("phone"),it.str("name"),it.str("product"),it.str("notes"),it.str("company"),it.str("address"))
        }
    }
    @Synchronized fun trashPage(limit:Int=100,offset:Int=0):List<Recipient> {
        require(limit in 1..200 && offset>=0)
        return rows("SELECT * FROM recipients WHERE deleted=1 ORDER BY id DESC LIMIT ? OFFSET ?",arrayOf(limit.toString(),offset.toString())) {
            Recipient(it.long("id"),it.long("list_id"),it.str("phone"),it.str("name"),it.str("product"),it.str("notes"),it.str("company"),it.str("address"))
        }
    }
    @Synchronized fun importNumbers(id: Long, raw: String): ImportResult {
        val valid=PasteParser.parse(raw)
        val candidates=raw.lines().count { it.isNotBlank() }
        var added=0
        val db=writableDatabase; db.beginTransaction()
        try {
            valid.forEach { r ->
                val inserted=db.insertWithOnConflict("recipients",null,values("list_id" to id,"phone" to r.phone,"name" to r.name,"product" to r.product),SQLiteDatabase.CONFLICT_IGNORE)
                if(inserted>=0) added++

            }
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
        return ImportResult(added,valid.size-added,(candidates-valid.size).coerceAtLeast(0))
    }
    @Synchronized fun importPeople(id: Long, people: List<ImportedPerson>): ImportResult {
        var inserted=0; var duplicates=0; var invalid=0
        val db=writableDatabase; db.beginTransaction()
        try {
            people.forEach { p ->
                val phone=Phone.normalize(p.phone)
                if(phone==null) {invalid++;return@forEach}
                val n=db.insertWithOnConflict("recipients",null,values("list_id" to id,"phone" to phone,"name" to (p.first+" "+p.last).trim(),"company" to p.company,"address" to p.address,"notes" to p.notes),SQLiteDatabase.CONFLICT_IGNORE)
                if(n>=0) inserted++ else duplicates++
            }
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
        return ImportResult(inserted,duplicates,invalid)
    }
    @Synchronized fun addRecipient(listId:Long,phoneRaw:String,name:String,company:String,address:String,product:String,notes:String): Boolean {
        val phone=Phone.normalize(phoneRaw) ?: error("شماره موبایل ایرانی نامعتبر است")
        val id=writableDatabase.insertWithOnConflict("recipients",null,values("list_id" to listId,"phone" to phone,"name" to name.trim(),"company" to company.trim(),"address" to address.trim(),"product" to product.trim(),"notes" to notes.trim()),SQLiteDatabase.CONFLICT_IGNORE)
        return id>=0
    }
    /** Returns an actionable validation error rather than silently ignoring a duplicate mobile. */
    @Synchronized fun updateRecipient(id: Long, phoneRaw:String, name:String,product:String,notes:String,company:String="",address:String="") {
        val phone=Phone.normalize(phoneRaw) ?: error("شماره موبایل معتبر نیست")
        val original=rows("SELECT list_id FROM recipients WHERE id=? AND deleted=0",arrayOf(id.toString())){it.long("list_id")}.firstOrNull()
            ?: error("مخاطب پیدا نشد")
        if(count("SELECT COUNT(*) FROM recipients WHERE list_id=? AND phone=? AND id<>?",original.toString(),phone,id.toString())>0)
            error("این شماره در همین گروه وجود دارد؛ اطلاعات مخاطب قبلی تغییر نکرد")
        val changes=values("phone" to phone,"name" to name.trim(),"product" to product.trim(),"notes" to notes.trim(),"company" to company.trim(),"address" to address.trim())
        try {
            check(writableDatabase.update("recipients",changes,"id=? AND deleted=0",arrayOf(id.toString()))==1)
        } catch(e:android.database.sqlite.SQLiteConstraintException) { error("این شماره از قبل در گروه وجود دارد") }
        // Campaign recipients are immutable snapshots, intentionally unchanged.
    }
    /** Move only eligible active contacts; conflicting numbers remain in their original group.
     * No campaign snapshot or sending history is changed.
     */
    @Synchronized fun moveRecipients(ids: List<Long>, targetListId: Long): Pair<Int,Int> {
        require(ids.isNotEmpty() && ids.size <= 20000) { "تعداد مخاطبین نامعتبر است" }
        require(count("SELECT COUNT(*) FROM lists WHERE id=? AND deleted=0",targetListId.toString())==1) { "گروه مقصد وجود ندارد" }
        val db=writableDatabase
        var moved=0
        var skipped=0
        db.beginTransaction()
        try {
            ids.distinct().forEach { id ->
                val src=rows("SELECT list_id,phone FROM recipients WHERE id=? AND deleted=0",arrayOf(id.toString())){
                    it.long("list_id") to it.str("phone")
                }.firstOrNull()
                if(src==null || src.first==targetListId ||
                    count("SELECT COUNT(*) FROM recipients WHERE list_id=? AND phone=?",targetListId.toString(),src.second)>0) {
                    skipped++
                } else {
                    moved+=db.update("recipients",values("list_id" to targetListId),"id=? AND deleted=0",arrayOf(id.toString()))
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return moved to skipped
    }
    @Synchronized fun deleteRecipients(ids: List<Long>) {
        if(ids.isEmpty())return
        val db=writableDatabase; db.beginTransaction()
        try {ids.forEach {db.update("recipients",values("deleted" to 1),"id=?",arrayOf(it.toString()))};db.setTransactionSuccessful()}
        finally {db.endTransaction()}
    }
    @Synchronized fun trash():List<Recipient> = rows("SELECT * FROM recipients WHERE deleted=1 ORDER BY id DESC LIMIT 5000") {
        Recipient(it.long("id"),it.long("list_id"),it.str("phone"),it.str("name"),it.str("product"),it.str("notes"),it.str("company"),it.str("address"))
    }
    @Synchronized fun restoreRecipients(ids:List<Long>) { ids.forEach { id ->
        writableDatabase.update("recipients",values("deleted" to 0),"id=?",arrayOf(id.toString()))
        writableDatabase.execSQL("UPDATE lists SET deleted=0 WHERE id=(SELECT list_id FROM recipients WHERE id=?)",arrayOf(id))
    } }
    /** Permanently delete only records already in the trash; campaign snapshots remain independent. */
    @Synchronized fun permanentlyDeleteTrash(ids:List<Long>): Int {
        if(ids.isEmpty())return 0
        val db=writableDatabase; db.beginTransaction()
        try {
            var deleted=0
            ids.distinct().forEach { deleted+=db.delete("recipients","id=? AND deleted=1",arrayOf(it.toString())) }
            db.setTransactionSuccessful(); return deleted
        } finally {db.endTransaction()}
    }
    @Synchronized fun emptyTrash():Int=writableDatabase.delete("recipients","deleted=1",null)
    @Synchronized fun trashCount():Int=count("SELECT COUNT(*) FROM recipients WHERE deleted=1")
    @Synchronized fun blockedPhones():List<String> = rows("SELECT phone FROM blocked ORDER BY phone") { it.str("phone") }
    @Synchronized fun blockPhone(raw:String) {
        val phone=Phone.normalize(raw) ?: error("شماره نامعتبر است")
        writableDatabase.insertWithOnConflict("blocked",null,values("phone" to phone),SQLiteDatabase.CONFLICT_IGNORE)
    }
    @Synchronized fun unblockPhone(raw:String) { val phone=Phone.normalize(raw)?:return; writableDatabase.delete("blocked","phone=?",arrayOf(phone)) }
    @Synchronized fun isBlocked(phone:String):Boolean=count("SELECT COUNT(*) FROM blocked WHERE phone=?",phone)>0
    @Synchronized fun skipBlocked(id:Long) {writableDatabase.update("queue",values("status" to "SKIPPED","note" to "انصراف از دریافت پیامک"),"id=? AND status='WAITING'",arrayOf(id.toString()))}
    @Synchronized fun templates()=rows("SELECT * FROM templates ORDER BY id DESC") { MessageTemplate(it.long("id"),it.str("title"),it.str("body")) }
    @Synchronized fun saveTemplate(id:Long,title:String,body:String) {
        require(title.isNotBlank()&&body.isNotBlank())
        if(id==0L) writableDatabase.insertOrThrow("templates",null,values("title" to title,"body" to body))
        else writableDatabase.update("templates",values("title" to title,"body" to body),"id=?",arrayOf(id.toString()))
    }
    @Synchronized fun removeTemplate(id:Long) {writableDatabase.delete("templates","id=?",arrayOf(id.toString()))}
    @Synchronized fun createCampaign(title:String,body:String,groupIds:Set<Long>,simId:Int,dailyLimit:Int,spacing:Int): Pair<Long,Int> {
        require(title.isNotBlank() && body.isNotBlank() && groupIds.isNotEmpty() && simId>=0)
        require(dailyLimit in 1..2000 && spacing in 5..120)
        val db=writableDatabase; db.beginTransaction()
        try {
            val id=db.insertOrThrow("campaigns",null,values("title" to title,"body" to body,"sim_id" to simId,"daily_limit" to dailyLimit,"spacing" to spacing,"state" to "DRAFT","created" to System.currentTimeMillis()))
            groupIds.forEach { group ->
                db.execSQL("INSERT OR IGNORE INTO queue(campaign_id,phone,name,product) SELECT ?,phone,name,product FROM recipients WHERE list_id=? AND deleted=0",arrayOf(id,group))
            }
            val total=DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM queue WHERE campaign_id=?",arrayOf(id.toString())).toInt()
            require(total>0) {"فهرست انتخاب‌شده شماره‌ای ندارد"}
            db.setTransactionSuccessful()
            return id to total
        } finally {db.endTransaction()}
    }
    private val campaignProjection = """SELECT c.*,
        (SELECT COUNT(*) FROM queue q WHERE q.campaign_id=c.id) total,
        (SELECT COUNT(*) FROM queue q WHERE q.campaign_id=c.id AND q.status='SENT') sent,
        (SELECT COUNT(*) FROM queue q WHERE q.campaign_id=c.id AND q.status='SENT' AND q.parts>0 AND q.delivery_count>=q.parts AND q.delivery_failures=0) delivered,
        (SELECT COUNT(*) FROM queue q WHERE q.campaign_id=c.id AND q.status='FAILED') failed,
        (SELECT COUNT(*) FROM queue q WHERE q.campaign_id=c.id AND q.status='UNKNOWN') unknown,
        (SELECT COUNT(*) FROM queue q WHERE q.campaign_id=c.id AND q.status='DEFERRED') deferred,
        (SELECT COUNT(*) FROM queue q WHERE q.campaign_id=c.id AND q.status IN ('WAITING','SENDING','DEFERRED')) waiting
        FROM campaigns c"""
    private fun mapCampaign(it:Cursor):Campaign = Campaign(
        it.long("id"),it.str("title"),it.str("body"),it.num("sim_id"),it.num("daily_limit"),it.num("spacing"),
        it.str("state"),it.long("created"),it.num("total"),it.num("sent"),it.num("failed"),it.num("waiting"),
        it.num("unknown"),it.num("deferred"),it.num("archived")!=0,it.num("removed")!=0,
        it.str("last_reason"),it.num("delivered"))

    @Synchronized fun campaigns():List<Campaign> = rows("$campaignProjection ORDER BY c.created DESC") { mapCampaign(it) }
    // Efficient single-campaign query: called during live UI polling; do not scan all campaigns.
    @Synchronized fun campaign(id:Long):Campaign? = rows("$campaignProjection WHERE c.id=?",
        arrayOf(id.toString())) { mapCampaign(it) }.firstOrNull()
    /**
     * Counts every recipient in a campaign, optionally filtered by status.
     * This is NOT limited to the number of rows visible on screen.
     */
    @Synchronized fun queueCount(id: Long, filter: String = ""): Int =
        if (filter.isBlank()) {
            count("SELECT COUNT(*) FROM queue WHERE campaign_id=?", id.toString())
        } else {
            count("SELECT COUNT(*) FROM queue WHERE campaign_id=? AND status=?", id.toString(), filter)
        }

    /**
     * Fetch only one page for the campaign details screen. No fixed global 1000-row cap.
     * Page size is bounded so a large campaign cannot freeze the UI by loading every row.
     * Sorting by the unique id ensures deterministic page navigation.
     * IMPORTANT: The background SMS sender uses nextWaiting(), not this display query.
     */
    @Synchronized fun queuePage(id: Long, filter: String = "", limit: Int = 100, offset: Int = 0): List<QueueItem> {
        require(limit in 1..200) { "Page size must be between 1 and 200" }
        require(offset >= 0) { "Offset cannot be negative" }
        val columns = "id,campaign_id,phone,name,status,parts,sent_at,note,delivery_count,delivery_failures"
        val (sql, args) = if (filter.isBlank()) {
            "SELECT $columns FROM queue WHERE campaign_id=? ORDER BY id LIMIT ? OFFSET ?" to
                arrayOf(id.toString(), limit.toString(), offset.toString())
        } else {
            "SELECT $columns FROM queue WHERE campaign_id=? AND status=? ORDER BY id LIMIT ? OFFSET ?" to
                arrayOf(id.toString(), filter, limit.toString(), offset.toString())
        }
        return rows(sql, args) {
            QueueItem(it.long("id"),it.long("campaign_id"),it.str("phone"),it.str("name"),it.str("status"),it.num("parts"),it.long("sent_at"),it.str("note"),it.num("parts")>0 && it.num("delivery_count")>=it.num("parts") && it.num("delivery_failures")==0)
        }
    }
    /** Archiving and removing campaigns never erase SMS attempts or queue history. */
    @Synchronized fun setReason(id:Long,reason:String) {
        writableDatabase.update("campaigns",values("last_reason" to reason.take(500)),"id=?",arrayOf(id.toString()))
    }
    @Synchronized fun setArchived(id:Long, archived:Boolean) {
        ensureNotSending(id)
        writableDatabase.update("campaigns",values("archived" to if(archived)1 else 0),"id=?",arrayOf(id.toString()))
    }
    /** User-visible delete: hides the campaign while retaining send audit, cooldown and quota. */
    @Synchronized fun removeCampaign(id:Long) {
        ensureNotSending(id)
        writableDatabase.update("campaigns",values("removed" to 1,"archived" to 0),"id=?",arrayOf(id.toString()))
    }
    @Synchronized fun undoRemoveCampaign(id:Long) {
        ensureNotSending(id)
        writableDatabase.update("campaigns",values("removed" to 0),"id=?",arrayOf(id.toString()))
    }
    private fun ensureNotSending(id:Long) {
        val c=campaign(id) ?: error("کمپین پیدا نشد")
        require(c.state!="RUNNING" && count("SELECT COUNT(*) FROM queue WHERE campaign_id=? AND status='SENDING'",id.toString())==0) {
            "ابتدا ارسال کمپین را متوقف کنید و وضعیت شماره‌های در حال ارسال را بررسی کنید"
        }
    }
    @Synchronized fun setState(id:Long,state:String) {writableDatabase.update("campaigns",values("state" to state),"id=?",arrayOf(id.toString()))}
    @Synchronized fun activeCampaigns():Int = count("SELECT COUNT(*) FROM campaigns WHERE state='RUNNING'")
    @Synchronized fun clearAbandonedSending(id:Long) {
        writableDatabase.update("queue",values("status" to UNKNOWN,"note" to "ارسال پیشین نامشخص؛ تکرار خودکار ممنوع"),"campaign_id=? AND status='SENDING'",arrayOf(id.toString()))
    }
    @Synchronized fun nextWaiting(id:Long): QueueItem?=rows("SELECT * FROM queue WHERE campaign_id=? AND status='WAITING' ORDER BY id LIMIT 1",arrayOf(id.toString())) {
        QueueItem(it.long("id"),it.long("campaign_id"),it.str("phone"),it.str("name"),it.str("status"),it.num("parts"),it.long("sent_at"),it.str("note"),it.num("parts")>0 && it.num("delivery_count")>=it.num("parts") && it.num("delivery_failures")==0)
    }.firstOrNull()
    /** Revive deferred recipients according to the saved user setting.
     * UNKNOWN or SENDING in ANY campaign are always blocked, even when repeat sending is ON.
     * SENT in this SAME campaign is never reset, because only DEFERRED rows are updated.
     */
    @Synchronized fun reviveDeferred(id:Long):Int {
        val allowRepeat=allowResendWithinThreeDays()
        val db=writableDatabase
        db.execSQL("""UPDATE queue SET status='WAITING',note=''
            WHERE campaign_id=? AND status='DEFERRED' AND NOT EXISTS (
                SELECT 1 FROM queue prior WHERE prior.phone=queue.phone AND prior.id<>queue.id
                AND (prior.status IN ('UNKNOWN','SENDING') OR
                     (?=0 AND prior.status='SENT' AND prior.attempted_at>?))
            )""".trimIndent(),arrayOf(id,if(allowRepeat)1 else 0,System.currentTimeMillis()-3L*86_400_000L))
        return count("SELECT COUNT(*) FROM queue WHERE campaign_id=? AND status='WAITING'",id.toString())
    }
    /** Turning ON repeat sending never permits automatic retry of an uncertain attempt. */
    @Synchronized fun recentlyAttempted(phone:String,days:Int=3):Boolean = count(
        """SELECT COUNT(*) FROM queue WHERE phone=? AND
           (status IN ('UNKNOWN','SENDING') OR
            (?=0 AND status='SENT' AND attempted_at>?))""".trimIndent(),
        phone,if(allowResendWithinThreeDays())"1" else "0",
        (System.currentTimeMillis()-days*86_400_000L).toString())>0
    @Synchronized fun defer(id:Long) {writableDatabase.update("queue",values("status" to DEFERRED,"note" to "شماره مشمول محدودیت ارسال مجدد است یا نتیجه ارسال قبلی نامشخص است"),"id=? AND status='WAITING'",arrayOf(id.toString()))}
    @Synchronized fun deferExplanation(id:Long):String {
        val n=count("SELECT COUNT(*) FROM queue WHERE campaign_id=? AND status='DEFERRED'",id.toString())
        if(n==0)return ""
        val uncertain=count("""SELECT COUNT(*) FROM queue d WHERE d.campaign_id=? AND d.status='DEFERRED'
            AND EXISTS (SELECT 1 FROM queue prior WHERE prior.phone=d.phone AND prior.id<>d.id AND prior.status IN ('UNKNOWN','SENDING'))""".trimIndent(),id.toString())
        return if(allowResendWithinThreeDays())
            "$n شماره در تعویق است؛ $uncertain شماره به علت ارسال نامشخص یا در حال پردازش مسدود است. شماره‌های قابل ارسال با شروع دستی بعدی آزاد می‌شوند."
        else "$n شماره در تعویق است؛ محدودیت سه‌روزه فعال است و $uncertain شماره هم به علت ارسال نامشخص/در حال پردازش مسدود است. فیلتر «تعویق» را بررسی کنید."
    }
    @Synchronized fun usedSegmentsToday(simId:Int):Int=count("SELECT COALESCE(SUM(segments),0) FROM attempts WHERE day=? AND sim_id=?",LocalDate.now().toString(),simId.toString())
    @Synchronized fun attemptedToday(id:Long):Int=count("SELECT COUNT(*) FROM attempts WHERE campaign_id=? AND day=?",id.toString(),LocalDate.now().toString())
    @Synchronized fun checkpointSend(item:QueueItem,simId:Int,parts:Int):Long {
        val db=writableDatabase;db.beginTransaction()
        try {
            val attempt=System.currentTimeMillis()
            val updated=db.update("queue",values("status" to SENDING,"parts" to parts,"attempt_id" to attempt,"callback_count" to 0,"callback_failures" to 0,"delivery_count" to 0,"delivery_failures" to 0,"attempted_at" to attempt,"note" to ""),"id=? AND status='WAITING'",arrayOf(item.id.toString()))
            check(updated==1)
            db.insertOrThrow("attempts",null,values("queue_id" to item.id,"campaign_id" to item.campaignId,"sim_id" to simId,"day" to LocalDate.now().toString(),"segments" to parts,"at" to attempt))
            db.setTransactionSuccessful();return attempt
        } finally {db.endTransaction()}
    }
    /** Late callback for old attempt is ignored; never turn failed / unknown into sent by mistake. */
    @Synchronized fun recordSentPart(id:Long,attempt:Long,success:Boolean,error:String) {
        val db=writableDatabase;db.beginTransaction()
        try {
            db.rawQuery("SELECT parts,callback_count,callback_failures,status FROM queue WHERE id=? AND attempt_id=?",arrayOf(id.toString(),attempt.toString())).use { c ->
                if(c.moveToFirst()&&c.str("status")==SENDING) {
                    val parts=c.num("parts");val n=c.num("callback_count")+1;val fails=c.num("callback_failures") + if(success)0 else 1
                    val status=when { fails>0 -> FAILED; n>=parts -> SENT; else -> SENDING }
                    db.update("queue",values("callback_count" to n,"callback_failures" to fails,"status" to status,"sent_at" to if(status==SENT)System.currentTimeMillis() else 0L,"note" to if(success)"" else error),"id=?",arrayOf(id.toString()))
                }
            }
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
    }
    @Synchronized fun recordDeliveryPart(id:Long,attempt:Long,success:Boolean) {
        writableDatabase.execSQL("UPDATE queue SET delivery_count=delivery_count+1,delivery_failures=delivery_failures+? WHERE id=? AND attempt_id=?",arrayOf(if(success)0 else 1,id,attempt))
    }
    @Synchronized fun statusOf(id:Long)=rows("SELECT status FROM queue WHERE id=?",arrayOf(id.toString())){it.str("status")}.firstOrNull()
    @Synchronized fun markUnknown(id:Long) {writableDatabase.update("queue",values("status" to UNKNOWN,"note" to "نتیجه ارسال دریافت نشد؛ بررسی دستی لازم است"),"id=? AND status='SENDING'",arrayOf(id.toString()))}
    @Synchronized fun markFailed(id:Long,why:String) {writableDatabase.update("queue",values("status" to FAILED,"note" to why),"id=? AND status='SENDING'",arrayOf(id.toString()))}
    @Synchronized fun retryFailures(campaignId:Long):Int = writableDatabase.update("queue",values("status" to WAITING,"note" to ""),"campaign_id=? AND status='FAILED'",arrayOf(campaignId.toString()))
    @Synchronized fun allCounts()=mapOf("lists" to count("SELECT COUNT(*) FROM lists WHERE deleted=0"),"numbers" to count("SELECT COUNT(*) FROM recipients WHERE deleted=0"),"campaigns" to count("SELECT COUNT(*) FROM campaigns WHERE removed=0"),"sent" to count("SELECT COUNT(*) FROM queue WHERE status='SENT'"),"unknown" to count("SELECT COUNT(*) FROM queue WHERE status='UNKNOWN'"),"blocked" to count("SELECT COUNT(*) FROM blocked"))
    @Synchronized fun recoverIdleAfterRestart() {
        writableDatabase.update("campaigns",values("state" to "PAUSED"),"state='RUNNING'",null)
        writableDatabase.update("queue",values("status" to UNKNOWN,"note" to "برنامه بسته شده؛ وضعیت ارسال نامشخص"),"status='SENDING'",null)
    }
    private val tableNames=listOf("lists","recipients","templates","campaigns","queue","attempts","blocked","settings")
    @Synchronized fun exportSnapshot():String {
        val db=readableDatabase
        val json=JSONObject().put("schema",1).put("app","SmartSMSCRM-V4")
        for(table in tableNames) {
            val array=JSONArray()
            db.rawQuery("SELECT * FROM $table",null).use { c ->
                val cols=c.columnNames
                while(c.moveToNext()) {
                    val row=JSONObject()
                    cols.indices.forEach { i -> when(c.getType(i)) {
                        FIELD_TYPE_NULL -> row.put(cols[i],JSONObject.NULL)
                        FIELD_TYPE_INTEGER -> row.put(cols[i],c.getLong(i))
                        FIELD_TYPE_FLOAT -> row.put(cols[i],c.getDouble(i))
                        else -> row.put(cols[i],c.getString(i))
                    } }
                    array.put(row)
                }
            }
            json.put(table,array)
        }
        return json.toString()
    }
    /** Atomic restore. Call only with user confirmation and while no sending service is active. */
    @Synchronized fun restoreSnapshot(raw:String) {
        val json=JSONObject(raw)
        require(json.optInt("schema",-1)==1 && json.optString("app")=="SmartSMSCRM-V4") {"این فایل متعلق به V4 نیست"}
        // V4/V4.2 backups predate the settings table: missing settings means safe default OFF.
        tableNames.forEach { require(it=="settings" || json.optJSONArray(it)!=null) {"اطلاعات پشتیبان ناقص است: $it"} }
        val db=writableDatabase
        db.beginTransaction()
        try {
            tableNames.asReversed().forEach {db.delete(it,null,null)}
            tableNames.forEach { table ->
                val arr=json.optJSONArray(table) ?: JSONArray()
                val allowed=mutableSetOf<String>()
                db.rawQuery("PRAGMA table_info($table)",null).use {c-> while(c.moveToNext())allowed.add(c.getString(1))}
                for(i in 0 until arr.length()) {
                    val obj=arr.getJSONObject(i)
                    val cv=ContentValues()
                    val names=obj.keys()
                    while(names.hasNext()) {
                        val key=names.next()
                        require(key in allowed) {"ستون نامعتبر در فایل پشتیبان"}
                        if(obj.isNull(key)) cv.putNull(key)
                        else when(val v=obj.get(key)) {
                            is Int -> cv.put(key,v)
                            is Long -> cv.put(key,v)
                            is Number -> cv.put(key,v.toDouble())
                            else -> cv.put(key,v.toString())
                        }
                    }
                    db.insertOrThrow(table,null,cv)
                }
            }
            // Old backups contain no settings. Default back to the safer policy.
            db.insertWithOnConflict("settings",null,
                values("key" to "allow_repeat_within_3_days", "value" to "0"),
                SQLiteDatabase.CONFLICT_IGNORE)
            db.update("campaigns",values("state" to "PAUSED"),"state='RUNNING'",null)
            db.update("queue",values("status" to UNKNOWN,"note" to "بازیابی از پشتیبان؛ نتیجه نامشخص"),"status='SENDING'",null)
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
    }
    fun backupTables()=tableNames
}
