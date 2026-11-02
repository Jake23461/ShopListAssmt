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
        fun at(position: Int, categoryCount: Int, planMode: Boolean): PagerPage =
            if (planMode) {
                if (position == 0) Overview else Category(position - 1)
            } else {
                if (position == categoryCount) Overview else Category(position)
            }

        /**
         * The ID FragmentStateAdapter uses to decide whether an existing fragment
         * can be reused for a position. Same as the adapter's default (the position).
         */
        @Suppress("UNUSED_PARAMETER") // part of the signature the page ID should depend on
        fun idAt(position: Int, categoryCount: Int, planMode: Boolean): Long =
            position.toLong()
    }
}
