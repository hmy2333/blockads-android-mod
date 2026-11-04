package app.pwhs.blockadstv.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "firewall_rules",
    indices = [Index(value = ["packageName"], unique = true)]
)
data class FirewallRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val isEnabled: Boolean = true,
)
