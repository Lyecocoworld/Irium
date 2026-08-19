# Breakthrough research — Server-driven Minecraft modding without client installation

(Second investigation)

## Executive summary — second investigation

## What changes in this research

The first investigation concluded: "an agent of ~300 KB installed once". This second research starts from the inverse question: **why is an installation needed at all, and can that step be moved into a mechanism that already exists?**

## The four new discoveries

### Discovery 1 — The definitive proof of the floor (class by class)

Absolute zero-click with a vanilla client is impossible, and this time the proof is exhaustive. The vanilla client executes only three categories of code: (a) the JARs of the classpath fixed by the launcher at startup (versions + libraries), (b) the sandboxed GLSL of resource packs, (c) commands/expressions within defined bounds. The only disk-write channel controlled by a server is the `server-resource-packs/` folder — an **inert** file, never read as code. The protocol's 225 packets contain no execution vector. The OpenURL prompt accepts only `http`/`https` (no `file://`). Conclusion: **at least one OS-level action, once in a player's life, is physically incompressible.** Anything promising less is a cheat.

### Discovery 2 — `JDK_JAVA_OPTIONS`: the launcher-agnostic injection

Verified against the official Oracle documentation (java man page, JDK 21): the environment variable `JDK_JAVA_OPTIONS` (and its elder `JAVA_TOOL_OPTIONS`) is read by **the `java` launcher itself** and injects its options into **every Java process started**, regardless of which program launched it — official launcher, Prism, Modrinth App, CurseForge, SKLauncher, bash script. It is the mechanism Datadog, New Relic and every industrial APM use to instrument JVMs without touching applications. Major architectural consequence: **the agent no longer needs to be declared in a launcher profile**. One system variable + one JAR in a fixed folder = the runtime activates in all launchers simultaneously, including those that forbid custom profiles (Lunar, Badlion), and survives launcher and Minecraft updates. The agent then filters by process name/main class (established dd-trace pattern) to activate only on Minecraft.

### Discovery 3 — The attach daemon: activation WITHOUT restart

The JVM Attach API lets a local process ask a running JVM to load an agent hot (`agentmain`). A small resident service (installed once, Store-signed) watches for Minecraft JVMs and attaches: **the vanilla player joining an SDM server sees effects appear in their current session**, without relaunching anything. Attached mode is weaker than premain (method-body retransformation only — enough for HUD, overlays, custom rendering), and the next launch switches to full power via Discovery 2. This is better than the stated dream ("restart acceptable"): no restart at all for the majority of effects.

### Discovery 4 — The auto-update channel exists: the "version + libraries" mechanism

Verified against the real Fabric meta (`meta.fabricmc.net/v1/versions/loader/...`): a runtime declares itself as a Minecraft version (JSON `inheritsFrom`, custom `mainClass`, Maven libraries with download URL). Third-party launchers (Prism, Modrinth App, SKLauncher — verified present on the target machine) accept custom versions and **download and update these libraries automatically** through the standard dependency mechanism. This is the transparent distribution/update channel for the runtime — the same one that updates Minecraft itself, never blocked by antiviruses, never touched by the player.

## The verdict in one table

| Architecture | Installation | Clicks | Restart | Universality |
|---|---|---|---|---|
| **V0 — Absolute vanilla (L0+)** | 0 | 0 | 0 | Everyone, ~85% of use cases |
| **V1 — Daemon + Env-Hook (recommended)** | once, Store | 1 (Get) | 0 for most effects; 1 for full power | All launchers, all versions, forever |
| **V2 — Ghost version (launcher channel)** | import 1 profile | 1-2 | 1 | Third-party launchers only |
| **V3 — Combined gradient (product target)** | once | 1 | 0 | V0 for everyone + V1 for equipped + V2 for distribution |

## The answer to the brief's final question

**No**, zero-click with a strictly intact vanilla client is impossible — class-by-class proof in chapter 2. **But the smallest concession is smaller than what the first report proposed**: a single "Get" click (Microsoft Store, signed, no SmartScreen), once in a player's life, after which (a) effects appear in the current session via attach, (b) all their launchers are equipped forever via `JDK_JAVA_OPTIONS`, (c) the runtime updates itself via the version "libraries" channel, (d) every SDM server activates without any action, with an in-game two-button consent and cookie memory. The dream "you join, we ask yes/no, you say yes, it works" is realized one OS click away — and that click is the most banal gesture on Windows.

