# Tests

Tests for the "double-tap opens the wrong page" bug. They were written before the fixes, and
then run again after each fix. The on-device tests run on the `Medium_Phone_API_37.0` emulator
(Android 17).

- **Before fix:** the code before either fix (2026-11-02). For tests added later
  (d, e), this column is the code with fix 1 applied but not fix 2.
- **After fix:** both fixes applied (`fef1b1d` page IDs, `728a18c` double-tap/swipe).
  All tests passed in 3 out of 3 consecutive full runs.

How to run them. Use JDK 21 (see AI_LOG.md, Task 1), and have an emulator or device connected.

```powershell
.\gradlew.bat testDebugUnitTest           # unit tests (JVM)
.\gradlew.bat connectedDebugAndroidTest   # on-device tests
```

## Unit tests: `app/src/test/.../PagerPageTest.kt`

These test `PagerPage`, the page and page-ID logic used by `CategoryPagerAdapter`.
`FragmentStateAdapter` reuses the fragment it already has for a page ID, so a position
whose page changes must get a new ID.

| Test | Expected | Before fix | After fix |
|---|---|---|---|
| `shopMode_categoriesFirst_summaryLast` | Shop: categories at 0..n-1, Summary at n | ✅ Pass | ✅ Pass |
| `planMode_categoryListFirst_thenCategories` | Plan: category list at 0, category i at i+1 | ✅ Pass | ✅ Pass |
| `idsAreUniqueWithinOneLayout` | No two positions share an ID | ✅ Pass | ✅ Pass |
| `shopMode_summaryAndCategoryAtSamePosition_haveDifferentIds` | Summary (4 categories) and category 5 (20 categories), both at position 4, have different IDs | ❌ Fail: both `4` | ✅ Pass |
| `shopMode_whenCategoryCountChanges_positionsWhosePageChanges_getNewIds` | When the number of categories changes, every position whose page changes gets a new ID | ❌ Fail: 4→20, position 4 keeps ID `4` | ✅ Pass |
| `planAndShopPagesAtSamePosition_haveDifferentIds` | A Plan page and a Shop page at the same position have different IDs | ❌ Fail: position 0 is `0` in both | ✅ Pass |
| `containsId_matchesCurrentPagesOnly` *(added with fix 1)* | `containsItem` is true only for IDs of the current pages | n/a (default `containsItem`) | ✅ Pass |

## On-device tests: `app/src/androidTest/.../PagerNavigationTest.kt`

Test data is written into SharedPreferences before launch, so no file picker is used.
(a)–(c) use Espresso `doubleClick()`, which holds each tap for only a few ms.
(d) injects touchscreen events at finger speed instead: each tap held 80 ms, 80 ms apart.

| Test | Expected | Before fix | After fix |
|---|---|---|---|
| (a) `shopMode_afterImportChangesCategoryCount_doubleTapCategory5_opensItsPage`: 4 categories, Shop, go to Summary, Home, set 20 categories the way import does, SHOP. Check position 4, then double-tap "Aisle 5" on the Summary | Position 4 is "Aisle 5", and the double-tap opens "Aisle 5" | ❌ Position 4 shows a stale "Summary". With fix 1 only, the double-tap lands on "Aisle 6"/"Aisle 7" (cause 2) | ✅ Pass |
| (b) `planMode_rotateThenShop_lastPageIsSummary`: Plan, last category page, recreate the activity (as rotation does), SHOP, go to last page | Last page is "Summary" | ❌ "Frozen" (restored Plan fragment) | ✅ Pass |
| (c) `planMode_unchangedList_doubleTapEachCategory_opensItsPage` | Each category opens its own page | ❌ Frozen → "Dairy" | ✅ Pass |
| (c) `shopMode_unchangedList_doubleTapEachCategory_opensItsPage` | Each category opens its own page | ❌ Fruit → "Summary" (sometimes also Dairy → "Bakery") | ✅ Pass |
| (d) `planMode_humanSpeedDoubleTap_eachCategory_opensItsPage` *(added before fix 2)* | Each category opens its own page | ❌ e.g. Bakery → "Fruit", Frozen → "Dairy" | ✅ Pass |
| (d) `shopMode_humanSpeedDoubleTap_eachCategory_opensItsPage` *(added before fix 2)* | Each category opens its own page | ❌ Fruit → "Summary", Dairy → "Frozen" | ✅ Pass |
| (e) `planMode_wrapAroundSwipe_onlyAtTheEnds` *(added before fix 2)* | Back on first page → last page; forward on last → first; other swipes move one page | ❌ Forward on the last page does not wrap | ✅ Pass |
| (e) `shopMode_wrapAroundSwipe_onlyAtTheEnds` *(added before fix 2)* | Same, in Shop mode | ❌ Forward on the last page does not wrap | ✅ Pass |
| `ExampleInstrumentedTest.useAppContext` | Package name is correct | ✅ Pass | ✅ Pass |

Manual check after the fix: human-timed `adb input motionevent` double-taps on every row,
3 times each, in both modes. All 24 opened the right page (before the fix, 14 of 24 were wrong).

## The two causes and their fixes

1. **Pages were remembered by position** (tests a, b and the unit tests). `FragmentStateAdapter`
   used the position as the page ID. So after the number of categories changed, or after
   rotating in Plan mode, an old fragment (Summary or a Plan page) was reused where a different
   page belongs.
   **Fix:** `PagerPage.idAt` builds the ID from what the page shows: the mode, plus either
   Overview or the category index. `CategoryPagerAdapter.containsItem` matches those IDs.
2. **A double-tap was read as a swipe** (tests c, d, e). The page changed on the second
   *finger-down*, so the finger-up went to the ViewPager. The wrap-around listener (whose start
   position was stale) or the pager's own scrolling then moved to another page.
   **Fix:** the double-tap handlers change page on the second *finger-up*. The wrap-around
   listener is now a `RecyclerView.OnItemTouchListener`, so it sees where each gesture starts,
   ignores gestures it didn't see start, and only wraps when the swipe starts and ends on an
   end page.

## Notes on the test setup

- Each double-tap waits longer than the 300 ms double-tap timeout first. Otherwise
  `GestureDetector` pairs it with the previous step's tap.
- (d) Sometimes the emulator delays injected events enough that they no longer count as a
  double-tap. Then nothing happens, both before and after the fix. In that case only, the
  helper tries once more. A wrong page is never retried.
