package com.mrafshun20.lasernesting.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PartDao {

    @Query("SELECT * FROM parts ORDER BY priority DESC, id ASC")
    fun observeAll(): Flow<List<PartEntity>>

    @Query("SELECT * FROM parts ORDER BY priority DESC, id ASC")
    suspend fun getAll(): List<PartEntity>

    @Query("SELECT * FROM parts WHERE id = :id")
    suspend fun getById(id: Long): PartEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(part: PartEntity)

    @Insert
    suspend fun insert(part: PartEntity): Long

    @Insert
    suspend fun insertAll(parts: List<PartEntity>): List<Long>

    @Update
    suspend fun update(part: PartEntity)

    @Delete
    suspend fun delete(part: PartEntity)

    @Query("DELETE FROM parts")
    suspend fun deleteAll()
}
