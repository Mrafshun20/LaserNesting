package com.mrafshun20.lasernesting.data

import kotlinx.coroutines.flow.Flow

/**
 * Single access point for parts and settings persistence.
 * Settings always exist: the first read inserts the default row.
 */
class NestingRepository(private val db: AppDatabase) {

    private val partDao = db.partDao()
    private val settingsDao = db.settingsDao()

    fun observeParts(): Flow<List<PartEntity>> = partDao.observeAll()

    suspend fun getParts(): List<PartEntity> = partDao.getAll()

    suspend fun upsertPart(part: PartEntity) = partDao.upsert(part)

    suspend fun deletePart(part: PartEntity) = partDao.delete(part)

    suspend fun deleteAllParts() = partDao.deleteAll()

    fun observeSettings(): Flow<SettingsEntity?> = settingsDao.observe()

    suspend fun getSettings(): SettingsEntity {
        return settingsDao.get() ?: SettingsEntity().also { settingsDao.upsert(it) }
    }

    suspend fun updateSettings(settings: SettingsEntity) = settingsDao.upsert(settings)
}