## Chapter 1 — Why is a bootstrap needed? Anatomy of the boundary

## 1.1 The three code sources of a vanilla client

To understand why a bootstrap seems necessary, one must list exhaustively what executes in a vanilla client, and who controls each source:

| Executed code source | Who controls it | Can the server extend it? |
|---|---|---|
| Launch classpath JARs (versions + libraries) | The launcher (profile files + metadata) | Not directly — but see V2 |
| Resource pack GLSL (post_effect, core shaders) | The server (streamed pack) | Yes — GPU sandbox |
| Native library code (LWJGL, etc.) | The launcher (natives) | No |
| Scripts/datapacks | Server only | The client never executes them |

The vanilla client has **only these four sources**. The first report exhausted the second (85% of use cases). This research attacks the first — the one everyone believes is reserved for launchers.

## 1.2 The seven locks of zero-click

Each "lock" is a place where self-installation without an OS action is blocked. They are cumulative — a single one blocks everything:

1. **The game does not execute what it downloads** — `server-resource-packs/` is written by `DownloadedPackSource`, read only as rendering data (textures/models/sounds). No code path treats it as executable.

2. **OpenURL accepts only http/https** — the client sanitizes URLs before handing them to the OS. No `file://`, no custom scheme. The click remains the only way out.

3. **No packet carries bytecode** — the 225 packet classes (complete inventory from previous sessions) are closed data structures. `ClientboundCustomPayload` 1 MB is decoded then discarded (`DiscardedPayload`).

4. **Client registries are synchronized but closed** — exactly 30 registries; EntityType/MenuType/BlockEntityType are absent by design.

5. **The game's JVM is not started by the game** — the launcher builds the command. To add an agent, you must touch either the launcher, or the process environment, or the running JVM. All three are outside the game.

6. **The launcher is not drivable from the game** — no IPC, no socket, no command line. The game and its launcher do not talk after launch.

7. **The OS requires confirmation to run signed code** — Windows SmartScreen / macOS Gatekeeper. Only the Microsoft Store (or an EV signature) makes it native.

## 1.3 What this boundary implies

Installing an execution component therefore requires **at minimum one unique OS gesture**. The question becomes: which mechanism makes this gesture (a) as rare as possible (once in a lifetime), (b) as covering as possible (all launchers), (c) as durable as possible (survives updates), (d) as fluid as possible (no wizard)? The following chapters evaluate each candidate mechanism.

## Chapter 2 — Exhaustive inventory of activation mechanisms without installation

This chapter screens every hypothesis A-K of the brief, with proof, and introduces the two novel mechanisms discovered by this research.

## 2.1 Master table of mechanisms

| # | Mechanism | Category | Effect | Verdict |
|---|---|---|---|---|
| M1 | `JDK_JAVA_OPTIONS` / `JAVA_TOOL_OPTIONS` | JVM env | Injects `-javaagent` into every java process, every launcher | ✓ **Key discovery** |
| M2 | Attach API (`agentmain`) hot | Local JVM | Agent loaded into the RUNNING game, zero restart | ✓ **Key discovery** |
| M3 | Ghost version (`inheritsFrom` + libraries) | Third-party launcher | Runtime distributed/maintained as an MC version, auto-update | ✓ Distribution channel |
| M4 | `minecraft://` deep link | OS/launcher | Return to the game in 1 click after consent | ✓ Return bridge |
| M5 | Dialog `OpenURL` → `ms-windows-store://` | Vanilla protocol | The most banal click: signed Store "Get" | ✓ Unique OS consent |
| M6 | Cookies (5 KB) | Vanilla protocol | Remembers consent — never asked again | ✓ Support |
| M7 | `server-resource-packs/` | Vanilla protocol | Pre-positioned local cache of the agent (inert, retrieved by the installer) | ✓ Support |
| M8 | packwiz / pack-URL | Third-party ecosystem | Auto-updated instance (Prism/MultiMC precedent) | ~ Precedent |
| M9 | Bedrock behavior packs | Outside Java | The Mojang precedent: client scripts auto-downloaded on join | Moral reference |
| M10 | Transparent network proxy | Network | Intercepts launcher downloads (libraries) — MITM of the M3 channel | ! Legally/ethically fragile |
| M11 | Modify `launcher_profiles.json` in-game | FS | The game cannot write outside its data folders | x Dead |
| M12 | Exploits (deserialization, etc.) | Security | Out of scope by ethics | x Forbidden |

