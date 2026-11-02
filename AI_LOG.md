# AI Log

Running log of AI-assisted maintenance on ShopListGPT (Claude Code, model claude-opus-5-5).
Each entry records the request, the actions taken, files changed, and any problems.

---

## Setup: Repoint remote, push history, architecture overview (2026-11-01)

> Note: this step was committed as `79a72b9 "Task 1: Add AI_LOG.md ..."` before the assignment's
> task numbering was known. It is a setup step; the assignment's Task 1 is the build task below.
> The commit message was left as-is to avoid rewriting pushed history.

**Request**
- Read AGENTS.md and explore the codebase (MainActivity, CategoryPagerAdapter,
  PlanCategoriesFragment, ShopCategoriesFragment, PlanCategoryFragment,
  CategoryFragment, ShoppingListViewModel, adapter/).
- Change `origin` from the lecturer's repo to `https://github.com/Jake23461/ShopListAssmt`.
- Push `main` with full history and confirm `git log` shows the original commits.
- Summarise the architecture: Plan vs Shop modes and how ViewPager pages are built.

**Actions**
1. Read AGENTS.md and the source files listed above, plus `ShoppingItem.kt` and `ShoppingCategory.kt`.
2. `git remote set-url origin https://github.com/Jake23461/ShopListAssmt`
   (was `https://github.com/mmnuig/ShopListAssmt.git`).
3. `git push -u origin main`. Result: `* [new branch] main -> main`. `git ls-remote origin`
   shows `refs/heads/main` at `e285176`, the same as local HEAD.
4. `git log` shows the two original commits intact:
   - `e285176 Added some icons that were missing.`
   - `8bd95c5 Initial Shopping List app code for programming assignments.`
5. Created this log and committed it.

**Files changed**
- `AI_LOG.md` (new)

**Problems**
- None. The remote was empty, so the push was a plain fast-forward. No history was rewritten.

**Observations recorded for later tasks (no code changed)**
- `ShoppingCategory.remainingCount` actually counts *checked* items, so the name is misleading.
- `CategoryPagerAdapter` does not override `getItemId`/`containsItem`, so fragments are keyed by
  position. `MainActivity` only calls `notifyDataSetChanged()` when the number of categories
  changes, so reordering categories can leave stale pages.
- `onBackPressed()` is deprecated in recent AndroidX.

---

## Task 1: Get the project building from the command line (2026-11-01)

**Request**
- Find the Java 21 JDK the user downloaded, use it as `JAVA_HOME`, and run
  `gradlew.bat assembleDebug` and then `gradlew.bat test`.
- Fix only what is needed to build. Do not upgrade Gradle, AGP or Kotlin.
- Make sure `build/`, `.gradle/` and `local.properties` are not committed.

**JDK issue (found by the user in Android Studio)**
- Android Studio's bundled JDK is Java 25. Gradle 8.11.1 (the project's wrapper version) does not
  support it, and sync failed with "Incompatible Gradle JVM version".
- Fix: the user downloaded JetBrains Runtime 21 (`jbr-21.0.11`) and selected it as the Gradle JDK in
  Android Studio. After that, sync and build worked.
- For command-line builds the JDK is at `C:\Users\CS-Guest\.jdks\jbr-21.0.11`
  (`openjdk version "21.0.11"`). Each command was run with:
  ```powershell
  $env:JAVA_HOME = 'C:\Users\CS-Guest\.jdks\jbr-21.0.11'
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  ```
  This only sets `JAVA_HOME` for that shell session. No machine-wide or project settings were changed.

**Actions and results**
1. `gradlew.bat assembleDebug`: **BUILD SUCCESSFUL**. The APK is at
   `app/build/outputs/apk/debug/app-debug.apk`. The only warnings were these, and none of them stop
   the build:
   - AGP recommends removing `package="com.mmgpt.shoplistgpt"` from `AndroidManifest.xml`
     (the `namespace` setting in Gradle replaces it).
   - "Deprecated Gradle features were used ... incompatible with Gradle 9.0."
2. `gradlew.bat test`: **BUILD FAILED** in `:app:compileDebugUnitTestKotlin`:
   ```
   e: .../ExampleUnitTest.kt:13:6 Unresolved reference: Test
   e: .../ExampleUnitTest.kt:15:9 Unresolved reference: assertEquals
   ```
   Cause: `app/build.gradle.kts` has no JUnit test dependency. The version catalog
   (`gradle/libs.versions.toml`) already defines `junit = 4.13.2`; it just isn't used.
