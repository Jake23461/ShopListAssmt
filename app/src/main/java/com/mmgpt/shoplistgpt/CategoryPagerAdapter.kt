package com.mmgpt.shoplistgpt

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

import com.mmgpt.shoplistgpt.PlanCategoryFragment
import com.mmgpt.shoplistgpt.PlanCategoriesFragment

class CategoryPagerAdapter(
    activity: FragmentActivity,
    private val viewModel: ShoppingListViewModel,
    private val planMode: Boolean = false
) : FragmentStateAdapter(activity) {
    private val categoryCount: Int
        get() = viewModel.categories.value?.size ?: 0

    override fun getItemCount(): Int = categoryCount + 1

    override fun getItemId(position: Int): Long =
        PagerPage.idAt(position, categoryCount, planMode)

    override fun containsItem(itemId: Long): Boolean =
        PagerPage.containsId(itemId, categoryCount, planMode)

    override fun createFragment(position: Int): Fragment =
        when (val page = PagerPage.at(position, categoryCount, planMode)) {
            PagerPage.Overview ->
                if (planMode) PlanCategoriesFragment() else ShopCategoriesFragment()
            is PagerPage.Category ->
                if (planMode) PlanCategoryFragment.newInstance(page.index)
                else CategoryFragment.newInstance(page.index)
        }
}
