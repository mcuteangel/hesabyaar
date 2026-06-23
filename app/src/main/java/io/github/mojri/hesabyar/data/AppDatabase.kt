package io.github.mojri.hesabyar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Transaction::class, Loan::class, Installment::class, PaymentHistory::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun loanDao(): LoanDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun paymentHistoryDao(): PaymentHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migrate monetary columns from Double (Toman) to Long (Rial)
                // Multiply all amounts by 1000 to convert Toman to Rial

                // transactions: amount column
                db.execSQL("CREATE TABLE transactions_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, category TEXT NOT NULL, amount INTEGER NOT NULL, description TEXT NOT NULL, personName TEXT, date INTEGER NOT NULL, dueDate INTEGER, installmentId INTEGER)")
                db.execSQL("INSERT INTO transactions_new (id, type, category, amount, description, personName, date, dueDate, installmentId) SELECT id, type, category, CAST(amount * 1000 AS INTEGER), description, personName, date, dueDate, installmentId FROM transactions")
                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")

                // loans: originalAmount, remainingAmount columns
                db.execSQL("CREATE TABLE loans_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, personName TEXT NOT NULL, type TEXT NOT NULL, originalAmount INTEGER NOT NULL, remainingAmount INTEGER NOT NULL, description TEXT NOT NULL, date INTEGER NOT NULL, isSettled INTEGER NOT NULL)")
                db.execSQL("INSERT INTO loans_new (id, personName, type, originalAmount, remainingAmount, description, date, isSettled) SELECT id, personName, type, CAST(originalAmount * 1000 AS INTEGER), CAST(remainingAmount * 1000 AS INTEGER), description, date, isSettled FROM loans")
                db.execSQL("DROP TABLE loans")
                db.execSQL("ALTER TABLE loans_new RENAME TO loans")

                // installments: amount column
                db.execSQL("CREATE TABLE installments_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amount INTEGER NOT NULL, dueDate INTEGER NOT NULL, isPaid INTEGER NOT NULL, reminderEnabled INTEGER NOT NULL, notes TEXT NOT NULL)")
                db.execSQL("INSERT INTO installments_new (id, title, amount, dueDate, isPaid, reminderEnabled, notes) SELECT id, title, CAST(amount * 1000 AS INTEGER), dueDate, isPaid, reminderEnabled, notes FROM installments")
                db.execSQL("DROP TABLE installments")
                db.execSQL("ALTER TABLE installments_new RENAME TO installments")

                // payment_history: amount column
                db.execSQL("CREATE TABLE payment_history_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, loanId INTEGER NOT NULL, amount INTEGER NOT NULL, date INTEGER NOT NULL, notes TEXT NOT NULL)")
                db.execSQL("INSERT INTO payment_history_new (id, loanId, amount, date, notes) SELECT id, loanId, CAST(amount * 1000 AS INTEGER), date, notes FROM payment_history")
                db.execSQL("DROP TABLE payment_history")
                db.execSQL("ALTER TABLE payment_history_new RENAME TO payment_history")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hesabyar_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
