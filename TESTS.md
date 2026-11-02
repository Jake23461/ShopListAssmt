# Tests

Tests for the "double-tap opens the wrong page" bug, written before any fix.
Results are from 2026-11-02, before the fix. The on-device tests ran on the
`Medium_Phone_API_37.0` emulator (Android 17), and the results are the same
across 3 recorded runs unless the table says otherwise.

How to run them. Use JDK 21 (see AI_LOG.md, Task 1), and have an emulator or device connected.

```powershell
.\gradlew.bat testDebugUnitTest           # unit tests (JVM)
.\gradlew.bat connectedDebugAndroidTest   # on-device tests
```

## Unit tests: `app/src/test/.../PagerPageTest.kt`

These test `PagerPage`, the page/ID logic taken out of `CategoryPagerAdapter`
with no change in behaviour. `FragmentStateAdapter` reuses the fragment it already
has for a page ID, so a position whose page changes must get a new ID.

| Test | Expected | Result |
|---|---|---|
| `shopMode_categoriesFirst_summaryLast` | Shop: categories at 0..n-1, Summary at n | ✅ Pass |
| `planMode_categoryListFirst_thenCategories` | Plan: category list at 0, category i at i+1 | ✅ Pass |
| `idsAreUniqueWithinOneLayout` | No two positions share an ID | ✅ Pass |
| `shopMode_summaryAndCategoryAtSamePosition_haveDifferentIds` | Summary (4 categories) and category 5 (20 categories), both at position 4, have different IDs | ❌ **Fail (bug):** both are `4` |
| `shopMode_whenCategoryCountChanges_positionsWhosePageChanges_getNewIds` | When the number of categories changes, every position whose page changes gets a new ID | ❌ **Fail (bug):** 4→20, position 4 is Summary→Category(4) but keeps ID `4` |
| `planAndShopPagesAtSamePosition_haveDifferentIds` | A Plan page and a Shop page at the same position have different IDs | ❌ **Fail (bug):** position 0 is ID `0` in both modes |

## On-device tests: `app/src/androidTest/.../PagerNavigationTest.kt`

Test data is written into SharedPreferences before launch, so no file picker is
used. Double-taps use Espresso `doubleClick()`.

| Test | Expected | Result |
|---|---|---|
| (a) `shopMode_afterImportChangesCategoryCount_doubleTapCategory5_opensItsPage`: 4 categories, Shop, go to Summary, Home, set 20 categories the way import does, SHOP, double-tap "Aisle 5" | Opens "Aisle 5" | ❌ **Fail (bug):** stays on "Summary", the stale fragment at position 4 |
| (b) `planMode_rotateThenShop_lastPageIsSummary`: Plan, last category page, recreate the activity (as rotation does), SHOP, go to last page | Last page is "Summary" | ❌ **Fail (bug):** shows "Frozen", a restored Plan-mode fragment |
| (c) `planMode_unchangedList_doubleTapEachCategory_opensItsPage` | Each of the 4 categories opens its own page | ❌ **Fail, not expected:** Frozen → "Dairy" (3/3 runs). Fruit, Dairy and Bakery are correct. |
| (c) `shopMode_unchangedList_doubleTapEachCategory_opensItsPage` | Each of the 4 categories opens its own page | ❌ **Fail, not expected:** Fruit → "Summary" (3/3 runs), Dairy → "Bakery" (1/3 runs). Others are correct. |
| `ExampleInstrumentedTest.useAppContext` | Package name is correct | ✅ Pass |

### Why the (c) controls fail

They were expected to pass, but they find a second real cause of the bug. It
isn't related to page IDs (see AI_LOG.md, Task 2b):

- The double-tap handlers change page on the **second finger-down**, before the
  finger is lifted. The jump moves the touched page away, Android cancels the touch
  on it, and the finger-up goes to the ViewPager's own RecyclerView instead.
- **Shop → first category:** that finger-up reaches the wrap-around swipe listener
  (`MainActivity.kt:96-121`). Its `touchStartX` is still `0`, so it reads the tap as
  a long swipe while `currentItem == 0` and jumps to the last page (Summary). Traced
  with a stack trace to `MainActivity.kt:108`.
- **Jumps of 3 or more pages (e.g. Plan → Frozen):** the pager stops on a page on the way
  to the target. The touch cancel and the finger-up going to the pager were captured,
  but the exact step that stops the scroll has not been traced yet.
- The same wrong pages happen with human-speed double-taps through `adb`
  (finger held about 80 ms). They don't happen with instant `adb input tap` taps, which is
  why Task 2 missed this.

The test waits longer than the 300 ms double-tap timeout before each double-tap.
Otherwise `GestureDetector` would pair the first tap with the previous step's tap,
which is a test artifact.