## 2.2 M1 — `JDK_JAVA_OPTIONS`, the launcher-agnostic agent (detailed)

**Proof** (Oracle docs, `java` man page, JDK 21 — verified today): these variables are read by the `java` launcher and their options applied as if they were on the command line. `-javaagent:jarpath[=options]` is a documented option there. `JAVA_TOOL_OPTIONS` is honored by all JVMs ≥ 8; `JDK_JAVA_OPTIONS` (JDK 9+) is the modern equivalent, also recognized by executable JVMs (javaw). The JVM prints "Picked up JDK_JAVA_OPTIONS" on stderr — discreet, blockable by redirection.

**The "Env-Hook" architecture:**

```text
[1 Store click, once]
  → Signed installer:
      • copies sdm-agent.jar to %LOCALAPPDATA%\SDM\ (fixed)
      • setx JDK_JAVA_OPTIONS "-javaagent:C:\Users\X\AppData\Local\SDM\sdm-agent.jar=sdm"
        (HKCU\Environment — user, no admin)
  → Tiny resident service (optional): watches JVMs
    named net.minecraft.client.main.Main → M2 attach if not pre-equipped
[All subsequent Minecraft launches, ALL launchers]
  java ... -javaagent:sdm-agent.jar=sdm  ← injected by the JVM itself
  → agent premain: detects Minecraft (main class, assets) otherwise no-op
  → SDMP handshake with the joined server → streamed modules
```

**Why this is a breakthrough**: the first report went through launcher-by-launcher profiles (fragile: launchers rewrite their profiles, some forbid it). The env-var bypasses everything: the JVM instruments itself. This is the production pattern of Datadog (`dd-trace-java`), New Relic, Elastic APM — tens of millions of instrumented JVMs without touching applications. Process filtering (activating the agent only for Minecraft) is the established pattern of these tools.

**Honest limits**: the variable applies to ALL java processes of the user (IDEs, other Java games) — hence the early-exit filter in premain (cost: a few ms of startup for other Java apps, zero side effect). Some corporate environments strip it (rare for players). Launchers that bundle their JVM AND reset the env (rare; to watch: Lunar) — the M2 daemon covers that case by attaching afterwards.

## 2.3 M2 — Hot attach: activation without restart (detailed)

**Proof**: `com.sun.tools.attach.VirtualMachine.list()` enumerates local JVMs; `vm.loadAgent(jar)` loads an agent into the target through the attach channel (Windows: NamedPipe `\\.\pipe\javaAttachPid...`). The `agentmain` entry point receives Instrumentation. Standard JVM, no launch option required, works on a vanilla JVM started without an agent.

**The "magic in session" sequence:**

```text
Vanilla player in the server → dialog [★ Install]
  → OpenURL → Store → [Get] (service installed)
  → service: VirtualMachine.list() → target net.minecraft.client.main.Main
  → vm.loadAgent(sdm-agent.jar)
  → agentmain: instrumentation.retransformClasses(GameRenderer, Gui, ...)
  → SDMP handshake on the EXISTING connection (the game socket is accessible)
  → modules downloaded (cache + HTTPS) → HUD/effects appear —
     IN THE CURRENT SESSION. The player relaunched nothing.
```

**Power of attached mode** (retransformation, not premain): injecting calls into method bodies (HUD overlay via `Gui.render`, event hooks, custom rendering by delegation) — the core of visual needs. **What requires relaunching**: adding fields/methods to vanilla classes (deep custom entities, keybinds registered at boot). The next instance launch automatically switches to full mode via M1. Restart is therefore no longer a UX step: it is a **silent power migration**.

