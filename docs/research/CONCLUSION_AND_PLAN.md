# RESEARCH CONCLUSION & CONSTRUCTION PLAN — SDM Project

**Closing document.** Research is finished (verdict: CONDITIONAL GO, sdm3 validation). This document concludes then transforms: working methodology, roadmaps, features, and concrete application to CocoWorld.

---

## 1. Research conclusion — the one-page summary

## 1.1 What was demonstrated (3 documents, 1 laboratory)

| Report | What it establishes |
|---|---|
| sdm/ (44 p.) | The vanilla ceiling (L0 = 85%), the wall of the 5 families, the LinkAgent, SDMP, security, roadmap |
| sdm2/ (22 p.) | The UX breakthrough: 7 zero-click locks, `JDK_JAVA_OPTIONS`, hot attach, ghost version, V0-V3 architecture |
| sdm3/ (23 p.) | Falsification: real JDK 25 lab (env-var → premain → transformation; attach → live retransform), H1-H14 matrix, CONDITIONAL GO |

## 1.2 The final statement of the breakthrough

> **SDM moves modding's installation from the player to the platform.** The server becomes the source of everything (content, code, versions, signatures); the client becomes a universal terminal (pure vanilla = 85%; + 1 Store click once in a lifetime = ~98%). The only remaining failure mode is execution (stupid code, sloppy security) — never theory.

## 1.3 The session sandbox principle (answer to the keybinds/HUD question)

**Claim: everything a server adds to the client disappears when leaving the server.**

Architecture guaranteeing it:

```text
SESSION LAYER (scoped to the connection, cleared on disconnect)
  ├── active modules (HUD, entities, rendering)
  ├── virtual keybinds (dynamic input layer, NEVER the vanilla registry)
  ├── server resource packs (popped on disconnect)
  ├── settings overrides (FOV, gamma… snapshotted → restored)
  └── SDMP state (cookies, manifest, active permissions)

AGENT LAYER (permanent, generic, dormant)
  ├── generic hooks installed (input layer, Gui.render, event bus)
  └── without an active session: inert hooks → 100% vanilla behavior
```

Verifiable consequences:
1. **Keybinds**: the vanilla options screen shows ONLY vanilla, always. SDM binds (configured via the SDM screen) exist only during connection to the server requesting them.
2. **HUD**: the injection is a generic hook; on disconnect it iterates an empty list → no pixel drawn.
3. **Mid-session crash**: at next boot, no handshake → no module → clean vanilla. The worst case never leaves dirty state.
4. **Documented limit**: loaded classes remain in RAM (JVM limit) but behaviorally neutral — and the "single generic hook + everything else data-driven at runtime" pattern minimizes even this residue.

**Architecture rule #1 of the project: no module mutates vanilla state directly. Everything goes through the runtime's session layer.** This is what makes the promise "leaving the server = as if never there" true by construction rather than by cleanup.

---

## 2. Working methodology to carry the project through

## 2.1 The doctrine: "the only possible failure is execution"

All risk is concentrated in code quality and trust. The methodology is therefore built around two axes: **never write stupid code**, and **be beyond reproach**.

## 2.2 Engineering — the non-negotiable disciplines

1. **One repo, CI from day 1.** Build + automatic tests on every commit; the pipeline reproduces the lab (E1/E2) on a JVM matrix (8/17/21/25) so the mechanisms never silently regress.
2. **The lab as non-regression test.** Experiments E1/E2 become executable JUnit tests: if `env-var → premain → transform` breaks on a future JDK, CI screams before any player.
3. **TDD on critical points.** Signature verification, manifest validation, path quoting (the lab trap), cache handling: written test-first. These are the functions where a bug = existential loss of trust.
4. **The "single generic hook" pattern.** Each vanilla class is transformed only ONCE, by a stable generic hook; everything specific lives in data/runtime. This reduces the per-version recipe surface by ~95% and makes multi-version maintenance tractable.
5. **Versioned recipes + anchor hash + mandatory L0 fallback.** A recipe without a clean degradation path is never merged (review rule).
6. **Self peer review: the "worst enemy audit".** Every PR describes what an attacker could do with it; merge requires the answer.
7. **Everything open-source from day one** (agent + service + protocol). Transparency is a security feature and the best marketing argument.
8. **Never a deadline on security.** Phase 4 (signatures, permissions, CRL) slips if needed — a security incident kills the project, a slip kills nothing.

