package com.lapaola.facturas

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Long,
    @ColumnInfo(defaultValue = "0") val stock: Int = 0
)

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long,
    val clientName: String,
    val phone: String,
    val address: String,
    val total: Long,
    @ColumnInfo(defaultValue = "''") val seller: String = "",
    @ColumnInfo(defaultValue = "''") val paymentMethod: String = ""
)

@Entity(
    tableName = "invoice_items",
    indices = [Index("invoiceId")],
    foreignKeys = [ForeignKey(
        entity = Invoice::class,
        parentColumns = ["id"],
        childColumns = ["invoiceId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val name: String,
    val qty: Int,
    val unitPrice: Long
)

@Dao
abstract class AppDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    abstract fun products(): Flow<List<Product>>

    @Insert
    abstract suspend fun insertProduct(p: Product)

    @Query("DELETE FROM products WHERE id = :id")
    abstract suspend fun deleteProduct(id: Long)

    @Query("UPDATE products SET stock = :stock WHERE id = :id")
    abstract suspend fun setStock(id: Long, stock: Int)

    /** Descuenta existencias (nunca baja de 0). Compara el nombre sin distinguir mayúsculas. */
    @Query("UPDATE products SET stock = MAX(stock - :qty, 0) WHERE name = :name COLLATE NOCASE")
    abstract suspend fun decStock(name: String, qty: Int)

    @Query("SELECT * FROM employees ORDER BY name COLLATE NOCASE")
    abstract fun employees(): Flow<List<Employee>>

    @Insert
    abstract suspend fun insertEmployee(e: Employee)

    @Query("DELETE FROM employees WHERE id = :id")
    abstract suspend fun deleteEmployee(id: Long)

    @Query("SELECT * FROM invoices ORDER BY id DESC")
    abstract fun invoices(): Flow<List<Invoice>>

    /** Clientes anteriores (para autocompletar), el más reciente primero. */
    @Query("SELECT * FROM invoices GROUP BY clientName ORDER BY MAX(dateMillis) DESC")
    abstract fun clients(): Flow<List<Invoice>>

    @Insert
    abstract suspend fun insertInvoice(i: Invoice): Long

    @Insert
    abstract suspend fun insertItems(items: List<InvoiceItem>)

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :id")
    abstract suspend fun itemsOf(id: Long): List<InvoiceItem>

    @Query("SELECT * FROM invoice_items WHERE invoiceId IN (:ids)")
    abstract suspend fun itemsOfMany(ids: List<Long>): List<InvoiceItem>

    @Query("DELETE FROM invoices WHERE id = :id")
    abstract suspend fun deleteInvoice(id: Long)

    @Transaction
    open suspend fun saveInvoice(inv: Invoice, items: List<InvoiceItem>): Long {
        val id = insertInvoice(inv)
        insertItems(items.map { it.copy(invoiceId = id) })
        items.forEach { decStock(it.name, it.qty) }
        return id
    }
}

@Database(
    entities = [Product::class, Invoice::class, InvoiceItem::class, Employee::class],
    version = 3,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN stock INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN seller TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS employees (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(ctx: Context): AppDb =
            Room.databaseBuilder(ctx, AppDb::class.java, "lapaola.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}

/** Métodos de pago disponibles al facturar. */
val PAYMENT_METHODS = listOf("Efectivo", "Transferencia QR", "Tarjeta", "Sistecrédito", "Addi")
const val NO_PAYMENT = "Sin definir"
fun payLabel(m: String): String = m.ifBlank { NO_PAYMENT }

const val DEFAULT_PRIMARY = "#7A4B2A"
const val DEFAULT_SECONDARY = "#FFFFFF"

class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    var printerIp: String
        get() = sp.getString("ip", "") ?: ""
        set(v) { sp.edit().putString("ip", v).apply() }

    var paperMm: Int
        get() = sp.getInt("paper", 80)
        set(v) { sp.edit().putInt("paper", v).apply() }

    var noAccents: Boolean
        get() = sp.getBoolean("noacc", false)
        set(v) { sp.edit().putBoolean("noacc", v).apply() }

    var businessName: String
        get() = sp.getString("bname", "Mi Negocio") ?: "Mi Negocio"
        set(v) { sp.edit().putString("bname", v).apply() }

    var logoOnReceipt: Boolean
        get() = sp.getBoolean("logo_receipt", false)
        set(v) { sp.edit().putBoolean("logo_receipt", v).apply() }

    var darkMode: Boolean
        get() = sp.getBoolean("dark", false)
        set(v) { sp.edit().putBoolean("dark", v).apply() }

    var primaryHex: String
        get() = sp.getString("primary", DEFAULT_PRIMARY) ?: DEFAULT_PRIMARY
        set(v) { sp.edit().putString("primary", v).apply() }

    var secondaryHex: String
        get() = sp.getString("secondary", DEFAULT_SECONDARY) ?: DEFAULT_SECONDARY
        set(v) { sp.edit().putString("secondary", v).apply() }

    var lastSeller: String
        get() = sp.getString("last_seller", "") ?: ""
        set(v) { sp.edit().putString("last_seller", v).apply() }
}
