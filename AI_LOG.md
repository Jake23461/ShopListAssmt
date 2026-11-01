# AI Log

Running log of AI-assisted maintenance on ShopListGPT (Claude Code, model claude-opus-5-5).
Each entry records the request, the actions taken, files changed, and any problems.

---

## Task 1: Repoint remote, push history, architecture overview (2026-11-01)

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
