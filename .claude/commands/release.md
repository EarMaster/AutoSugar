---
description: Bump the app version (major/minor/patch or explicit semver), update CHANGELOG.md, and commit the release.
allowed-tools: Bash(git status:*), Bash(git pull:*), Bash(git diff:*), Bash(git add:*), Bash(git commit:*), Bash(git push:*), Read, Edit, Bash, AskUserQuestion, TodoWrite, TodoRead
model: haiku
---

You are preparing a new release for the AutoSugar Android app. Your job is to determine the new version number, update `app/build.gradle.kts`, restructure `CHANGELOG.md`, and commit the result.

## Input

The user may have provided an argument: `$ARGUMENTS`

Interpret `$ARGUMENTS` as follows:
- `major`, `minor`, or `patch` (case-insensitive) → bump that semver component
- A full semver string like `1.0.4` or `2.0.0` → use it exactly
- Empty / blank → analyse the changelog and suggest a bump type (see below)
- Anything else → it is unusual; flag it and ask for confirmation or correction

## Step 1 — Read current version

Read `app/build.gradle.kts` and extract:
- `versionName` (e.g. `"0.1.0"`)
- `versionCode` (integer)

## Step 2 — Determine target version

**If argument is `major` / `minor` / `patch`:**
Compute the new semver by incrementing the appropriate component (reset lower components to 0).

**If argument is a valid semver (`X.Y.Z` with all three numeric parts):**
Use it as-is. Warn (but don't block) if the new version is less than or equal to the current one.

**If argument is empty:**
Read the `## [Unreleased]` section of `CHANGELOG.md`. Based on the entries:
- Any `### Added` → at minimum a `minor` bump
- Only `### Fixed` or `### Changed` (no new features) → `patch`
- Breaking change indicated (rare) → `major`

Formulate a suggestion with a one-sentence rationale.

**If argument is unusual** (e.g. only two parts like `1.2`, has leading zeros, non-numeric, etc.):
Do not proceed. Ask the user to confirm or correct.

## Step 3 — Confirm with user

Before making any changes, use **AskUserQuestion** to confirm. Show:
- Current version
- Proposed new version
- New versionCode (current + 1)

Single question, two options: "Proceed" and "Cancel / change". If the user cancels or provides a correction, re-evaluate from Step 2 with their input, then confirm again.

## Step 4 — Apply changes

**4a. Update `app/build.gradle.kts`**

Replace the `versionCode` and `versionName` lines with the new values. Use Edit — do not rewrite the whole file.

**4b. Update `CHANGELOG.md`**

Get today's date via: `date +%Y-%m-%d`

Replace the line `## [Unreleased]` (at the top of the Unreleased section) with:

```
## [Unreleased]

## [X.Y.Z] - YYYY-MM-DD
```

This preserves an empty Unreleased section for future work and stamps the release with today's date.

## Step 5 — Commit

Run:
```
git add app/build.gradle.kts CHANGELOG.md
git commit -m "chore(release): bump version to X.Y.Z"
```

Do **not** push. Tell the user the commit is ready and they can push / open a PR when ready.
