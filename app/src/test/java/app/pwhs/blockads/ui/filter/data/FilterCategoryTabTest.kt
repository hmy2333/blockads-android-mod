package app.pwhs.blockads.ui.filter.data

import app.pwhs.blockads.data.entities.FilterList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterCategoryTabTest {

    @Test
    fun allTab_matchesAllFiltersRegardlessOfCategoryOrEnabledStatus() {
        val builtInFilter = FilterList(
            id = 1,
            name = "Test Ad Filter",
            url = "https://example.com/filter.txt",
            category = "Ads",
            isBuiltIn = true,
            isEnabled = false
        )
        val customFilter = FilterList(
            id = 2,
            name = "Custom Filter",
            url = "https://example.com/custom.txt",
            category = "Custom",
            isBuiltIn = false,
            isEnabled = true
        )

        assertTrue(builtInFilter.matchesCategory(FilterCategoryTab.ALL))
        assertTrue(customFilter.matchesCategory(FilterCategoryTab.ALL))
    }

    @Test
    fun allTab_isFirstTabInEntries() {
        assertEquals(FilterCategoryTab.ALL, FilterCategoryTab.entries.first())
    }
}
