package com.mmgpt.shoplistgpt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FragmentStateAdapter reuses an existing fragment whenever a position's page ID is
 * unchanged. So any position whose page changes must also get a new ID, otherwise
 * the old fragment (e.g. the Summary) keeps showing where a category page belongs.
 */
class PagerPageTest {

    // --- Page layout (current behaviour, should pass) ---

    @Test
    fun shopMode_categoriesFirst_summaryLast() {
        assertEquals(PagerPage.Category(0), PagerPage.at(0, 4, planMode = false))
        assertEquals(PagerPage.Category(3), PagerPage.at(3, 4, planMode = false))
        assertEquals(PagerPage.Overview, PagerPage.at(4, 4, planMode = false))
    }

    @Test
    fun planMode_categoryListFirst_thenCategories() {
        assertEquals(PagerPage.Overview, PagerPage.at(0, 4, planMode = true))
        assertEquals(PagerPage.Category(0), PagerPage.at(1, 4, planMode = true))
        assertEquals(PagerPage.Category(3), PagerPage.at(4, 4, planMode = true))
    }

    @Test
    fun idsAreUniqueWithinOneLayout() {
        for (planMode in listOf(false, true)) {
            val ids = (0..4).map { PagerPage.idAt(it, 4, planMode) }
            assertEquals("duplicate IDs in planMode=$planMode: $ids", ids.size, ids.toSet().size)
        }
    }

    // --- Page IDs (the bug: these fail until IDs stop being plain positions) ---

    @Test
    fun shopMode_summaryAndCategoryAtSamePosition_haveDifferentIds() {
        // Position 4 is the Summary with 4 categories, but category "5" with 20.
        assertEquals(PagerPage.Overview, PagerPage.at(4, 4, planMode = false))
        assertEquals(PagerPage.Category(4), PagerPage.at(4, 20, planMode = false))

        assertNotEquals(
            "Summary and category 5 share an ID, so the stale Summary fragment is reused",
            PagerPage.idAt(4, 4, planMode = false),
            PagerPage.idAt(4, 20, planMode = false)
        )
    }

    @Test
    fun shopMode_whenCategoryCountChanges_positionsWhosePageChanges_getNewIds() {
        val countChanges = listOf(4 to 20, 20 to 4, 4 to 5, 5 to 4, 0 to 1)
        for ((before, after) in countChanges) {
            for (position in 0..minOf(before, after)) {
                val oldPage = PagerPage.at(position, before, planMode = false)
                val newPage = PagerPage.at(position, after, planMode = false)
                if (oldPage == newPage) continue
                assertNotEquals(
                    "count $before -> $after: position $position changed from $oldPage to $newPage but kept its ID",
                    PagerPage.idAt(position, before, planMode = false),
                    PagerPage.idAt(position, after, planMode = false)
                )
            }
        }
    }

    @Test
    fun containsId_matchesCurrentPagesOnly() {
        val summaryWith4 = PagerPage.idAt(4, 4, planMode = false)
        assertTrue(PagerPage.containsId(summaryWith4, 4, planMode = false))
        // With 20 categories the Summary has moved to position 20 and keeps its ID;
        // position 4 now has a category ID, so nothing stale is kept there.
        assertEquals(summaryWith4, PagerPage.idAt(20, 20, planMode = false))
        // A Plan-mode page ID is never valid in the Shop pager.
        assertFalse(PagerPage.containsId(PagerPage.idAt(1, 4, planMode = true), 4, planMode = false))
        // A category that no longer exists is not contained.
        assertFalse(PagerPage.containsId(PagerPage.idAt(10, 20, planMode = false), 4, planMode = false))
        for (position in 0..4) {
            assertTrue(PagerPage.containsId(PagerPage.idAt(position, 4, true), 4, planMode = true))
        }
    }

    @Test
    fun planAndShopPagesAtSamePosition_haveDifferentIds() {
        // After rotation in Plan mode, ViewPager2 restores the Plan fragments into the
        // new Shop-mode adapter by ID. The two modes use different fragment classes at
        // every position, so their IDs must never match.
        for (position in 0..4) {
            assertNotEquals(
                "position $position: Plan page ${PagerPage.at(position, 4, true)} and " +
                    "Shop page ${PagerPage.at(position, 4, false)} share an ID",
                PagerPage.idAt(position, 4, planMode = true),
                PagerPage.idAt(position, 4, planMode = false)
            )
        }
    }
}
