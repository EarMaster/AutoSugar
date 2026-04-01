---
description: Commit recent changes using a conventional commit message, after syncing with the remote.
allowed-tools: Bash(git status:*), Bash(git pull:*), Bash(git merge:*), Bash(git diff:*), Bash(git add:*), Bash(git commit:*), Bash(git push:*), AskUserQuestion, Edit, Write
model: haiku
---
Commit recent changes using a conventional commit message, after syncing with the remote.

## Steps

1. **Check git status** — run `git status` to see staged, unstaged, and untracked files. If there is nothing staged, stage recent changes. If there are unstaged or changed files from previous sessions group them (if possible) and use `AskUserQuestion` to ask the user if these changes shall be commited as well.

2. **Sync** — run `git pull --rebase` to incorporate upstream changes before committing. If it fails (e.g. conflicts), stop and report the error; do not proceed.

3. **Analyze the diff** — run `git diff --cached` to understand what is staged.

4. **Draft a conventional commit message** following this format:
   ```
   <type>(<optional scope>): <short description>

   <optional body — explain WHY, not WHAT>
   ```
   - **type**: `feat` | `fix` | `docs` | `style` | `refactor` | `test` | `chore` | `perf` | `ci` | `build` | `revert`
   - **scope**: affected module/component (omit if unclear or cross-cutting)
   - **short description**: imperative mood, lowercase, no trailing period, ≤ 72 chars
   - **body**: wrap at 72 chars; omit if the subject line is self-explanatory
   - Add `BREAKING CHANGE: <description>` in the footer if applicable

5. **Update CHANGELOG.md** — prepend an entry for this commit under the `[Unreleased]` section (create the file and section if absent). Format:
   ```
   ## [Unreleased]

   ### <Category>
   - <human-readable summary of the change>
   ```
   Categories: `Added` | `Changed` | `Fixed` | `Removed` | `Security` | `Performance`. Use one category block per entry; add multiple bullets if needed. Stage `CHANGELOG.md` with `git add CHANGELOG.md` before committing.

6. **Confirm** — use `AskUserQuestion` with two questions in a single call:
   1. Show the drafted commit message and CHANGELOG entry; ask the user to approve or request changes.
   2. Ask whether to push after committing.

7. **Commit** — once confirmed, run `git commit -m "<message>"`.

8. **Merge** — run `git merge --no-edit origin/main` to pull in any commits that landed on `main` (e.g. PR merge commits) so this branch stays up-to-date with the base branch; skip this if already on `main`. If it fails, stop and report the error.

9. **Push** — if the user chose to push, run `git push`.