## 2.3 Process — iteration in 3 loops

```text
Daily loop (1 dev)    : code → CI → quick manual test (local dev server)
Weekly loop (gateway) : playable demo on the dev Canvas server,
                        logged video, personal retro
Per-phase loop        : roadmap success criteria = gate;
                        no next phase without proof (logs/video)
```

## 2.4 Risk management during construction

| Risk | Early warning signal | Prepared response |
|---|---|---|
| JEP 451 (opt-in attach) becomes default | JDK release notes | switch to env-var premain (already the main rail); nothing breaks |
| Store review refuses | submission rejection | EV-signed exe + third-party launcher ghost version (channels already spec'd) |
| Recipe breaks on MC snapshot | anchor hash mismatch in CI | automatic L0 degradation, recipe re-anchored (dedicated time budget) |
| Malicious module attempted | abnormal telemetry | CRL + key revocation + public post-mortem |
| Mojang reacts | mail/DMCA | prepared position: identical to Fabric, zero assets, opt-out; open the dialogue |

---

## 3. MVP roadmap then complete

## 3.1 MVP — "the killing demo" (6 weeks, 1 dev)

Goal: prove the complete chain on real Minecraft, in public, filmable.

| Wk | Milestone | Deliverable | Success criterion |
|---|---|---|---|
| S1 | **D1 — Consent** | Canvas plugin: "★ Enable the experience" dialog + `sdm:hello` login query + memory cookie | vanilla player sees the dialog, Yes/No remembered |
| S2 | **D2 — Magic in session** | Real agent on MC: `Gui.render` recipe → "SDM OK" HUD appears in live session (env-var + attach) | video: HUD appears without restart, 0 crash |
| S3 | **D3 — Bidirectional** | Clickable HUD button → C→S event → server command executed; + breaking tests (wrong hash, missing module) | return path proven, clean degradations |
| S4 | **D4 — A real module** | "Custom boss bar" module: CocoWorld-styled health bar + phase announcements (replaces vanilla bossbar) | one visible, useful, desirable feature |
| S5 | **D5 — Voice** | Voicechat module: mic/opus/tunneled transport | speaking without installing anything |
| S6 | **D6 — Session sandbox** | Virtual keybind + HUD + settings snapshot/restored on disconnect | leaving the server = verified vanilla return |

**The MVP succeeds if the S2-S5 video exists and no crash occurred.** That is the launch material (announcement, Discord, first pilot servers).

## 3.2 Complete roadmap (after MVP)

| Phase | Est. duration | Objective | Exit |
|---|---|---|---|
| P1 Runtime v1 | 4-6 wks | prod agent: premain+attach+cache+verify+session layer | closed beta agent |
| P2 Complete SDMP | 3 wks | hello/manifest/events/delta-sync/rollback | frozen v1 protocol |
| P3 Modules | 4 wks | classloaders, lifecycle, hot-load/unload, isolation | 10 load/unload without leak |
| P4 Security | 4 wks | Ed25519, permissions, per-key consent, CRL, internal audit | published THREAT-MODEL |
| P5 Server API | 4 wks | ModuleHost, EventBridge, PackStudio | 3 demo features |
| P6 Client caps | 6 wks | HUD framework, keybinds, custom entities, rendering, prod voice | client module SDK |
| P7 DevKit | 4 wks | ServerModAPI + templates + docs + dev hot-reload | an external dev writes a module alone |
| P8 Store + distribution | 3 wks | MSIX app, EV exe, published ghost, site | public 1-click installation |
| P9 Compat | 6 wks | Fabric server-side adapter, asset extraction | 1 real mod loaded server-side |
| P10 Production | ongoing | open beta, 3-10 pilot servers, telemetry, support | 100 players, 1 week, 0 incident |

Total MVP→P8: ~6 months at 1 dev; ~3 months at 2 devs.

---

## 4. Features and small extras not mentioned until now

## 4.1 Platform capabilities (the "super-powers")

1. **Interactive cinematics** — scripted camera + letterbox + bullet-time (L0) + custom overlays (L3): update intros, cinematic boss deaths.
2. **Adaptive music director** — audio layers mixed by situation (combat/exploration/events), pushed by the server in packs.
3. **Integrated photo mode** — freeze + step + streamed GLSL filters: shared screenshot = organic marketing.
4. **Killcam / replay theater** — the server keeps the last N seconds of entity positions; free-camera replay after a boss kill.
5. **Server-driven accessibility** — colorblind palettes in packs, UI scales per module, custom subtitles of sound events.
6. **Per-player content A/B testing** — two variants of a module (pricing, difficulty, UI) served to two cohorts; server-side metrics.
7. **System emotes** — shareable animations via display entities (L0) + custom bones (L3).
8. **Custom nameplates** — rank badges, seasonal titles, role icons rendered by module.
9. **Custom death/connection screens** — server branding down to the last screen.
10. **Economy widgets** — HUD wallet, animated transaction history, 3D item previews (ItemBody).
11. **Living queue** — real-time position, ETA, mini-game during the queue (CocoQueue+).
12. **Streamer overlays** — the module exposes OBS-friendly widgets (separate from the player HUD): the server becomes stream-ready.
13. **Family LAN cache** — two players behind the same box share the module cache (downloaded once).
14. **Enriched staff spectate mode** — inspection tools (inventories, histories) as overlays without changing the client.
15. **Scheduled world events** — seasons, world bosses, traveling markets: timestamped activation/deactivation, hot-load on the hour.

## 4.2 Developer tools (the DevKit)

16. **Hot-reload modules in dev** — edit → re-push → tested in 5 s without relaunching the client.
17. **In-game inspector** — SDM F3-like: module state, passing events, errors, as an overlay.
18. **Live shader editor** — tweak GLSL + re-push PostEffects without reconnecting.
19. **Bug replay** — the agent client logs events; the dev replays the reporting player's session.
20. **CLI scaffold** — `sdm new module` → template compiled to module+pack+recipes.

## 4.3 Small extras — "the detail that sells"

21. Custom welcome toast on the first equipped join ("Full experience enabled").
22. Soft migration: a vanilla player sees in L0 what equipped players see in L3 → natural teasing.
23. Public "equipped players" counter on the site (social proof).
24. Guaranteed clean uninstall (CI-tested) + a "what the agent does" page in parent language.

---

## 5. The breakthrough applied to CocoWorld — conversion concepts

Principle: **plugins remain the brain (logic, truth, persistence); modules become the skin (rendering, HUD, feedback)**. Communication via EventBridge (`sdm.event.*`). Here is the conversion concept for each CocoWorld pillar.

## 5.1 Economy & Baltop (CocoEssentials)

- **Stays plugin**: balances, transactions, Baltop data, interest.
- **Module**: animated wallet widget (gain = floating +X), 3D Baltop screen with rotating items, custom transaction sounds, fortune charts in an enriched L3 dialog.
- **Dream modding**: 3D coin effects on rare trades, market prices displayed as a hologram at the hub.

## 5.2 Ranks (LuckPerms chain)

- **Stays plugin**: permissions, promotion chains.
- **Module**: per-rank nameplates (animated icons), prestige auras (particle fireworks on promotion), cinematic rank progression screen, rank-linked cosmetics served selectively (per-player modules).
- **Dream**: the rank VISUALLY visible everywhere — custom chat, styled tab list, unlocked emotes.

## 5.3 Bosses (Guardian Heart 90%, King 0.2%)

- **Stays plugin**: phases, damage, loot tables (Guardian Heart, King).
- **Module**: custom boss bar (CocoWorld art, not the vanilla bar), ground attack telegraphs, fullscreen phase announcements, **killcam on death**, cinematic loot reveal (the item falls in 3D, light, sound).
- **Dream**: the King boss as a server event — announced to all, participant HUD, 0.2% drop celebrated server-wide in overlay.

## 5.4 CocoCrops & CocoFishing

- **Stays plugin**: growth, harvests, series, fishing stats.
- **Module**: animated growth (L0 display interpolation → true 3D plants L3), fishing minigame (custom tension gauge, dedicated screen), rare fish as 3D models with cinematic introduction on catch.
- **Dream**: weather visually influencing crops (rain = visibly accelerated growth in particles).

## 5.5 War & civilizations (designs in progress)

- **Stays plugin**: territories, scores, villager AI (server).
- **Module**: HUD territory map (custom minimap), region selection walls in particles (StellarProtect+), animated banners, siege cinematics, visualized villager schedules (L3).
- **Dream**: the "war room" — real-time command screen for leaders.

## 5.6 Nether / Kronn (corrupted world)

- **Stays plugin/datapack**: worldgen, structure, progression.
- **Module**: **progressive corruption shader** (PostEffects driven by player progression — the deeper they go, the more the screen corrupts), distress audio layers, hallucinations (ephemeral fake entities), heartbeat HUD near Kronn.
- **Dream**: corruption as a sensory experience, not a biome.

## 5.7 Lore: Mack & Gorau auras

- **Module**: continuous aura rendering (GPU particles, not server particles = zero lag), Gorau's white aura x1000 as a visual event, legendary weapon effects.
- **Dream**: lore moments (Gorau's death, era 315) replayable as cinematic "chronicles" in the museum Cradle.

## 5.8 Collections & rewards

- **Module**: personal collection museum (3D room, items in rotating display), custom success toasts (not vanilla advancements), seasonal progression (pass) with visual rewards.

## 5.9 Queue & hub (CocoQueue, Velocity)

- **Module**: queue widget with ETA + mini-game, custom server transfer screen (instead of the void), visual coherence hub → worlds.

## 5.10 StellarProtect

- **Module**: region visualization (particle walls at claims), trust indicators, staff inspection mode.

## 5.11 Conversion prioritization (impact/effort)

| Wave | Modules | Why |
|---|---|---|
| 1 (MVP+) | Custom boss bar, wallet widget, toasts | small, very visible, prove the style |
| 2 | Lore auras, Nether shader, rank nameplates | the visual CocoWorld identity |
| 3 | Fishing minigame, collections museum, war map | deep gameplay |
| 4 | Killcam, war room, chronicles | the marketing "wow" |

---

## 6. What comes after the breakthrough (vision)

1. **Short term**: CocoWorld = showcase server ("the first server where modding installs itself") → native marketing content.
2. **Medium term**: 3-10 FR pilot servers → the platform becomes a product (signup, signed module registry, owner dashboard).
3. **Long term**: the distribution standard — creators publish SDM modules like they publish plugins today; the registry becomes the "Modrinth of server-driven"; the infrastructure position defends itself through trust and tooling, not a walled garden.

**The research exit thesis, one last time: the revolution is not technological — the technology is proven. The revolution is removing the friction between wanting to play and playing. Five years of research answered it: now, we build.**

```text
═══════════════════════════════════════════════
  RESEARCH : FINISHED (CONDITIONAL GO)
  NEXT STONE : D1 — 1 week of code
  TARGET : MVP 6 weeks → first public video
═══════════════════════════════════════════════
```
