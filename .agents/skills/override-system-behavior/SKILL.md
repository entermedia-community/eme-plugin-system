---
name: override-system-behavior
description: Use this skill when the user wants to change core framework behavior (page loading, request handling, search/data layer, layout macros) — requests like "override X in system", "change how the engine does Y", or "customize a core class without touching eme-lib". Covers the fallback-override pattern instead of editing plugins/system in place, since direct edits there affect every site and every plugin. Consult this before editing anything under plugins/system/code or plugins/system/html/configuration.
---

# Override System (Core Framework) Behavior

`plugins/system` is shared by every plugin and every site. Prefer overriding over editing in
place.

## Step 1: Confirm it actually needs to be a system-level change

Most "customize the app" requests belong in `catalog` (data/schema), `finder` (Java app logic),
`community`/`profile`/`openedit` (UI), not `system`. Only proceed here if the behavior genuinely
must apply to the shared engine/pipeline itself (page loading, module wiring, search plumbing,
layout macros used by every plugin).

## Step 2: Override via the Website fallback, not an in-place edit

Fallback order: `Website/plugins/system/*` is used before `EME-LIB/plugins/system/*` for
same-named files. To customize:

- Copy the specific file you need to change (an `.html`, `.xconf`, `velocitymacros.vm` fragment,
  or a Java class you're overriding) into `Website/plugins/system/<same relative path>`.
- Edit the copy there. The original in `EME-LIB/plugins/system` stays untouched, so upgrades to
  eme-lib don't clobber the site-specific change.

Only edit `plugins/system` directly (in this repo) when the change is meant to ship as part of the
shared framework itself, not a single site's customization.

## Step 3: If new Java behavior is unavoidable

- Add the class under `code/org/...` following the existing package layout
  (`org.openedit.*` for framework-generic code, `org.entermediadb.*` for EME-specific extensions).
- Wire it as a bean in `html/src/plugin.xml`, matching the style of existing singleton beans like
  `OpenEditEngine`.
- Expect this to require a full rebuild and server restart — these beans are not hot-reloaded.

## Step 4: Validate broadly

1. Rebuild and restart.
2. Confirm the server boots cleanly and the elastic index initializes without mapping errors.
3. Smoke-test at least one page in each major plugin (finder, community, profile) — a regression
   here tends to be site-wide, not localized to one feature.