3. Fix: added `testImplementation(libs.junit)` to `app/build.gradle.kts`. This uses the existing
   catalog entry, so no version numbers changed.
4. Ran `gradlew.bat test` again: **BUILD SUCCESSFUL**. `ExampleUnitTest` ran 1 test with 0 failures
   in both the debug and release unit-test variants.
5. `.gitignore` check: `git check-ignore` confirms `build`, `app/build`, `.gradle` and
   `local.properties` are all ignored. No change was needed.

**Files changed**
- `app/build.gradle.kts`: added the JUnit test dependency.
- `AI_LOG.md`: this entry, and the earlier entry relabelled as "Setup".

**Not committed (on purpose)**
- `.idea/gradle.xml`: Android Studio changed `gradleJvm` to `"jbr-21 (2)"`. That name only exists
  in this machine's JDK list, so committing it could break sync on other machines.
- `.idea/deploymentTargetSelector.xml` and `.idea/codeStyles/`: IDE settings changes, not related
  to the build.

**Problems / notes**
- `androidTest/ExampleInstrumentedTest.kt` probably has the same missing-dependency problem
  (`androidx.test.ext:junit` and espresso are in the catalog but are not declared). Neither
  `assembleDebug` nor `test` compiles it, so it was left alone. It would only matter for
  `connectedAndroidTest`.

---

## Task 2: Investigate "double-click jumps to the wrong page" (2026-11-01). No fix yet.

**Request**
- Trace the path from the double-tap handler to the ViewPager page change in both modes.
- Find the root cause. Consider position vs ID, the offset from the extra summary page, stale
  fragments, and the wrap-around swipe listener.
- Give reliable reproduction steps, plus steps where it happens to work. Do not change code.
  Leave the `.idea` changes alone.

**Method**
- Read the code, then tested each hypothesis on the running emulator (`emulator-5554`) with the
  debug APK from Task 1, using `adb` scripts kept in the session scratchpad, not the repo.
  The scripts seeded 4 categories (Fruit, Dairy, Bakery, Frozen) into the app's SharedPreferences,
  double-tapped rows, and read which page was showing from `uiautomator dump`.
- Emulator state afterwards: the app's saved state was put back to what it was before
  (`{"planDate":"2026-11-01","categories":[]}`), and the rotation setting was put back.
  `Shop-Example.xlsx` was copied to `/sdcard/Download/` for the import repro and left there.

**1. Trace (line numbers at commit 7e50c71)**

Plan mode (the overview is the FIRST page):
1. `PlanCategoriesFragment.kt:116-123` builds the list `[placeholder, cat0 … catN-1, placeholder]`,
   so category `i` is at list position `i+1`.
2. `PlanCategoriesFragment.kt:98-99`: the RecyclerView's `OnTouchListener` passes every event to a
   `GestureDetectorCompat` (`:81`).
3. `:82-84` `onDoubleTap` → `findChildViewUnder(e.x, e.y)` → `getChildAdapterPosition` = `position`.
4. `:87-90`: if the row is a heading, `index = position - 1` and `viewPager.currentItem = index + 1`
   (that is, `= position`).
5. `CategoryPagerAdapter.kt:18-23`: with `planMode = true`, page 0 is `PlanCategoriesFragment` and
   page `p` is `PlanCategoryFragment.newInstance(p - 1)`.
6. `PlanCategoryFragment.kt:26,85`: the fragment reads its index from arguments and shows
   `categories[index]`.

Shop mode (the summary is the LAST page):
1. `ShopCategoriesFragment.kt:65`: `CategorySummaryAdapter` gets the category list directly, so
   category `i` is at list position `i`.
2. `:57-58` OnTouchListener → GestureDetector (`:39`).
3. `:40-47` `onDoubleTap` converts `rawX/rawY` to RecyclerView coordinates → `findChildViewUnder` →
   `position`.
4. `:50` `viewPager.currentItem = position`.
5. `CategoryPagerAdapter.kt:25-30`: with `planMode = false`, page `p` is
   `CategoryFragment.newInstance(p)` for `p < size`, and page `size` is `ShopCategoriesFragment`.
6. `CategoryFragment.kt:22,48` shows `categories[index]`.