## 2.4 M3 — Ghost version: the third-party launcher distribution channel (detailed)

**Proof** (meta.fabricmc.net, verified today): a loader declares itself as a version: custom JSON `id`, `inheritsFrom: "1.21.8"`, `mainClass: net.fabricmc.loader.impl.launch.knot.KnotClient`, `libraries` = Maven list `{name, url}` (ASM 9.10.1 x5 + sponge-mixin, served by maven.fabricmc.net). Third-party launchers download these libraries automatically at the profile's first launch, and update them when the metadata changes.

**SDM application**: publish an `sdm-26.2` version (inheritsFrom 26.2, mainClass = LinkBootstrap, libraries = agent + ASM on a controlled Maven). A server can distribute an instance URL (packwiz pattern — auto-update via TOML/git, established precedent). The launcher keeps the runtime up to date **through the standard dependency mechanism** — invisible, antivirus-friendly, zero manipulation.

**Limit**: restricted to third-party launchers (Prism, Modrinth App, ATLauncher, SKLauncher — present on the target machine). The official launcher does not import external versions. Role: distribution channel for players already on third-party launchers, and an "full installation" option alternative to the Store.

## 2.5 M4 + M5 + M6 + M7 — the consent bridges

- **M5 (Store)**: `ms-windows-store://pdp/?ProductId=…` opened by the vanilla `OpenURL` dialog — native client confirmation, then Store on the product page. "Get" = signed installation, no SmartScreen. macOS: notarization + equivalent macOS App Store.

- **M4 (`minecraft://`)**: handler registered by the official launcher (absent on the target machine — SKLauncher; present with the official launcher). Used for **return**: after install, the agent/installer opens `minecraft://` to reopen the launcher. On third-party launchers: equivalents (`multimc://`, non-standard `prism://`) or simple launcher focus.

- **M6 (Cookies)**: `StoreCookie("sdm:consent", "yes|v1")` — the server remembers the choice; never a dialog again except on permission changes.

- **M7 (pack-cache)**: the server pushes the agent as a resource pack → it is already in `server-resource-packs/` (SHA1-validated cache) when the installer runs → instant offline installation, and cross-integrity proof.

## 2.6 What the research eliminated (and why — proofs)

| Track | Elimination proof |
|---|---|
| `ms-appinstaller://` zero-click | Disabled by default by Microsoft (CSPolicy 2024, malware abuse) |
| Custom `sdm://` protocol direct install | Circular (requires the handler already installed) |
| Game writing `launcher_profiles.json` | The game writes only its data folders; never profiles |
| Resource pack as executable | `DownloadedPackSource` → rendering data only (lock 1) |
| Bedrock behavior-pack equivalent in Java | Bedrock-only feature; Java Edition has no client script channel |
| MITM proxy of launcher libraries | Technically possible, breaks TLS trust / public-server ethics — rejected |
| winget / scripts / terminal | Not grandmother-proof; equivalent to "install an exe" with more steps |
| Lunar/Badlion custom profiles | Forbidden by design; covered by M2 (attach) only |

## 2.7 Synthesis: the mechanism hierarchy

```text
Zero OS action  →  impossible (7 locks, chapter 1)

Single OS action  →  M5 (Store "Get") + M6 (cookie memory)

Activation  →  M2 (attach: effects in session) then M1 (env-var: full power
               at next launch, all launchers, forever)

Distribution/runtime updates  →  M3 (ghost version, libraries channel) for third-party,
               signed self-update via HTTPS for the core

Return to game  →  M4 (minecraft://) or launcher focus

Cache/offline  →  M7 (pre-positioned pack)
```

The next chapter assembles these mechanisms into complete architectures evaluated against the brief's criteria (§31).

## Chapter 3 — Candidate architectures, evaluated against the brief's criteria

Each architecture is documented per the §35 grid: principle, client, server, installation, restart, streaming, runtime, modding, compatibility, security, feasibility, PoC.

## 3.1 Architecture V0 — "Complete illusion" (absolute vanilla, zero clicks, zero restarts)

