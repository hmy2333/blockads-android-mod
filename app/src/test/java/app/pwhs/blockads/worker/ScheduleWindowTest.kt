package app.pwhs.blockads.worker

import app.pwhs.blockads.data.entities.FirewallRule
import app.pwhs.blockads.service.FirewallManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for firewall schedule time windows. */
class ScheduleWindowTest {

    private fun rule(sh: Int, sm: Int, eh: Int, em: Int) = FirewallRule(
        packageName = "p", scheduleEnabled = true,
        scheduleStartHour = sh, scheduleStartMinute = sm, scheduleEndHour = eh, scheduleEndMinute = em
    )

    private fun firewallActive(r: FirewallRule, h: Int, m: Int) = FirewallManager.isWithinSchedule(r, h * 60 + m)

    @Test
    fun `same-day window covers its start and interior`() {
        assertTrue(firewallActive(rule(8, 0, 17, 0), 8, 0))
        assertTrue(firewallActive(rule(8, 0, 17, 0), 16, 59))
        assertFalse(firewallActive(rule(8, 0, 17, 0), 7, 59))
    }

    @Test
    fun `overnight window wraps midnight`() {
        assertTrue(firewallActive(rule(22, 0, 6, 0), 23, 30))
        assertTrue(firewallActive(rule(22, 0, 6, 0), 0, 0))
        assertFalse(firewallActive(rule(22, 0, 6, 0), 12, 0))
    }
}