Mode switching and pager adapter lifetime (`MainActivity.kt`):
- `:91-93`: a Shop-mode adapter is created in `onCreate`.
- `:353-366` `showPlanning()` replaces it with a new Plan-mode adapter. `:323-336`
  `showStartPage()` replaces it with a new Shop-mode adapter, but only if `inPlanMode` is true.
- `:340` `showShopping()` never creates a new adapter. It reuses whichever adapter is already
  there.
- `:122-129`: the categories observer calls `notifyDataSetChanged()` only when the number of
  categories changes.
- `:96-121`: the wrap-around `OnTouchListener` on ViewPager2's internal RecyclerView.

**2. Root cause analysis**

| Hypothesis | Verdict | Evidence |
|---|---|---|
| Wrong offset for the summary page (+1 in Plan, none in Shop) | **Not the cause** | The mapping works out correctly in both modes. On device, double-tapping each of the 4 categories went to the right page in both modes. |
| Coordinates (Plan uses `e.x`, Shop uses `rawX` minus screen location) | **Not the cause** | Both work out to the same RecyclerView-local point, and all taps hit the right row. |
| Displayed order and ViewModel order drift apart when dragging (async `submitList` in `ItemAdapter.moveItem`) | **Not reproduced** | 9 drags at speeds from 150 to 3000 ms. The displayed order and saved order matched every time, and double-tap was correct afterwards. |
| Stale pages after reorder, add or delete *within Plan mode* | **Not the cause** | After a reorder, adding "Snacks" and deleting "Fruit", every double-tap was correct. Fragments look up `categories[index]` live, and Plan-mode page types never change position. |
| **A. Fragments keyed by position (`CategoryPagerAdapter` has no `getItemId`/`containsItem`)** | **ROOT CAUSE: confirmed** | See below. |
| **B. Wrap-around swipe listener uses a stale `touchStartX`** | **Real bug: confirmed** | Gives the wrong page on a swipe, not on a double-tap. See below. |

**A. Stale fragment of the wrong type (main cause).** `FragmentStateAdapter` identifies each
page's fragment by `getItemId(position)`, which defaults to the position itself. It reuses the
fragment it already has for an ID as long as `containsItem(id)` is true, and `createFragment()`
is *not* called again. In Shop mode, the type of page at a given position depends on the number
of categories: position `size` is the summary. So if the number of categories changes while the
same Shop-mode adapter is alive, the old `ShopCategoriesFragment` stays at the old summary
position, which is now a category position. The pager then shows the Summary page where category
`k` should be. Double-tapping row `k` "works" (`currentItem = k`) but lands on that stale summary.
- Only an import can change the number of categories in Shop mode
  (`MainActivity.kt:206/261-262`). That path goes through `showShopping()`, which doesn't build a
  new adapter.
- The same mechanism also shows up after the activity is recreated (rotation) while in Plan mode.
  ViewPager2 restores the saved Plan-mode fragments into the new Shop-mode adapter, and
  `inPlanMode` is reset to false. Confirmed: Shop's last page (position 4) became a
  `PlanCategoryFragment` for "Frozen" with Add, Edit and Delete controls, and the Summary page
  could not be reached.
- Why it's only "sometimes": the stale fragment lasts only while ViewPager2 keeps that page's view.
  Once the user moves about 3 or more pages away, the view holder is recycled, the fragment is
  destroyed, and `createFragment()` builds the correct one when the user comes back. Verified:
  after swiping far away and back, page 4 showed "5 Dairy & Ham" correctly.

**B. Wrap-around listener (`MainActivity.kt:96-121`).** `touchStartX` is only set when the
pager's RecyclerView receives `ACTION_DOWN` itself. But each page's `NestedScrollView` consumes
DOWN, so the pager's RecyclerView only sees the gesture after it intercepts a horizontal drag:
MOVE and UP, but no DOWN. So `touchStartX` is usually still `0f`, and on UP
`deltaX = event.x - 0 > 100` while `currentItem` is still the starting page.
- Shop: swiping forward from the first category jumps to the **Summary** page (confirmed).
- Plan: swiping forward from the Categories page jumps to the **last category** instead of the
  first (confirmed: went to "Bakery" instead of "Fruit").
- The intended wrap from the last page to the first almost never fires.
- To the user this also looks like "goes to the wrong page". For example, after double-tapping
  the first category, the next swipe forward throws them back to the summary.