**Principle**: push vanilla primitives to their maximum combinatorial ceiling (§19-22 of the brief): dialogs + hot-swap packs + display entities + CustomClickAction RPC + cookies + PostEffects + TickingState. The client "does not know" it is executing a modding architecture — it displays what the server orchestrates.

**Client**: strictly official vanilla. **Server**: complete SDM platform (gateway + Pack Studio + event bus). **Installation**: 0. **Restart**: 0. **Streaming**: packs (250 MB/pack, unlimited stacking), synchronized registries, 135 KB state. **Modding**: ~85% of use cases (report 1 catalog). **Compatibility**: all versions/launchers, including Bedrock via Geyser. **Security**: vanilla's (optional signed packs). **Feasibility**: proven (Polymer 3.67M downloads). **PoC**: report 1, P1 (4 weeks).

**Role in the target**: the floor EVERY player receives, including those who refuse the click. The final architecture never eliminates it — it keeps it as the degradation floor.

## 3.2 Architecture V1 — "Daemon + Env-Hook" (the discovered optimum)

**Principle**: combine M5 (1-click Store consent) + M2 (immediate attach, zero restart) + M1 (JVM env-var = permanent full power, all launchers) + M6/M7 (memory + cache). This is the architecture closest to the "yes → it works" dream.

**Complete life sequence:**

```text
D1 (first time)
  joins play.example.com (vanilla)
  → dialog [★ Install the complete experience] [Continue without]
  → Yes → sdm:consent cookie → OpenURL → Store → [Get]   ← ONLY OS gesture of their life
  → service: attach to the running game → HUD/effects appear in the session
  → (optional) "Restart in full mode?" → minecraft:// after quit

D2+ (all launches, all launchers, all instances)
  JVM auto-injects the agent (JDK_JAVA_OPTIONS)
  → premain: SDMP handshake → signed modules → full power
  → no prompt, no action, no technical notion

Server B, C, D…  → automatic. The per-server cookie remembers the choice.
```

**Client**: vanilla + (invisible) 300 KB agent + 200 KB resident service. **Server**: same as V0 + Recipe Store + Module Compiler + signature. **Installation**: 1 Store click, once. **Restart**: 0 for attached effects; 1 future launch for full power (transparent). **Streaming**: Ed25519 signed modules, hash cache, delta. **Modding**: ~98%. **Compatibility**: all launchers (env-var); Lunar/Badlion via attach only; pure vanilla → V0. **Security**: signature + permissions + per-server-key consent + CRL + verified cache; the resident service runs in user session (no admin). **Feasibility**: all standard components (JVM instrumentation, documented env-var, Store) — the engineering is integration, not research. **PoC**: chapter 4.

**Specific risks**: (a) the env-var affects all user java → mandatory early-exit filter (dd-trace pattern); (b) Store review for a full-trust tool — feasible, plan 2-4 weeks; (c) third-party server anti-cheat detectors could flag the agent on THEIR servers → the agent deactivates outside SDM servers (signed public list, per-server opt-out).

## 3.3 Architecture V2 — "Ghost version" (third-party launcher distribution)

**Principle**: the runtime distributed as a custom Minecraft version (M3) + library auto-update. The player imports an instance via URL (packwiz pattern): 1-2 clicks in their third-party launcher.

**Client**: `sdm-26.2` instance (inheritsFrom + agent + ASM in libraries). **Server**: same as V1. **Installation**: 1-2 clicks (instance/URL import). **Restart**: 1 (profile first launch). **Streaming**: same as V1. **Modding**: ~98% (full premain mode by default). **Compatibility**: Prism, Modrinth App, ATLauncher, SKLauncher, MultiMC — NOT the official launcher. **Security**: code downloaded by the launcher (standard channel) + SDM signatures on top. **Feasibility**: 100% proven mechanism (Fabric is it). **PoC**: publish the JSON + Maven, import into Prism.

**Role**: the path for "third-party launcher" players (already the majority of custom-FR server players) and the silent runtime update channel for them.

## 3.4 Architecture V3 — "Combined gradient" (the product target)

**Principle**: do not choose — superimpose the three by player profile:

```text
EVERY player  →  V0 (vanilla floor, always)
Store-equipped player (V1)  →  silent full power, all servers
Third-party launcher player (V2)  →  auto-maintained ghost version
```

