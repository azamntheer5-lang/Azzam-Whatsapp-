package com.example.receiptscanner.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Database
import com.example.receiptscanner.model.Transfer
import com.example.receiptscanner.storage.SecureStorage
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File
import java.security.SecureRandom

/**
 * قاعدة Room مشفَّرة بالكامل عبر SQLCipher. نستخدم net.zetetic:android-database-sqlcipher
 * (النسخة "الكلاسيكية" وليست sqlcipher-android الأحدث) عمداً: كلتاهما تعملان،
 * لكن هذه موثَّقة بشكل أوسع وأكثر اتساقاً عبر مصادر متعددة مستقلة، وهذا مشروع
 * شخصي غير موزَّع فقيد "16KB page size" الخاص بمتجر Google Play لا ينطبق هنا.
 *
 * كلمة مرور التشفير عشوائية بالكامل (32 بايت)، تُولَّد مرة واحدة وتُخزَّن هي
 * نفسها مشفَّرة عبر SecureStorage (Android Keystore) - لا توجد كلمة مرور
 * مكتوبة بالكود إطلاقاً.
 */
@Database(entities = [Transfer::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            SQLiteDatabase.loadLibs(context) // تحميل مكتبة SQLCipher الأصلية قبل أي استخدام
            val passphrase = getOrCreatePassphrase(context)
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(context, AppDatabase::class.java, "receipts_secure.db")
                .openHelperFactory(factory)
                .build()
        }

        private fun getOrCreatePassphrase(context: Context): ByteArray {
            val passphraseFile = File(context.filesDir, "db_passphrase.enc")
            SecureStorage.readAndDecrypt(passphraseFile)?.let {
                return SQLiteDatabase.getBytes(it.toCharArray())
            }

            val randomHex = ByteArray(32).also { SecureRandom().nextBytes(it) }
                .joinToString("") { b -> "%02x".format(b) }
            SecureStorage.encryptAndWrite(passphraseFile, randomHex)
            return SQLiteDatabase.getBytes(randomHex.toCharArray())
        }
    }
}