**3. Reproduction steps**
- Reliable repro of A (import changes the number of categories) and step-by-step "works" cases:
  see the Task 2 summary in the chat and the list below.
  1. Start with a list of N categories (e.g. 4). Tap **SHOP**, then go to the **Summary** page
     (the last page, position N).
  2. Tap **Home**. Tap **IMPORT** and pick a file with more than N categories
     (`Shop-Example.xlsx` has 20).
  3. The app returns to Shop on position N. It shows **Summary**, but should show category N+1
     (with N=4, "5 Dairy & Ham").
  4. Double-tap row N+1 ("5 Dairy & Ham"). **Wrong:** it stays on the stale Summary.
  5. Double-tap any other row, e.g. "2 Fruit & Veg": correct. Swipe back to position N: it now
     shows "5 Dairy & Ham" correctly, because the stale page was recycled.
- Repro of A through rotation: in **PLAN**, swipe to the last category page. Rotate to landscape
  and back. Tap **SHOP**. The last page is a Plan-style editor instead of Summary.
- Repro of B: open **SHOP** (on the first category) and swipe forward once. It lands on Summary.
  In **PLAN** on the Categories page, swipe forward once. It lands on the last category.

**Files changed**
- `AI_LOG.md` only. No source code changed (investigation only, as requested).

**Problems**
- My `adb` test scripts made a few false starts (they read the wrong page or typed into the wrong
  screen). Those runs were discarded and redone; none of them affected the conclusions.
- **Correction (found in Task 2b):** "Not the cause" for the wrap-around listener above is wrong for
  double-taps. Every adb double-tap in this task used `input tap`, which lifts the finger instantly.
  With human-speed taps, the listener and the pager do interfere with double-tap navigation. See
  Task 2b.

---

## Task 2b: Write tests before fixing (2026-11-02). No fix yet.

**Request**
1. Set up on-device testing: add the `AndroidJUnitRunner`, `androidx.junit` and `espresso-core`
   (from the version catalog). Commit this on its own.
2. Write tests for the root cause (pages kept by position). A unit test on page IDs, pulling the
   logic into a small function if needed without changing behaviour. On-device tests: (a) import
   changes the count, then double-tap category 5; (b) rotate in Plan, then Shop's last page should
   be Summary; (c) controls that double-tap each category with an unchanged list.
3. Run them. The bug tests should fail and (c) should pass. Add TESTS.md. Leave the swipe bug alone.

**Actions**
1. `app/build.gradle.kts`: added `testInstrumentationRunner` and the two `androidTestImplementation`
   lines. `connectedDebugAndroidTest` ran `ExampleInstrumentedTest` and it passed. Committed on its own
   (`530a896`).
