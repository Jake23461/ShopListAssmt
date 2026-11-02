package com.mmgpt.shoplistgpt

/**
 * What a ViewPager position shows. Kept free of Android classes so the
 * position/ID logic used by [CategoryPagerAdapter] can be unit tested.
 */
sealed class PagerPage {
    /** The extra page: the category list in Plan mode (first), the summary in Shop mode (last). */
    object Overview : PagerPage() {
        override fun toString() = "Overview"
    }

    data class Category(val index: Int) : PagerPage()

    companion object {
        private const val PLAN_BIT = 1L
        private const val OVERVIEW_BIT = 2L

        fun at(position: Int, categoryCount: Int, planMode: Boolean): PagerPage =
            if (planMode) {
                if (position == 0) Overview else Category(position - 1)
            } else {
                if (position == categoryCount) Overview else Category(position)
            }

        /**
         * The ID FragmentStateAdapter uses to decide whether an existing fragment
         * can be reused. It describes what the page shows (mode, and Overview or
         * which category index), not where it is, so a fragment is only reused for
         * the same kind of page. Category fragments look their category up by
         * index, so the index is what identifies them.
         */
        fun idAt(position: Int, categoryCount: Int, planMode: Boolean): Long {
            val mode = if (planMode) PLAN_BIT else 0L
            return when (val page = at(position, categoryCount, planMode)) {
                Overview -> OVERVIEW_BIT or mode
                is Category -> (page.index.toLong() shl 2) or mode
            }
        }

        /** True if some position currently has this ID (FragmentStateAdapter.containsItem). */
        fun containsId(id: Long, categoryCount: Int, planMode: Boolean): Boolean =
            (0..categoryCount).any { idAt(it, categoryCount, planMode) == id }
    }
}
