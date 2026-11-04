package app.pwhs.blockads.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MilestoneGatingTest {

    @Test
    fun disabledToggleSuppressesMilestone() {
        assertNull(NotificationHelper.unseenMilestone(blocked = 60_000, lastSeen = 0, enabled = false))
    }

    @Test
    fun enabledToggleReturnsHighestReachedMilestone() {
        assertEquals(50_000L, NotificationHelper.unseenMilestone(blocked = 60_000, lastSeen = 0, enabled = true))
    }

    @Test
    fun alreadySeenMilestoneIsNotShownAgain() {
        assertNull(NotificationHelper.unseenMilestone(blocked = 60_000, lastSeen = 50_000, enabled = true))
    }

    @Test
    fun belowFirstMilestoneReturnsNull() {
        assertNull(NotificationHelper.unseenMilestone(blocked = 999, lastSeen = 0, enabled = true))
    }

    @Test
    fun backupDefaultMatchesDatastoreDefault() {
        assertEquals(false, app.pwhs.blockads.data.entities.SettingsBackup().milestoneNotificationsEnabled)
    }
}
