# AGENTS.md

## System Plugin Guide

### Purpose

`plugins/system` is the core EME/OpenEdit framework: the servlet engine, module/bean manager,
page loader, search/data layer, and elastic index bootstrap that every other plugin runs on top
of. It is the most foundational and highest-blast-radius plugin — most customization should
happen in a higher-level plugin (`catalog`, `finder`, `community`, ...) or by overriding, not by
editing `system` directly.

### Folder Map

- `code/org/openedit/` The framework itself: `servlet/` (engine, request handling), `page/`
  (page loading/config), `data/` (Searcher, PropertyDetail, data iteration), `hittracker/`
  (search result handling), `modules/` (module/bean base classes), `users/` (auth/permissions),
  `event/` (WebEventListener/EventManager), `util/`, `config/`, `generators/`
- `code/org/entermediadb/` A thin layer of EME-specific extensions (asset/location helpers) on
  top of the openedit framework
- `html/src/plugin.xml` Core Spring bean wiring — `OpenEditEngine`, `moduleManager`, `pageManager`,
  and the other singletons everything else depends on
- `html/configuration/` `beans.xml`, `elastic.xml` / `elasticindex.yaml` (Elasticsearch mapping and
  bootstrap), `lucene.xml`, `basenode.xml`
- `html/data/fields` `/lists` `/mapping` `/views` Core system tables: `user`, `view`, `mount`,
  `siteparameters`, `lock`, `email`, `plan`, `reindexLog`, `transactionLog`, ... — infrastructure
  tables, not application content
- `html/display/` Shared layout macros/fragments used across the whole fallback chain
  (`velocitymacros.vm`, inner-layouts, white-area fragments)
- `html/events/` Core lifecycle event scripts (asset, scripts, snapshot)
- `html/elasticplugins/` Bundled Elasticsearch plugin binaries

### What This Plugin Owns

- The request/response pipeline, module wiring, and page/config loading used by every plugin
- The Elasticsearch/Lucene index bootstrap and low-level search plumbing
- Core infrastructure tables (users, permissions, mounts, site parameters, locks)
- Shared layout macros used by layouts across all plugins

### Editing Rules

- Prefer the fallback override pattern over editing `system` in place: put a same-named file under
  `Website/plugins/system/...` (or the equivalent plugin) to override behavior for one site without
  touching the shared framework.
- Java changes here affect every plugin — extend/override a class rather than changing shared
  behavior in a non-backwards-compatible way, and expect a full rebuild + restart.
- New infrastructure tables (not application data) go in `html/data/fields`/`lists` here; anything
  that's really application/content data belongs in `catalog` instead — see the
  `catalog-table-creator` skill for the actual table-creation steps.
- Changes to `html/configuration/elastic.xml` / `elasticindex.yaml` affect the search index schema
  for the whole system — treat as a migration, not a routine edit.

### Validation Checklist

1. Rebuild Java classes after any `code/org` or `plugin.xml` change; restart the server (core
   singletons like `OpenEditEngine`/`moduleManager` are not hot-reloaded).
2. Clear the page cache (or restart) after any `.xconf`/`html/data`/`html/configuration` change.
3. Confirm the server boots cleanly and the elastic index initializes without mapping errors.
4. Smoke-test a page from another plugin (e.g. finder) end to end — a system-level regression
   tends to break everything, not just one screen.

### Notes For Agents

- Treat any request to "add a field/table" for application content as a `catalog` change, not a
  `system` change — `system`'s tables are infrastructure (users, mounts, locks), not content.
- If unsure whether a change belongs here or in a feature plugin, prefer the feature plugin and
  only touch `system` when the behavior genuinely must be shared by all plugins.
