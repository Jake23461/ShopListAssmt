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
