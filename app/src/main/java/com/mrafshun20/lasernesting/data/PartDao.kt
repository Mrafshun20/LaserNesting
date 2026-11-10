package com.mrafshun20.lasernesting.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PartDao {

    @Query("SELECT * FROM parts ORDER BY name ASC")
    fun observeAll(): Flow<List<PartEntity>>

    @Query("SELECT * FROM parts ORDER BY name ASC")
    suspend fun getAll(): List<PartEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(part: PartEntity)

    @Delete
    suspend fun delete(part: PartEntity)

    @Query("DELETE FROM parts")
    suspend fun deleteAll()
}
