package com.duoc.prestaaula.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.duoc.prestaaula.data.local.dao.AuditLogDao
import com.duoc.prestaaula.data.local.dao.AvailabilityDao
import com.duoc.prestaaula.data.local.dao.EquipmentBlockDao
import com.duoc.prestaaula.data.local.dao.EquipmentDao
import com.duoc.prestaaula.data.local.dao.LoanDao
import com.duoc.prestaaula.data.local.dao.LoanHistoryDao
import com.duoc.prestaaula.data.local.dao.UserDao
import com.duoc.prestaaula.data.local.entity.AuditLogEntity
import com.duoc.prestaaula.data.local.entity.AvailabilityEntity
import com.duoc.prestaaula.data.local.entity.EquipmentBlockEntity
import com.duoc.prestaaula.data.local.entity.EquipmentEntity
import com.duoc.prestaaula.data.local.entity.LoanEntity
import com.duoc.prestaaula.data.local.entity.LoanHistoryEntity
import com.duoc.prestaaula.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class, EquipmentEntity::class, LoanEntity::class, AvailabilityEntity::class,
        EquipmentBlockEntity::class, AuditLogEntity::class, LoanHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PrestaAulaDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun loanDao(): LoanDao
    abstract fun availabilityDao(): AvailabilityDao
    abstract fun equipmentBlockDao(): EquipmentBlockDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun loanHistoryDao(): LoanHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: PrestaAulaDatabase? = null

        fun getInstance(context: Context): PrestaAulaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrestaAulaDatabase::class.java,
                    "prestaaula.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
