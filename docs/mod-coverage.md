# Irium Mod Coverage — Method & Audit (M7-X2)

**Goal:** any Fabric 26.x mod should install, activate, and be usable through the
streaming agent. This document defines the verification method and records the
current state of coverage.

## 1. Method — three layers of verification

### Layer 1: manifest parsing audit (`harness/coverage-audit.py`)

Corpus: 60 popular Fabric 26.2 mods (Modrinth top downloads across 15
categories). For each jar, the tool checks:

- every `fabric.mod.json` field actually parsed vs. the full field set
- entrypoint keys used in the wild (client/main/server/modmenu/preLaunch/
  `sodium:config_api_user`/`jei_mod_plugin`/`rei_client`/…)
- mixin configs (package, plugin class, compatibility level, counts)
- the `net.fabricmc.*` class surface consumed by the mod's constant pools

Run: `python3 harness/coverage-audit.py`

### Layer 2: API surface verification (`harness/api-surface-check.py`)

Every `net/fabricmc/**` method reference in a mod (extracted via `javap -c`
from the constant pools) is checked against the agent's compiled stubs:
class present, method present, descriptor exact. Any mismatch is a
**guaranteed `NoSuchMethodError` at runtime** — caught before runtime.

Run: `python3 harness/api-surface-check.py agent/target/irium-agent-X.jar mod1.jar mod2.jar`

### Layer 3: runtime self-test (in-agent, `-Dirium.test.modsscreen=true`)

The agent itself opens the Mods screen through the exact user-click path
(`PauseScreen → ModsScreen.init → ModListWidget.filter → setSelected →
rebuildUI`) and closes it. Any crash in that path fails the bot run. This
caught both real Mod Menu crashes (`getContact`, `Person.getContact`) that
static analysis alone missed.

## 2. Parsing rules (learned from the corpus — enforced in `parseFmj`)

The parser must never throw on spec-legal input. Verified failure modes of a
strict GSON binding (each one silently **rejected the whole mod**):

| fmj form | Legal? | Strict GSON | `parseFmj` |
|---|---|---|---|
| `entrypoints.client: "com.x.Init"` (string shorthand) | yes | throws | accepted |
| `entrypoints.client: [{"adapter":"kotlin","value":"..."}]` | yes (all Kotlin mods) | throws | `value` extracted |
| `mixins: {"client": [...]}` (Quilt ext) | yes | throws | flattened |
| `authors: [{"name":..., "contact":{...}}]` | yes | objects dropped | Person with contact |
| raw control chars inside strings | ships in real mods | lenient by default | lenient |
| `version: 2` (number) | tolerated | coerced | coerced |

Non-goals (not needed at runtime): `schemaVersion`, `environment`, `provides`,
`recommends`, `suggests`, `conflicts`, `breaks`, `contributors`.

## 3. Current state (2026-08-21, agent 0.6.26)

- **Streaming mods (SVC, Xaero, Mod Menu): 100% surface coverage** — 0
  mismatches on layers 1–2, layer 3 green (self-test opens/closes Mods screen,
  no crash, descriptions/authors/licenses/contacts/links render).
- **Corpus gaps** (for the 94–99% target), by frequency:
  - `accessWidener` — 34/60 mods ship one; we don't apply it yet. **Biggest
    single blocker** for sodium/lithium/iris-class mods.
  - `transfer/v1` (fluid/item variants, Storage) — JEI, FarmersDelight-class
  - `renderer/v1 mesh` (QuadEmitter family) — sodium/continuity-class
  - `datagen/v1` — dev-time only, ignorable at runtime
  - event/screen/keymapping client APIs — incremental
- JiJ (`jars`) — 28/60 mods embed nested jars; handled since 0.6.23.
- Entrypoint keys beyond client/main/server are already dispatched
  generically (any key works via `getEntrypoints`); known consumers:
  modmenu (31 mods), jei_mod_plugin, rei_*, sodium:config_api_user.

## 4. Next steps (priority order)

1. **Access widener support** — parse `accessWidener` field, apply
   class-file transformations at materialization time (open up
   private/final fields the mods' mixins expect).
2. `transfer/v1` surface (Storage, variants, transactions) for JEI-class.
3. `renderer/v1` mesh surface for sodium-class.
4. Extend the runtime self-test: click through dependencies view, search
   filter, mod config button (`modmenu` entrypoint) in ModsScreen.