2. Added `PagerPage.kt`: `PagerPage.at(position, categoryCount, planMode)` (which page a position
   shows) and `PagerPage.idAt(...)` (its page ID, which currently returns the position, the same as
   FragmentStateAdapter's default). `CategoryPagerAdapter` now uses them in `createFragment` and
   `getItemId`. **No behaviour change:** the same fragments are created at the same positions, and
   the overridden `getItemId` returns the same value as the default, so the default `containsItem`
   still matches.
3. Added `PagerPageTest.kt` (6 unit tests) and `PagerNavigationTest.kt` (4 on-device tests). Test
   data is written into SharedPreferences before launch. Test (a) does what `importXlsx()` does
   after parsing: `viewModel.setCategories(...)`, then `showShopping()` via the SHOP button.
   Test (b) uses `ActivityScenario.recreate()`, which goes through the same lifecycle as rotation.
4. Added TESTS.md with the results.

**Problems**
- **Espresso 3.5.1 (the catalog version) can't run on this emulator (API 37).** Every Espresso test
  failed with `NoSuchMethodException: android.hardware.input.InputManager.getInstance`. Espresso
  looks this method up by reflection, and it was removed in Android 15. I tried 3.6.1 first and it
  failed the same way. **Bumped `espressoCore` to 3.7.0 in `gradle/libs.versions.toml`** (latest
  stable), which fixed it. This is a test-only dependency; the app is unchanged. Gradle, AGP and
  Kotlin were not touched. Committed separately.
- **The (c) controls fail, but they were expected to pass.** I investigated before writing them up:
  - First failure: Plan "Bakery → Dairy". This was partly a test artifact. Consecutive steps
    double-tapped within 300 ms of each other, and the rows are closer together than the double-tap
    distance limit, so `GestureDetector` paired taps across steps. Fixed in the test by waiting
    longer than the 300 ms double-tap timeout before each double-tap.
  - Still failing after that: Shop Fruit → "Summary" (3/3 runs), Shop Dairy → "Bakery" (1/3),
    Plan Frozen → "Dairy" (3/3).
  - Diagnosed with a temporary logging test (deleted, not committed). It logged touch events and
    page-change stack traces:
    - The double-tap handlers change page on the **second finger-down**, before the finger lifts.
      The jump moves the page under the finger away. That page's list gets `ACTION_CANCEL`, and the
      finger-up goes to ViewPager2's own RecyclerView.
    - **Shop → first category (confirmed with a stack trace):** `onPageSelected(0)` comes from
      `ShopCategoriesFragment.kt:50`, then `onPageSelected(4)` comes from **`MainActivity.kt:108`**,
      the wrap-around listener. Its `touchStartX` is still 0, so the finger-up looks like a 437 px
      swipe while `currentItem == 0`.
    - **Longer jumps land on a page on the way** (Plan Frozen → Dairy, Shop Dairy → Bakery). This
      happens consistently, but I didn't capture the exact step that stops the scroll. Two traced
      runs with different timing didn't reproduce it. Likely mechanism: the leftover finger-up
      interrupts ViewPager2's smooth scroll, and the pager snaps to the nearest page.
  - **Checked against real-speed input:** `adb input motionevent` double-taps with about 80 ms per
    tap, on the real app with 4 categories, 3 tries per row:
    - Shop: Fruit → Summary ×3, Dairy → Frozen ×3, Bakery → Frozen ×2, Frozen OK.
    - Plan: Fruit OK, Dairy OK, Bakery → Fruit/Dairy, Frozen → Dairy ×3.
    - Instant `adb input tap` taps (used in Task 2) went to the right page every time.
  - **Conclusion:** the reported "sometimes goes to the wrong page" has two causes.
    (1) Pages are remembered by position (Task 2, tests a and b).
    (2) Double-tap navigation happens while the finger is still down, so the finger-up is treated
    as a swipe by the wrap-around listener and the pager. (2) is likely what users hit most, since
    it doesn't need an import or rotation.
  - The tests were left to fail honestly (not changed to pass). Fixing (2) touches the wrap-around
    listener, which I was asked to leave alone, so that needs a decision from the user.
- Test runs uninstall the app from the emulator, so I reinstalled it afterwards (`installDebug`).
  Its saved data is now empty; before, it had no categories either. `Shop-Example.xlsx` is still in
  `/sdcard/Download/`.
- The "Java compiler version 21 has deprecated support for ... version 8" warning comes from the
  project's existing `sourceCompatibility = 1.8`, not from this task.

**Files changed**
- `app/build.gradle.kts`: test runner and androidTest dependencies.
- `gradle/libs.versions.toml`: `espressoCore` 3.5.1 → 3.7.0.
- `app/src/main/java/.../PagerPage.kt` (new) and `CategoryPagerAdapter.kt`: logic moved out, no
  behaviour change.
- `app/src/test/java/.../PagerPageTest.kt` (new), `app/src/androidTest/java/.../PagerNavigationTest.kt`
  (new).
- `TESTS.md` (new), `AI_LOG.md`.
- `.idea/*` was not touched or committed.

---

## Task 2c: Fix both causes (2026-11-02)

**Request**
1. Page IDs: give each page an ID based on what it shows (mode, and Overview vs which
   category), and override `containsItem` to match. Commit as
   "Task 2: fix stale pages by giving each page a stable ID".
2. Double-tap/swipe conflict: make the smallest change that stops a double-tap being treated
   as a swipe, and keep the wrap-around swipe working. Commit as
   "Task 2: stop double-tap being read as a swipe".
3. Rerun all tests, add an "After fix" column to TESTS.md, update the log, push.

**Fix 1: stable page IDs** (`fef1b1d`)
- `PagerPage.idAt` now encodes what the page shows: bit 0 is the mode (Plan), bit 1 marks the
  Overview page, and the remaining bits are the category index. A Summary, a category page and
  a Plan page therefore never share an ID.
- Added `PagerPage.containsId`, and `CategoryPagerAdapter.containsItem` uses it. When the
  number of categories changes, or when a Plan fragment is restored after rotation, the stale
  fragment's ID is no longer valid, so FragmentStateAdapter discards it and builds the right one.
- **Design choice:** category pages are identified by index, not by category ID. The category
  fragments are created with an index and look up `categories[index]` live. Keying them by
  category ID would reuse a fragment whose index is out of date after a reorder.
- Added unit test `containsId_matchesCurrentPagesOnly`.
- The 3 failing unit tests and test (b) now pass.

**Test (a) correction** (`cf21e3c`, committed before fix 1)
- Its precondition "a Summary is showing" (after the import) was asserting the bug itself, so it
  failed once fix 1 worked. Rewritten:
  1. Check that position 4 shows "Aisle 5" after the import.
  2. Go to the real Summary and double-tap "Aisle 5".
- Checked against the pre-fix code: the rewritten test still fails ("Summary" at position 4).

**New tests before fix 2** (`bb53395`, then `9737943`)
- (d) Human-speed double-taps on every category, both modes.
- (e) Wrap-around swipes, both modes: wrap only at the ends, and other swipes move one page.
- Before fix 2 (fix 1 applied):
  - (d) produced wrong pages in every run.
  - (e) failed: swiping forward on the last page did not wrap.
  - (a) failed: the 16-page jump to "Aisle 5" landed on "Aisle 6" or "Aisle 7".
- **Problem: the (d) input was unreliable at first.** Fixed in `9737943`.
  1. Sending each event as a shell command (`input motionevent`) was too slow. `GestureDetector`
     only counts a double-tap if the second finger-down comes within 300 ms of the *first*
     finger-down. My first attempt measured the wrong gap (first finger-up to second finger-down).
  2. Switched to `UiAutomation.injectInputEvent`. With `sync = true` it waits for the app after
     each event, which stretched the 80 ms holds to 100–230 ms. Temporary logging in the
     fragments (removed, not committed) showed a missed tap had 298 ms between finger-downs.
  3. Final version: `injectInputEvent(event, false)` with 80 ms holds and an 80 ms gap.
     Occasional "nothing happened" results remained, and they happen *both* before and after
     the fix, so they come from the emulator, not the code. In that case only, the helper
     retries once. A wrong page is never retried.

**Fix 2: stop double-tap being read as a swipe** (`728a18c`)
- `PlanCategoriesFragment` and `ShopCategoriesFragment`: changed `onDoubleTap` (fires on the
  second finger-down) to `onDoubleTapEvent`, acting only on `ACTION_UP`. The page now changes
  after the finger has lifted, so no part of the touch reaches the ViewPager afterwards. The
  rest of the handler is unchanged.
- `MainActivity`: the wrap-around listener changed from `setOnTouchListener` to
  `addOnItemTouchListener`.
  - **Why the switch:** the old listener almost never received `ACTION_DOWN` (the page
    underneath consumes it), so it measured swipes from a stale start position. An item touch
    listener's `onInterceptTouchEvent` sees every event of the gesture, including the DOWN. Just
    ignoring gestures without a DOWN, while keeping `setOnTouchListener`, would have switched
    the wrap feature off completely.
  - **Gestures it didn't see start are ignored:** `touchStartX` is now nullable, and it is
    cleared on UP and CANCEL.
  - **Only wraps at the ends:** the swipe must start and end on the end page
    (`touchStartPage`), so an ordinary swipe that ends on page 0 can't trigger a wrap.
  - **Posted page change:** returning `true` makes the RecyclerView stop scrolling, which would
    cancel a page change made inside the callback, so the change is posted.

**Results after both fixes** (details in TESTS.md)
- Unit tests: 8/8 pass.
- On-device: 9/9 pass in 3 out of 3 consecutive full runs. The human-speed tests (d) alone
  passed 8 out of 8 runs (64 double-taps) with no wrong page.
- Manual check with adb human-timed double-taps (the Task 2b repro): 24/24 correct, against
  14/24 wrong before. Swiping forward from the first page now goes to the next page in both
  modes.

**Problems / notes**
- `GestureDetectorCompat` is deprecated in the current AndroidX core. It was already used before
  this task; I left it alone.
- Emulator: test runs uninstall the app, so I reinstalled it and set its saved data back to the
  empty state from before (`{"planDate":"2026-11-01","categories":[]}`).

**Files changed**
- `PagerPage.kt`, `CategoryPagerAdapter.kt`, `PagerPageTest.kt` (fix 1).
- `MainActivity.kt`, `PlanCategoriesFragment.kt`, `ShopCategoriesFragment.kt` (fix 2).
- `PagerNavigationTest.kt` (test (a) correction, tests d/e, real-speed input helper).
- `TESTS.md`, `AI_LOG.md`.
