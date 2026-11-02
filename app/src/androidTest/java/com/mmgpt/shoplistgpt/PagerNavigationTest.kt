package com.mmgpt.shoplistgpt

import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.ViewConfiguration
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.doubleClick
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import org.hamcrest.Matchers.allOf
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Double-tapping a category in the overview page (first page in Plan, last in Shop)
 * should open that category's page. Data is seeded into SharedPreferences before
 * launch, so no file picker is involved.
 */
@RunWith(AndroidJUnit4::class)
class PagerNavigationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val summary = context.getString(R.string.summary_heading)
    private val fourCategories = listOf("Fruit", "Dairy", "Bakery", "Frozen")
    private lateinit var scenario: ActivityScenario<MainActivity>

    @After
    fun tearDown() {
        scenario.close()
        prefs().edit().clear().commit()
    }

    // (a) Bug: import changes the number of categories while the Shop pager is alive.
    @Test
    fun shopMode_afterImportChangesCategoryCount_doubleTapCategory5_opensItsPage() {
        launchWith(fourCategories)
        onView(withId(R.id.btnShop)).perform(click())
        goToPage(4)
        assertEquals("precondition: page 4 is the Summary", summary, visibleHeading())

        onView(withId(R.id.action_home)).perform(click())
        // What importXlsx() does after parsing: replace the categories, then showShopping().
        val aisles = (1..20).map { "Aisle $it" }
        onActivity { viewModel(it).setCategories(categories(aisles, firstId = 100)) }
        onView(withId(R.id.btnShop)).perform(click())

        // The app comes back on position 4, which is now category 5 (before the fix
        // it still showed the old Summary fragment).
        assertEquals("page at position 4 after import", "Aisle 5", visibleHeading())

        // Double-tap category 5 from the Summary, now the last page.
        goToPage(onActivity { pager(it).adapter!!.itemCount - 1 })
        assertEquals("precondition: on the Summary", summary, visibleHeading())
        doubleTapRow("Aisle 5")
        assertEquals("page opened by double-tapping 'Aisle 5'", "Aisle 5", visibleHeading())
    }

    // (b) Bug: Plan-mode fragments restored into the Shop pager after rotation.
    @Test
    fun planMode_rotateThenShop_lastPageIsSummary() {
        launchWith(fourCategories)
        onView(withId(R.id.btnPlan)).perform(click())
        goToPage(4) // last category page in Plan mode
        scenario.recreate() // same lifecycle as rotating the device

        onView(withId(R.id.btnShop)).perform(click())
        val lastPage = onActivity { pager(it).adapter!!.itemCount - 1 }
        goToPage(lastPage)

        assertEquals("last Shop page after rotating in Plan mode", summary, visibleHeading())
    }

    // (c) Control: unchanged list, Plan mode.
    @Test
    fun planMode_unchangedList_doubleTapEachCategory_opensItsPage() {
        launchWith(fourCategories)
        onView(withId(R.id.btnPlan)).perform(click())
        assertEachRowOpensItsPage(overviewPage = 0)
    }

    // (c) Control: unchanged list, Shop mode.
    @Test
    fun shopMode_unchangedList_doubleTapEachCategory_opensItsPage() {
        launchWith(fourCategories)
        onView(withId(R.id.btnShop)).perform(click())
        assertEachRowOpensItsPage(overviewPage = 4)
    }

    // (d) Double-taps at human speed (finger down ~80 ms per tap) instead of Espresso's
    // very short taps. Before the fix, the page changed while the finger was still down.
    @Test
    fun planMode_humanSpeedDoubleTap_eachCategory_opensItsPage() {
        launchWith(fourCategories)
        onView(withId(R.id.btnPlan)).perform(click())
        assertEachRowOpensItsPage(overviewPage = 0, humanSpeed = true)
    }

    @Test
    fun shopMode_humanSpeedDoubleTap_eachCategory_opensItsPage() {
        launchWith(fourCategories)
        onView(withId(R.id.btnShop)).perform(click())
        assertEachRowOpensItsPage(overviewPage = 4, humanSpeed = true)
    }

    // (e) Wrap-around swipe still works at the ends, and ordinary swipes don't wrap.
    @Test
    fun planMode_wrapAroundSwipe_onlyAtTheEnds() {
        launchWith(fourCategories)
        onView(withId(R.id.btnPlan)).perform(click())
        assertSwipes(
            listOf("Categories", "Fruit", "Dairy", "Bakery", "Frozen")
        )
    }

    @Test
    fun shopMode_wrapAroundSwipe_onlyAtTheEnds() {
        launchWith(fourCategories)
        onView(withId(R.id.btnShop)).perform(click())
        assertSwipes(fourCategories + summary)
    }

    /** Double-taps every category from the overview page; reports all wrong rows at once. */
    private fun assertEachRowOpensItsPage(overviewPage: Int, humanSpeed: Boolean = false) {
        val results = fourCategories.map { name ->
            goToPage(overviewPage)
            if (humanSpeed) humanDoubleTapRow(name) else doubleTapRow(name)
            "'$name' -> '${visibleHeading()}'"
        }
        val expected = fourCategories.map { "'$it' -> '$it'" }
        assertEquals("double-tap results", expected, results)
    }

    /**
     * [pages] are the headings in pager order. Swiping back on the first page wraps to
     * the last, swiping forward on the last wraps to the first, and swipes in between
     * move one page. Reports all swipes at once.
     */
    private fun assertSwipes(pages: List<String>) {
        val last = pages.size - 1
        val cases = listOf(
            Triple(0, "forward", pages[1]),
            Triple(1, "back", pages[0]),
            Triple(0, "back", pages[last]),
            Triple(last, "forward", pages[0]),
            Triple(last, "back", pages[last - 1])
        )
        val results = cases.map { (start, direction, _) ->
            goToPage(start)
            Thread.sleep(ViewConfiguration.getDoubleTapTimeout() + 200L)
            onView(withId(R.id.viewPager)).perform(if (direction == "forward") swipeLeft() else swipeRight())
            waitForPagerIdle()
            "${pages[start]} swipe $direction -> ${visibleHeading()}"
        }
        val expected = cases.map { (start, direction, end) -> "${pages[start]} swipe $direction -> $end" }
        assertEquals("swipe results", expected, results)
    }

    // --- helpers ---

    private fun prefs() = context.getSharedPreferences("state", Context.MODE_PRIVATE)

    private fun categories(headings: List<String>, firstId: Long = 0L): List<ShoppingCategory> {
        var itemId = firstId * 10
        return headings.mapIndexed { i, heading ->
            ShoppingCategory(firstId + i, heading, listOf(ShoppingItem(itemId++, "$heading item")))
        }
    }

    /** Writes the same JSON that ShoppingListViewModel.saveState() produces, then launches. */
    private fun launchWith(headings: List<String>) {
        val cats = JSONArray()
        categories(headings).forEach { cat ->
            val items = JSONArray()
            cat.items.forEach { items.put(JSONObject().put("id", it.id).put("name", it.name)) }
            cats.put(JSONObject().put("id", cat.id).put("heading", cat.heading).put("items", items))
        }
        val state = JSONObject().put("planDate", "2026-11-01").put("categories", cats)
        prefs().edit().clear().putString("state", state.toString()).commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun <T> onActivity(block: (MainActivity) -> T): T {
        var result: Any? = null
        scenario.onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun viewModel(activity: MainActivity) =
        ViewModelProvider(activity)[ShoppingListViewModel::class.java]

    private fun pager(activity: MainActivity): ViewPager2 =
        activity.findViewById(R.id.viewPager)

    private fun goToPage(position: Int) {
        onActivity { pager(it).setCurrentItem(position, false) }
        waitForPagerIdle()
    }

    private fun doubleTapRow(name: String) {
        // Let any earlier tap expire, or GestureDetector pairs it with this one
        // (rows are closer together than the double-tap slop).
        Thread.sleep(ViewConfiguration.getDoubleTapTimeout() + 200L)
        onView(allOf(withId(R.id.tvName), withText(name), isDisplayed())).perform(doubleClick())
        waitForPagerIdle()
    }

    /** Double-tap with real input events, holding each tap for ~80 ms like a finger. */
    private fun humanDoubleTapRow(name: String) {
        Thread.sleep(ViewConfiguration.getDoubleTapTimeout() + 200L)
        var x = 0
        var y = 0
        onView(allOf(withId(R.id.tvName), withText(name), isDisplayed())).check { view, noView ->
            if (view == null) throw noView
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            x = location[0] + view.width / 2
            y = location[1] + view.height / 2
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (action in listOf("DOWN", "UP", "DOWN", "UP")) {
            ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("input motionevent $action $x $y")
            ).use { it.readBytes() }
            Thread.sleep(80)
        }
        waitForPagerIdle()
    }

    /** Espresso does not wait for ViewPager2's smooth scroll, so poll its scroll state. */
    private fun waitForPagerIdle() {
        onIdle()
        repeat(50) {
            if (onActivity { pager(it).scrollState } == ViewPager2.SCROLL_STATE_IDLE) return
            Thread.sleep(100)
        }
    }

    /** Heading text of the page currently on screen. */
    private fun visibleHeading(): String {
        var text = ""
        onView(allOf(withId(R.id.tvHeading), isDisplayed())).check { view, noView ->
            if (view == null) throw noView
            text = (view as TextView).text.toString()
        }
        return text
    }
}