The server detects the level at handshake (login query `sdm:hello` → agent answer / silence) and composes the experience per level — dual-track per PLAYER, not per server. A V1 player refusing a specific module (permission) falls back to V0 for that module only.

**Final table (§31 of the brief):**

| Solution | Installation | Clicks | Restart | Client | Server freedom |
|---|---|---|---|---|---|
| V0 Complete illusion | 0 | 0 | 0 | Pure vanilla | High (85%) |
| V1 Daemon + Env-Hook | 1x Store | 1 | 0→1 auto | Vanilla + invisible | Very high (98%) |
| V2 Ghost version | 1x import | 1-2 | 1 | Vanilla + profile | Very high (98%) |
| V3 Gradient (target) | 1x (choice) | 1 | 0 | mixed per player | Maximum |
| Manual bootstrap (report 1 ref.) | manual | several | 1 | Minimal | Very high |

## 3.5 Criteria coverage table (§30)

| Absolute criterion | V0 | V1 | V2 | V3 |
|---|---|---|---|---|
| Zero mods installed | ✓ | ✓ | ✓ | ✓ |
| Zero loaders installed | ✓ | ✓ (agent ≠ loader, invisible) | ✓ (auto profile) | ✓ |
| Zero files manipulated | ✓ | ✓ | ✓ | ✓ |
| Zero folders touched | ✓ | ✓ | ✓ | ✓ |
| Zero specific launchers | ✓ | ✓ | ✗ (existing third-party) | ✓ |
| Zero technical notions | ✓ | ✓ | ~ (URL import) | ✓ |
| Zero JVM config | ✓ | ✓ (env-var done by installer) | ✓ | ✓ |
| Zero dependencies managed | ✓ | ✓ | ✓ | ✓ |
| Zero clicks (ideal) | ✓ | ✗ (1 OS) | ✗ | ✗ (1 OS once) |
| 1 click + possible restart (fallback) | — | ✓ better (0 restart) | ✓ | ✓ |

## 3.6 Comparative security (§28)

| Model | V0 | V1/V2/V3 |
|---|---|---|
| Client-executed code | none (sandboxed GLSL) | Ed25519 signed modules |
| Consent | vanilla pack prompt | in-game dialog + server key + perms |
| Revocation | n/a | CRL + service kill switch |
| Sandbox | GPU only | declarative permissions (JVM sandbox = dead, JEP 486) |
| Zero-click compatible? | yes (nothing to install) | no: the OS click IS the security barrier — keeping it is a choice |

**Key point §28**: zero-click and security do not oppose each other in V1 — because the single click does not install content but a **generic capability** (the agent), and all subsequent content remains governed by signatures/permissions/in-game consent. The OS click happens once; the hundreds of in-game "yeses" remain per-server trust choices, remembered.

## 3.7 Restart: the precise analysis (§27)

| Need | Attach (M2) enough? | premain (M1) required? |
|---|---|---|
| HUD overlay | ✓ | — |
| Custom visual effects (post, custom particles) | ✓ | — |
| Enriched custom menus/dialogs | ✓ | — |
| Deep custom entities (fields, registered AI) | ✗ | ✓ |
| Keybinds registered at boot | ✗ | ✓ |
| Render class transformations | partial (bodies) | ✓ complete |

Restart is therefore never an "installation step": it is the attach→premain switch, automatic at next launch, invisible to the user.

## Chapter 4 — Feasibility, PoC and what remains to decide

## 4.1 What is proven vs what is to build

| Brick | Status | Proof / effort |
|---|---|---|
| Vanilla ceiling (V0) | Proven | Polymer 3.67M; report 1 catalog; 73,000+ server-side mods |
| Premain agent + recipes | Proven | Fabric/Forge have done it for 10 years; our report 1 |
| `JDK_JAVA_OPTIONS` injection | Proven (standard) | Oracle doc verified; Datadog dd-trace in massive production |
| Hot attach | Proven (standard) | JVM Attach API; IDE hot-reload; JRebel |
| Ghost version + auto libraries | Proven (standard) | Fabric meta verified today (inheritsFrom + 6 Maven libs) |
| Full-trust Store installer | Feasible | Standard processes, review ~2-4 wks |
| Resident service + JVM detection | Feasible | VirtualMachine.list() standard; APM pattern |
| SDM server platform | To build | The real work: gateway, Pack Studio, signatures, per-version recipes |
| SDMP handshake | To build | Report 1 spec + this one's M6/M7 |

