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
