package com.example.receiptscanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.receiptscanner.model.Transfer
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    @Query("SELECT * FROM transfers ORDER BY processedAt DESC")
    fun observeAll(): Flow<List<Transfer>>

    @Query("SELECT * FROM transfers ORDER BY processedAt DESC")
    suspend fun getAllOnce(): List<Transfer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transfer: Transfer)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transfers: List<Transfer>)

    @Update
    suspend fun update(transfer: Transfer)

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transfers")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transfers")
    suspend fun count(): Int
}
