package io.github.mojri.hesabyar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Transaction::class, Loan::class, Installment::class, PaymentHistory::class, Category::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun loanDao(): LoanDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun paymentHistoryDao(): PaymentHistoryDao
    abstract fun categoryDao(): CategoryDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create categories table
                db.execSQL("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, key TEXT NOT NULL, icon TEXT NOT NULL, color INTEGER NOT NULL, type TEXT NOT NULL, isDefault INTEGER NOT NULL)")

                // Insert default categories
                val defaults = listOf(
                    "('خوراک', 'Food', 'Restaurant', 808464432, 'EXPENSE', 1)",
                    "('حمل و نقل', 'Transportation', 'DirectionsCar', 4294945536, 'EXPENSE', 1)",
                    "('خرید', 'Shopping', 'ShoppingBag', 4283215591, 'EXPENSE', 1)",
                    "('قبوض', 'Bills', 'ReceiptLong', 4278241576, 'EXPENSE', 1)",
                    "('اقساط', 'Installments', 'CreditCard', 4294198070, 'EXPENSE', 1)",
                    "('وام و قرض', 'Loans', 'HistoryEdu', 4286578688, 'BOTH', 1)",
                    "('درآمد', 'Income', 'Paid', 808464432, 'INCOME', 1)",
                    "('سایر', 'Other', 'Paid', 4285867125, 'BOTH', 1)"
                )
                defaults.forEach { values ->
                    db.execSQL("INSERT INTO categories (name, key, icon, color, type, isDefault) VALUES $values")
                }

                // Create new transactions table with categoryId
                db.execSQL("CREATE TABLE transactions_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, categoryId INTEGER NOT NULL, amount INTEGER NOT NULL, description TEXT NOT NULL, personName TEXT, date INTEGER NOT NULL, dueDate INTEGER, installmentId INTEGER)")

                // Map old string categories to new IDs
                db.execSQL("""
                    INSERT INTO transactions_new (id, type, categoryId, amount, description, personName, date, dueDate, installmentId)
                    SELECT t.id, t.type,
                        CASE t.category
                            WHEN 'Food' THEN (SELECT id FROM categories WHERE key = 'Food' LIMIT 1)
                            WHEN 'Transportation' THEN (SELECT id FROM categories WHERE key = 'Transportation' LIMIT 1)
                            WHEN 'Shopping' THEN (SELECT id FROM categories WHERE key = 'Shopping' LIMIT 1)
                            WHEN 'Bills' THEN (SELECT id FROM categories WHERE key = 'Bills' LIMIT 1)
                            WHEN 'Installments' THEN (SELECT id FROM categories WHERE key = 'Installments' LIMIT 1)
                            WHEN 'Loans' THEN (SELECT id FROM categories WHERE key = 'Loans' LIMIT 1)
                            WHEN 'Income' THEN (SELECT id FROM categories WHERE key = 'Income' LIMIT 1)
                            ELSE (SELECT id FROM categories WHERE key = 'Other' LIMIT 1)
                        END,
                        t.amount, t.description, t.personName, t.date, t.dueDate, t.installmentId
                    FROM transactions t
                """)

                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hesabyar_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