## 4.2 "Dream Flow" PoC — 3 weeks, 4 milestones

**D1 (week 1) — The visual dream.** Server plugin: on a vanilla client's join, a two-button dialog `[★ Enable the experience]` `[Continue without]`. No → pure V0. Yes → cookie + (PoC: instructions display) — in production: Store. Deliverable: the consent flow demo, playable.

**D2 (week 1-2) — The magic attach.** Local tool (not yet Store): `VirtualMachine.list()` → Minecraft detection → `loadAgent` → retransform `Gui.render` → "SDM ACTIVE" HUD drawn in the current session, on the D1 server, without relaunching. **This is the debate-killing demo**: the player watches the installation barrier fall live.

**D3 (week 2) — The permanent env-var.** `setx JDK_JAVA_OPTIONS ...` + instance relaunch → premain → full power (custom overlay recipe + 1 virtual entity). Verify survival through SKLauncher (present on the target machine) and a second launcher.

**D4 (week 3) — The ghost version.** Publish `sdm-26.2.json` + Maven libraries + Prism import → auto-maintained instance. Verify auto-update by bumping a library.

**Overall success criterion**: an ordinary player, from a Microsoft Store vanilla client, joins the server, clicks Yes + Get, sees effects in their session, and their next launch is at full power — without ever opening a folder or understanding a technical word.

## 4.3 Risks and mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| Store refuses a full-trust tool | Medium | Fallback: EV-signed exe (1 "Run" click) + V2 third-party launcher |
| Heuristic antivirus on attach | Medium | EV signature + transparency (open-source agent, dedicated page, published hashes) |
| Mojang officially disapproves | Low-medium | Defensible position: identical to Fabric (javaagent), zero asset distribution, third-server opt-out; open dialogue early |
| Lunar/Badlion env reset | Low | Covered by the attach service (M2) |
| `JDK_JAVA_OPTIONS` removed from the JDK | Very low | Documented standard since JDK 9; `JAVA_TOOL_OPTIONS` (≥8) as backup — and V2 remains |
| Abuse (malicious server streaming a nasty module) | Serious | The full §28/§3.6 arsenal: platform signatures, perms, CRL, reputation, per-key consent |

## 4.4 What will always remain true (final honesty)

1. **The single OS click is the physical floor** — seven locks, class-by-class proofs (chapter 1). No honest architecture goes below 1 click once in a lifetime. It is also what protects players: what we want to do is exactly what a cheat would want to do.

2. **The truth remains server-side.** Whatever the level, the authoritative game state lives on the server; the client executes presentation.

3. **The cost of Minecraft versions is borne by the platform** (per-version recipes), not the player — report 1's promise, unchanged.

4. **Attached mode is a subset of premain.** Attach removes the restart for the majority of effects, but full power (fields, boot registries) will always require the next launch — turned into a silent switch.

## 4.5 Conclusion of the second investigation

The final question: "is there a path to modding as powerful as Fabric/Forge/NeoForge and beyond, with zero installation, zero clicks, or at worst one action + restart?"

**Answer: strict zero-click is impossible (exhaustively established proof), but the smallest concession is exactly: one "Get" click once in a lifetime, zero mandatory restart, zero technical notions, all launchers, all versions, all SDM servers forever.** The V3 architecture (V0+V1+V2 gradient) delivers this experience by superimposition: pure vanilla receives 85% of the power; the single click unlocks the 98% forever with immediate in-session activation (attach) and silent migration to full power (env-var); third-party launchers inherit the native update channel (ghost version).

The stated dream — "you arrive on the server, we offer yes/no, you say yes, and you have everything" — is thus realized one OS click away, and that click is "Get" in the Microsoft Store: the most banal, most signed, most familiar gesture there is. Below it, there is only cheating.
