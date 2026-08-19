# Executive summary and final verdict

## The question

This report answers a single question, broken into two parts:

1. **Can Minecraft be transformed into a platform where the vanilla client serves as the execution base, and where the server dynamically supplies the client with the capabilities needed to create a near-unlimited experience?**

2. **What is the smallest client modification that grants the greatest server freedom?**

The method follows the imposed hierarchy: maximum server freedom, minimum client footprint, zero initial modification. Each escalation level is introduced only when a technical proof demonstrates that the previous level has plateaued.

## The three major results

### Result 1 — Level 0 covers roughly 85% of modding use cases

A strictly vanilla client — never modified, never installed through a loader — can today receive, purely through server packets and resource packs: custom items with unlimited appearances, blocks and furniture, animated visual entities, complete interfaces (forms, menus, text input), custom sound and music, GLSL shaders compiled by the client GPU, data-driven world generation, persistent cross-server state systems, and even simulation-time control (bullet-time). This ceiling is not theoretical: it is proven at ecosystem scale by Polymer (3.67 million downloads, 100% server-side projects playable from a pure vanilla client) and by the 73,000+ mods tagged server-side on Modrinth. The foundation already exists; what is missing is a platform to orchestrate it.

### Result 2 — The Level 0 wall is structural and precisely bounded

Three families of needs are impossible on a strictly vanilla client, by construction confirmed in the decompiled code:

- **executing arbitrary client code** (no vanilla mechanism allows it; the only "streamable code" is sandboxed GLSL via PostEffects);

- **creating true new entity types, block-entities or menus** (client registries are closed and synchronized — exactly 30 registries, not one more);

- **capturing input, displaying a pixel-perfect HUD, modifying geometry rendering** (no packet exists for this).

Everything else is either already possible or convincingly approximable. This is the exact boundary separating "server-side data-driven modding" from "client code-driven modding".

### Result 3 — The optimal minimal client modification exists: a generic agent of about 300 KB, installed once, independent of Minecraft versions

The JVM analysis (ClassFileTransformer, defineClass, classloader lifecycle, retransformation constraints, JEP 451 and 486) demonstrates that a javaagent preinstalled at launch — small, generic, knowing nothing about mappings or versions — is enough to give the server more client-side power than Fabric/Forge/NeoForge:

- the server streams **modules** (compiled code) executed in a child classloader;

- the server streams **transformation recipes** (the equivalent of mixins, targeted by version, applied before game classes load);

- the server negotiates **capabilities** (rendering, input, HUD, dynamic registries) via a handshake protocol;

- the client becomes a **universal terminal**: a single installation serves every compatible server, with signed cache, permissions, and hot updates.

The report names this architecture **SDM — Server-Driven Modding**, its protocol **SDMP**, and its client component **LinkAgent**.

## The measured optimal trade-off

| Dimension | Typical Fabric/Forge modpack | SDM architecture |
|---|---|---|
| Client installation | Loader + API + 10-200 mods, 0.5-2 GB | 0 (Level 0) or 1 click once (~300 KB) |
| Per-server action | None (but incompatible with other servers) | None — each server streams its content |
| Use-case coverage | ~100% (arbitrary code) | ~85% at 0 KB, ~98% with the agent |
| Mod updates | Manual, player-side | Server-driven, hot |
| Per-world / per-player mods | No | Yes (server is the source of truth) |
| Multi-version | One installation per MC version | A single runtime, recipes targeted per version |
| User experience | Technical (folders, versions, dependencies) | "Launch Minecraft, join, play" |

## What this report does not claim

Out of technical honesty, three limits are documented as real and currently unavoidable:

1. **The strong JVM sandbox is dead** (JEP 486: Security Manager removed in JDK 24). The realistic trust model is that of application platforms — Ed25519 signatures, declarative permissions, reputation, revocation — not memory isolation. The WASM track is documented as a research outlet.

2. **High-performance custom rendering requires streamed modules** — Level 0 only provides declarative mechanisms (models, Display entities, post-process shaders). A custom geometry shader remains above the vanilla ceiling.

3. **Hot code unloading is imperfect on the JVM**: classes unload only when their classloader becomes GC-orphaned; the architecture plans "deactivate + isolate" and documents the residual memory limit.

## Verdict in one sentence

The architecture is feasible, and it is even partially proven already by the ecosystem (Polymer, Geyser, vanilla-like protocols); what does not exist yet is the orchestration layer — the SDMP protocol, the LinkAgent, the recipe compiler, and the server-side dependency manager — whose complete architecture, protocol, security model, PoC, and 11-phase roadmap this report defines.

## Chapter 1 — The true nature of a mod: why loaders require a modified client

Before trying to move the boundary, one must understand why it exists. This chapter deconstructs the mod into layers, identifies what currently requires a client installation, and classifies real mods into categories A-I.

## 1.1 Anatomy of a modern mod

A Fabric/Forge/NeoForge mod is a JAR typically containing:

| Layer | Content | Where does it run? |
|---|---|---|
| Business logic | Economy, progression, quests, scripts | Server (always) |
| Registered content | Blocks, items, entities — via Registry | Both (the ID must exist on both sides) |
| Assets | Textures, models, sounds, language | Client (resource pack) |
| Data | Recipes, loot tables, worldgen | Server data pack |
| Custom networking | C2S/S2C packets with StreamCodec | Both (identical codec on both sides) |
| Client mixins | Injections into rendering, input, HUD | Client |
| Server mixins | Injections into ticking, saves | Server |

The fundamental principle of current loaders: **content can only exist if it is registered in the same registry, with the same ID, on both sides of the connection**. A custom block requires a `Block` class compiled with the same mapping version — hence a modified client. This is an **architectural constraint of loaders**, not of Minecraft itself.

## 1.2 The three levels of truth: exposed / implemented / permitted

| Level | Question | Examples |
|---|---|---|
| **What Minecraft exposes** | What is officially accessible? | Data packs, resource packs, command blocks, plugin messaging |
| **What Minecraft implements** | Which mechanisms really exist in the code? | 225 packets, 30 synchronized registries, Dialog system, CustomClickAction, PostEffects, TickingState, cookies |
| **What the JVM allows** | What does the Java platform permit beneath the surface? | defineClass, javaagents, instrumentation, retransformation, hierarchical classloaders, modules |

Current loaders operate at levels 2 and 3, but **client-side, with installation**. The SDM thesis inverts the perspective: operate at levels 2 and 3 **server-side, without installation**, and touch client level 3 only through the smallest possible agent.

## 1.3 A-I classification of mods by their real client need

For each category: current need, real need (once the architecture is inverted), and server-side portability.

### Category A — Pure server

- **Examples**: WorldEdit/grand principles, Essentials, LuckPerms, scoreboard systems, most Bukkit plugins.

- **Current client need**: none.

- **SDM verdict**: trivial. Already solved by the plugin ecosystem (73,000+ server-side mods on Modrinth).

### Category B — Server + client data (assets)

- **Examples**: new decorative wood/block, appearance items, visual-enhancement resource packs.

- **Current client need**: resource pack distributed separately, or mods registering the item.

- **SDM verdict**: **solved at Level 0**. The server pushes a resource pack (hot-swappable in-game, 250 MB max/pack) and uses `ITEM_MODEL` + `CUSTOM_MODEL_DATA` to give a vanilla item (paper, stick) hundreds of appearances. Polymer proves it with 3.67M downloads.

### Category C — Server + rendering

- **Examples**: minimaps, visual shaders, XXL models, custom entity rendering.

- **Current client need**: client mod (mixin into the renderer).

- **SDM verdict**: **partially Level 0** — Display entities + interpolation, MapPatch 128x128, PostEffects (full-screen GLSL post-process). Custom geometric rendering (new living entity models with skeletal animation) requires Level 3 (streamed module).

### Category D — Server + GUI

- **Examples**: tech machine menus, custom HUDs, overlays.

- **Current client need**: client mod (custom MenuType + custom screen).

- **SDM verdict**: **mostly Level 0**. Dialog system (5 types, 4 input controls, actions) + CustomClickAction (bidirectional 32 KB NBT RPC) + 3.2 MB interactive books + 29 reusable vanilla MenuTypes. Pixel-perfect HUD requires Level 3.

### Category E — Server + input

- **Examples**: custom keybinds, gestures, double-tap dash.

- **Current client need**: client mod (KeyMapping).

- **SDM verdict**: **Level 3 required** — no vanilla packet carries keybinds. Level 0 workarounds: dialog inputs, command autocomplete, chat commands, held-item state. Free keybinding requires the streamed module.

### Category F — Server + client mixins

- **Examples**: zoom (OptiFine-like), FPS boost, HUD tweaks.

- **Current client need**: client mod mandatory.

- **SDM verdict**: **Level 3**. This is exactly the role of streamed transformation recipes: the server describes the injection (target method per version, handler in the module), the agent applies it before the class loads.

### Category G — Deep client engine mod

- **Examples**: new renderers, custom physics, voxel engines, epic shaders.

- **Current client need**: deeply modified client (often coremods + manual ASM).

- **SDM verdict**: **strong Level 3** — streamed modules + possibly more invasive transformation recipes. Feasible, but each of these mods must be rewritten as an SDM module.

### Category H — Protocol modification

- **Examples**: ViaVersion (cross-version), Geyser (Bedrock-Java), custom protocols.

- **Current client need**: variable (often proxy).

- **SDM verdict**: the SDM server absorbs translation — as Geyser/Hydraulic prove (5752 stars): a Bedrock client joins a modded Java server through architecture translation. SDM generalizes this pattern: the server IS the translation platform.

### Category I — Launcher/bootstrap

- **Examples**: installers, bootstraps, launch wrappers.

- **Current client need**: variable.

- **SDM verdict**: this is the LinkAgent (Level 3). Installed once, it makes every SDM server accessible.

## 1.4 Synthesis: what is "intrinsically client" vs "client by architectural choice"

The capital distinction requested by the brief:

| Need | Intrinsically client? | Justification |
|---|---|---|
| Execute arbitrary code | Yes — but the question is *how* it gets there | No vanilla packet carries bytecode. But a preinstalled agent removes the need to install code per server. |
| New registries/IDs | **No** — architectural choice of loaders | Proof: Polymer creates custom blocks without a modded client via virtualization. Closed client registries concern *types*, not *instances*. |
| Textures/models/sounds | **No** | Resource packs streamed by the server — official vanilla mechanism. |
| Custom geometric rendering | Yes (rendering = client GPU) | But streamable: post-process GLSL shaders are already vanilla; the streamed module completes the picture. |
| Keybind input | Yes (OS-level) | No packet; only a client hook can provide it. |
| Pixel-perfect HUD | Yes (presentation layer) | Bossbars/scoreboard/titles = workarounds; the streamed module provides it properly. |
| Business logic | **No** | Always server. |
| Worldgen | No | Vanilla data-driven (data packs) + synchronized registries (BIOME, DIMENSION_TYPE...). |
| GUI forms | **No** | Dialog system + CustomClickAction, vanilla. |
| Cross-server state | **No** | Cookies (5 KB/key) + TransferState, vanilla. |

**Chapter conclusion**: the necessity of a modified client is, for most use cases, an ecosystem convention — not a law of Minecraft. The true boundaries are: client executable code, entity types, input, HUD, deep rendering. These are precisely the five fronts the following chapters attack.

## Chapter 2 — Level 0: the real ceiling of the strictly vanilla client

This chapter measures what a server can impose on a **strictly official vanilla** client — no loader, no modification, no custom launcher. Everything below is verified against the decompiled client code (baseline 26.3-snapshot-7, protocol 1073742153; current release 26.2) and grouped by modding capability.

## 2.1 The raw inventory: what the protocol offers

The modern protocol counts **225 packet classes** (152 clientbound + 73 serverbound) spread over 6 phases (handshake, login, configuration, play, cookie, status). The CONFIGURATION phase — introduced in 1.20.2 — is the silent key of the architecture: it is a pre-game negotiation space where the server pushes resource packs, synchronized registries, and feature flags before the first game tick.

## 2.2 Content: items, blocks, "entities" without client registry

The central discovery: **client registries are closed only for types, not for instances**.

| Mod goal | Vanilla mechanism | Hard limit |
|---|---|---|
| Custom item (appearance) | `ITEM_MODEL` (redirects any item to any pack model) + `CUSTOM_MODEL_DATA` (4 lists: floats/flags/strings/colors) | Practically unlimited; one vanilla item = hundreds of appearances |
| Custom static block | Resource pack: model + texture on a vanilla support block (polymer: note block / fletching table / mushroom); CustomModelData on the block item | Pure rendering — no custom client block behavior |
| Custom "furniture" block | `BlockDisplay` entities + interpolation (translation/scale/rotation) | Server-side behaviors (hitbox via server barrier/collision) |
| Visual entity | TextDisplay / ItemDisplay / BlockDisplay + synchronized data + animation interpolation | No visible custom AI — the server drives movement |
| Custom sound / music | `sounds.json` + .ogg files in the streamed pack; custom SoundEvent; 10 channels | Vorbis; `stream: true` for large files |
| Custom fonts / icons | Bitmap / TTF / Unihex providers; Private Use area U+E000-U+F8FF | Glyph to inline icon in chat/books/dialogs/titles |

This is Polymer's (Patbox) demonstration: 3,671,063 downloads, entire mods (PolyFactory: complete automated factories, ~65,000 downloads) playable from a pure vanilla client. Custom "registry-closed" content is not a wall — it is a convention bypassable by virtualization.

## 2.3 Interfaces: the complete server-driven UI kit

The Dialog system (1.21.6+) is a complete form framework pushed by packet:

- **5 dialog types**: Notice, Confirmation, MultiAction, ServerLinks, DialogList.

- **Bodies**: PlainMessage (rich text), ItemBody (item preview with data components).

- **4 input controls**: BooleanInput, NumberRangeInput, SingleOptionInput, TextInput (multiline).

- **Actions**: OpenURL, SuggestCommand, RunCommand, and above all `CustomAll` → `ClickEvent.Custom` → the client returns `ServerboundCustomClickActionPacket(id, NBT)`.

Completed by:

- **29 reusable vanilla MenuTypes** (chest, anvil, loom, brewing stand...) with custom title and content driven by ContainerSetContent/Slot — server-side "fake menus" are an established art;

- **3.2 MB interactive books**: 100 pages x 32,767 chars of Components with ClickEvent/HoverEvent — true clickable client-side applications;

- **Clickable rich chat** (CustomClickAction everywhere: chat, dialog, book, panel, title, action bar).

**Verdict**: "form" and "inventory-menu" style GUIs are entirely Level 0. What is missing: the permanent pixel-perfect HUD (workarounds: bossbar, scoreboard sidebar, tab list, title, framed map) and free drag-and-drop.

## 2.4 Data channels: RPC and state

The generic bidirectional RPC channel of the vanilla protocol:

```text
ClickEvent.Custom(id: Identifier, payload: Optional<Tag>)
   ↓ user click (chat/dialog/book/panel/title)
ServerboundCustomClickActionPacket(id, Optional<Tag>)   [32,768 bytes, depth 16]
   ↓ the server validates (UNTRUSTED) and executes
```

Other channels, with their hardcoded limits:

| Channel | Direction | Capacity | Persistence |
|---|---|---|---|
| CustomClickAction NBT | Bidirectional | 32 KB / interaction | Session |
| Cookies | Bidirectional | 5,120 B / key | Cross-server (TransferState) |
| CustomReportDetails | S→C | 32x(128+4096) ≈ 135 KB | Survives CONFIG↔PLAY (CommonListenerCookie) |
| CustomPayload 1 MB | S→C | 1 MB/packet | **DISCARDED by the vanilla client** (DiscardedPayload) — not a real channel |
| Handshake hostname | C→S | 255 chars | Pre-auth (already used by Velocity/Bungee) |
| TagQuery / BlockEntityTagQuery | Bidirectional | Transactional NBT | Session |
| Registry data | S→C | 30 synchronized registries | Re-synchronizable hot |

## 2.5 The four signature mechanisms of Level 0

### In-game resource hot-swap

`ResourcePackPush/Pop` are **common** packets (CONFIG and PLAY). A server can change textures, models, sounds, fonts, shaders **while the player is playing**, with hot reload client-side. Combined with PLAY→CONFIGURATION→PLAY re-entry (`StartConfiguration`), the server can also re-push registries and tags mid-session — a season change, thematic dimension, or complete game-mode switch without reconnection.

### Bullet-time and client tick control

`ClientboundTickingStatePacket(tickRate, isFrozen)` drives the entire client's tick rate (floor 1.0). `TickingStepPacket` advances N ticks in freeze. Cinematics, slow motion, photo mode: Level 0.

### Streamed GLSL shaders (PostEffects)

The resource pack can define post-processing chains (`post_effect/*.json` + `.vsh`/`.fsh` shaders) that the vanilla client **compiles and executes on the GPU** every frame. This is the closest point to client code execution vanilla allows: sandboxed GLSL (no file/network access), perfect for color grading, vignetting, heat mirage, CRT scanlines, cinematic letterbox. **Present it for what it is**: sandboxed GPU visual code, not arbitrary execution.

### Persistent cross-server state

Cookies (5 KB/key, as many keys as wanted) + `ClientboundTransferPacket`: the client automatically reconnects to another server carrying its state (`TransferState`). A multi-server platform (hub → thematic worlds) works in pure Level 0, with continuous progression.

## 2.6 The Level 0 wall — confirmed in code

| Need | Status | Source proof |
|---|---|---|
| Execute arbitrary client code | Impossible by construction | No mechanism; CustomPayload 1 MB → `if (payload instanceof DiscardedPayload) return;` — decoded then discarded |
| True new EntityType (AI + animations) | Client registry closed | Exactly 30 synchronized registries; EntityType is not among them |
| New MenuType / BlockEntityType | Same | Same |
| Custom keybinds | No packet | KeyMapping = hardcoded client code |
| Pixel-perfect HUD | No packet | Declarative workarounds only |
| Dynamically modify a vanilla block's rendering | Static only | Resource pack = static; no rendering packet |
| Pack > 250 MB | Hardcoded | `MAX_PACK_SIZE_BYTES = 0xFA00000` (but unlimited pack stacking) |
| Client crash via fake KnownPacks | DoS vector, not a feature | `findAndLoadFromResource` → IllegalStateException → crash — to be documented as a risk, never exploited |

## 2.7 Quantified assessment of Level 0

Cross-referencing the A-I classification (chapter 1) with the mechanisms above:

```text
Modding use-case coverage ≈ 85%

  ✓ Custom items/blocks/furniture      ✓ Form GUIs + menus
  ✓ Sounds/music                       ✓ Post-process shaders
  ✓ Holograms/3D animations            ✓ Data-driven worldgen
  ✓ RPC + cross-server state           ✓ Bullet-time / cinematics

  ✗ Keybinds / custom input
  ✗ Pixel-perfect HUD
  ✗ True new AI entities / custom geometric rendering
  ✗ Any client transformation (zoom, HUD tweak, engine)
```

The remaining 15% requires reaching client bytecode — the subject of chapters 3 and 4.

## Chapter 3 — The JVM level: classloaders, agents and transformation

The Level 0 wall being structural (no packet carries code), the question becomes: **what is the smallest client surface that allows the server to inject behavior?** This chapter descends to the Java platform.

## 3.1 What the JVM offers beneath Minecraft

| Mechanism | What it allows | Constraint |
|---|---|---|
| `ClassLoader.defineClass()` | Create a class from bytes | Requires an anchor point already present client-side |
| `URLClassLoader` / child classloader | Load entire JARs hot | Same |
| `java.lang.instrument` (agent) | `premain` (before the app) + ClassFileTransformer | `-javaagent:` flag at launch |
| `retransformClasses` | Modify **already loaded** classes | No new method/field/superclass; method body rewrite only |
| `redefineClasses` | Brutal replacement | Same restrictions + instability |
| JVMTI (native) | Everything, including breakpoints | Outside portable JVM — ignored here |
| Module system (JPMS) | Strong compartmentalization | Minecraft does not use it strictly |

Crucial fact: **Minecraft imposes none of its own protections**. The vanilla client runs on a standard JVM, without game class signatures, without strict JPMS, without load control. Whoever controls the launch controls everything — and "controlling the launch" can mean *a single small generic agent*, not a game fork.

## 3.2 The transformation time window

The central lesson of Fabric/Mixin: transformations must apply **before game classes are loaded**. After the fact, only partial retransformations (method bodies) remain possible.

```text
JVM launch
   ↓ premain (agent)          ← UNLIMITED window: transform anything
   ↓ game classes loaded
   ↓ game main()
   ↓ runtime                  ← SHRUNK window: addClasses ✓, partial retransform ⚠
```

An agent installed at launch (once, generic, version-agnostic) therefore opens the full window: it can register a transformer for game classes, and the server can send it **which classes to apply which transformations to** — at first load of each class.

## 3.3 RemoteClassLoader: the remote loading architecture

```text
findClass("com.sdm.mod.HoverBoots")
   ↓ local cache miss
   ↓ server request (transport channel of choice)
   ↓ signed bytecode received
   ↓ defineClass()
   ↓ class available in the game
```

Technically trivial for the JVM — **the difficulty is not loading, it is anchoring the first entry point**. The vanilla client never calls `findClass` on a remote server: you need either an agent at launch (Level 3), or divert an existing vanilla mechanism (chapter 4, Levels 1-2).

## 3.4 The sandbox verdict: there is no strong cage left in the JVM

- **JEP 451**: Security Manager deprecated for removal.

- **JEP 486** (verified on openjdk.org today): "Permanently Disable the Security Manager" — the Security Manager is **dead since JDK 24**.

Consequence: strong memory sandboxing of streamed code no longer exists on the standard JVM. The realistic strategies, by decreasing strength:

1. **Process isolation** (dedicated process + IPC) — strong but architecturally heavy;

2. **Classloader isolation + process-level network filtering** (firewall rules from the launcher/runtime);

3. **Declarative permissions + signature + reputation** — the "application platform" model (like app stores): strong on provenance, not on execution.

The report retains 3 for the target architecture, with 1 as a hardening option.

## 3.5 Why Mixin exists — and what can be reduced

Mixin solves three problems: (a) historical obfuscation (resolved since 1.17+: Mojang JARs keep readable names), (b) injection point stability across versions, (c) transformation ergonomics. A reduced alternative for SDM:

- **Transformation recipes**: compact descriptions (JSON) — target class, method, injection point, handler module. Equivalent of a Mixin subset: `@Inject`-like (inject a call at method start/end), `@Redirect`-like (replace a call), `@ModifyArg`-like. No @Shadow/spy complexity: SDM modules access through a versioned reflection helper.

- **Version targeting**: each recipe references the exact client version (e.g. `26.2`, `26.3-snapshot-7`). The server maintains a recipe folder per version — it absorbs the mapping update cost, not the user.

- **Static fallback**: if no recipe exists for the client version, the server degrades cleanly (Level 0 only) and shows limitations via Dialog — never a crash.

## 3.6 Non-Minecraft precedents (chapter 28 of the brief)

| System | Applicable precedent |
|---|---|
| **Garry's Mod** | AddCSLuaFile: the server sends client Lua to joining players — normalized for 15+ years as the "server is the source of client content" model |
| **FiveM / GTA** | Resource streaming: scripts + assets streamed by the server, per-resource sandbox, signed manifest |
| **Roblox** | Client = universal runtime; all content comes from servers; catalog business model |
| **Browser / WebAssembly** | Universal runtime + sandbox + compiled bytecode streaming — the exact analogue of "LinkAgent + modules" |
| **Steam workshop / live games** | Frictionless content distribution |
| **Stadia / cloud gaming** | Ultimate thin client — not retained (bandwidth, latency) but theoretical bound of the axis |
| **OSGi / IDE plugins** | Hot load/unload of bundles, dependencies resolved by the platform |
| **Nadeshiko** (experimental MC JavaAgent, GitHub ★1) | Proof of concept that "javaagent + runtime mixins" is an explored path — immature but validating |

The SDM thesis is therefore not exotic: it is the standard model of live platforms (GMod, FiveM, Roblox, web) **applied to Minecraft**, where nobody has industrialized it yet for Java Edition.

## 3.7 Hot-loading: what the JVM really allows in-game

Brief scenario — "player connected, server activates Mod X":

| Operation | Possible? | Mechanism |
|---|---|---|
| Add classes | ✓ | New child classloader + defineClass |
| Load a complete module | ✓ | Same + dependency resolution |
| Modify already loaded classes | ⚠ Partial | retransformClasses: method bodies only |
| Add method/field to an existing class | ✗ | Fundamental JVMTI limit |
| Unload a module | ⚠ Partial | Orphaned classloader → GC; linked game classes = residual |
| Isolate a faulty module | ✓ | Separate classloader + kill switch + dedicated thread |

Retained architecture: **modules loaded in dedicated classloaders + interface hooks** (game classes calling interfaces loaded in the runtime root classloader — a pattern already proven by IDE/OSGi hot-reload). Deep transformations remain reserved for the pre-main / first-load window; hot, we compose additions.

## Chapter 4 — Escalation: Levels 0 → 6, measured

Method: each level is introduced only when the previous level is proven insufficient. Each level is measured against the brief's criteria: size, modified classes, dependencies, installation, maintenance, compatibility, security, capabilities gained.

## 4.0 Decision diagram

```text
LEVEL 0 — Strict vanilla (85% of use cases)
   │  wall: client code, entity types, input, HUD, deep rendering
   ↓
LEVEL 1 — Existing mechanisms pushed to their limits (nothing installed)
   │  wall: CustomPayload 1 MB DISCARDED (DiscardedPayload); no execution hook
   ↓
LEVEL 2 — Small one-off bootstrap (batch/script) that adds -javaagent
   │  wall: must be re-executed per profile; agent discovery unsolved
   ↓
LEVEL 3 — LinkAgent: permanent generic javaagent (~300 KB, once)
   │  coverage ≈ 98%; wall: the most extreme "engine rewrite" G mods
   ↓
LEVEL 4 — Specialized launcher (full installation, runtime auto-update)
   │  wall: no technical wall — a UX wall (installing a launcher)
   ↓
LEVEL 5/6 — Patched client / fork — REJECTED: crushing maintenance cost,
             excluded from the SDM model (remain useful as research fallbacks)
```

## 4.1 Level 0 — Strict vanilla

Already covered in chapter 2. Measurements:

| Criterion | Value |
|---|---|
| Client size | 0 KB |
| Files / classes modified | 0 / 0 |
| Installation | None |
| Maintenance | None (everything lives server-side) |
| Compatibility | All versions (protocol mechanisms stable since 1.20.2+) |
| Security | Same as vanilla (optional signed packs, acceptance prompt) |
| Coverage | ≈ 85% of use cases |

## 4.2 Level 1 — Existing mechanisms, pushed to the limit

Without installing anything, a server can still attempt:

- **Handshake steganography**: 255-char pre-auth hostname (Velocity/Bungee pattern) — useful for routing/negotiation, not execution.

- **CustomPayload 1 MB S→C**: decoded by the client... then discarded (`DiscardedPayload`). Bandwidth/CPU only. **Confirmed as a dead vector for pure vanilla.**

- **Fake KnownPacks**: crashes the client (DoS). Documented as an attack, never as a capability.

- **Discreet C→S exfiltration**: SetTestBlockPacket.message (32,767 chars, op-level required).

Verdict: Level 1 adds peripheral channels, **no execution capability**. The wall is confirmed: without an execution anchor, nothing crosses.

## 4.3 Level 2 — One-off bootstrap (without launcher)

The user (or a script provided by their server) executes **once** a lightweight installer that:

1. Copies `linkagent.jar` (~300 KB) to a folder;

2. Registers `-javaagent:linkagent.jar` on the Minecraft profile (`launcher_profiles.json` / third-party profiles);

3. Touches nothing else — the Minecraft client remains the intact official binary.

Measurements:

| Criterion | Value |
|---|---|
| Size | ~300 KB |
| Modified MC classes | 0 (the agent attaches at launch, does not modify files) |
| Installation | 1 double-click + confirm (official/third-party launchers re-read the profile) |
| Maintenance | Low; the agent is generic (no MC version inside) |
| Risks | Launchers sometimes rewrite profiles → occasional reinstallation; non-standard JVM argument may be ignored |
| Coverage | ≈ 98% (see Level 3) |

Level 2 limit: discovery. A player discovering an SDM server must know the bootstrap is needed. Levels 3/4 make discovery automatic.

## 4.4 LEVEL 3 — LinkAgent: the permanent generic agent

**The central result of the report.** A single javaagent, installed once (via Level 2 or 4), that permanently transforms the client into a universal terminal:

| Criterion | Value |
|---|---|
| Size | ~300 KB (ASM ~130 KB + codec + crypto + core API) |
| Content | premain, ClassFileTransformer, RemoteClassLoader, signed cache, SDMP negotiation, permissions |
| What it does NOT contain | No mapping, no mod logic, no content — everything comes from the server |
| Installation | Once, by bootstrap or launcher |
| Compatibility | All MC versions: recipes/mappings target precise versions and are streamed per server |
| Security | Mandatory Ed25519 signature, declarative permissions, revocation |
| Coverage | ≈ 98% — everything except the most radical engine rewrites |

Agent responsibilities:

1. **Listen to the handshake** (login plugin message / cookie / hostname) and negotiate SDMP capabilities;

2. **Download and verify** modules + recipes + packs (Ed25519 signature, versioned disk cache);

3. **Apply recipes** to game classes at first load (full window);

4. **Load modules** into child classloaders with permissions;

5. **Expose the runtime API** (events, rendering, input, HUD, virtual registries) to modules;

6. **Age gracefully**: unknown MC version → missing recipes → clean Level 0 fallback, never a crash.

Why ~300 KB is enough: the agent embeds ASM (transformation), an HTTP(S) client (transport), Ed25519 (native JCA), and ~40 runtime classes. All version-specific knowledge lives server-side — THIS is the reversal: the cost of Minecraft updates moves from the player to the server.

## 4.5 Level 4 — Specialized launcher

A launcher (or extension of a third-party launcher: Prism, MultiMC, Modrinth App, ATLauncher...) integrates the LinkAgent natively: zero-click installation, automatic SDM server discovery, shared cross-server cache, runtime updates.

- Advantages: maximum UX, simplified distribution, security brand (signatures verified by the launcher).

- Drawback: dependence on the launcher ecosystem — against the goal of maximum independence. Treated as an **optional accelerator**, not a foundation.

## 4.6 Levels 5-6 — Patched client / fork

Rejected as target architecture: every Minecraft version = re-patch, re-distribute (legal gray zones), re-maintain a fork. The LinkAgent makes these levels unnecessary — they survive only as isolated research work (e.g. rendering studies).

## 4.7 The golden table: capabilities × levels (answer to §25 of the brief)

Legend: ✓ native · ~ approximable · ✗ blocked

| Capability | L0 | L3 (agent) | Note |
|---|---|---|---|
| Custom item (appearance/behavior) | ✓ | ✓ | CMD + ITEM_MODEL; server behavior |
| Custom static block/furniture | ✓ | ✓ | Polymer virtualization + Display |
| True custom AI entity | ~ | ✓ | L0: server puppet; L3: virtual type + module rendering |
| Form/menu GUIs | ✓ | ✓ | Dialog + fake menus |
| Pixel-perfect HUD | ~ | ✓ | L0: bossbar/scoreboard; L3: overlay module |
| Custom rendering (geometry/shader) | ~ | ✓ | L0: PostEffects + Display; L3: rendering module |
| Keybind input | ✗ | ✓ | Input module via agent |
| Client mixins (zoom, tweaks) | ✗ | ✓ | Transformation recipes |
| Custom packets | ~ | ✓ | L0: NBT channels; L3: native SDMP channel |
| Custom worldgen | ✓ | ✓ | Data-driven + synchronized registries |
| Hot module load in-game | ~ | ✓ | L0: hot packs/dialogs; L3: hot classes |
| Multi-version | ✓ | ✓ | Recipes targeted per version |
| Direct Fabric/Forge compat | ✗ | ✗→~ | Partial adapter (chapter 8) |

## 4.8 Comparison of installation models (§17 of the brief)

| Model | Simplicity | Security | Feasibility | UX |
|---|---|---|---|---|
| A — zero installation (Level 0) | ★★★★★ | ★★★★★ | Proven | "Launch, join, play" |
| B — single button (Level 2 bootstrap) | ★★★★☆ | ★★★★☆ | Proven (profile files) | 1 click once |
| C — dedicated launcher (Level 4) | ★★★☆☆ | ★★★★★ | Proven | 1 app installation |
| D — automatic association | ★★★★☆ | ★★★☆☆ | Depends on third-party launchers | Ideal if adopted |
| E — permanent bootstrap (LinkAgent) | ★★★★★ | ★★★★☆ | **SDM target** | 1 click once, all servers |

**Verdict**: E (via B for initial installation) is the optimum — zero action for all SDM servers after the first installation.

## Chapter 5 — Server architecture: the modding platform

The server becomes the platform. This chapter defines the complete server-side architecture, piece by piece.

## 5.1 Overview

```text
                        SDM PLATFORM (server)
┌──────────────────────────────────────────────────────────────────┐
│  SDM Gateway (Velocity proxy)  ── SDMP handshake, routing,       │
│                                 cookies, inter-server transfer    │
├──────────────────────────────────────────────────────────────────┤
│  SDM Server Core (Canvas-Paper plugin)                           │
│  ├── Mod Runtime          ── server module loading               │
│  ├── Mod Loader           ── discovery, dependency graph         │
│  ├── Content Manager      ── virtual registries (items/blocks)   │
│  ├── Registry Broker      ── sync of the 30 vanilla registries   │
│  ├── Virtualization Layer ── polymer-like: virtual blocks        │
│  ├── Network Manager      ── SDMP channels, Level 0 fallback     │
│  ├── Pack Studio          ── on-the-fly resource pack generation │
│  │   (textures/models/sounds/shaders/fonts)                      │
│  ├── Dependency Manager   ── resolution + pins + conflicts       │
│  ├── Module Compiler      ── server JAR → client modules         │
│  │   (client-side extraction + recipes per MC version)           │
│  ├── Recipe Store         ── mixins targeted by version          │
│  ├── Signature Service    ── Ed25519, manifest, revocation       │
│  └── Telemetry            ── aggregated client errors            │
└──────────────────────────────────────────────────────────────────┘
         │                                    │
         ▼                                    ▼
   Vanilla clients (Level 0)          LinkAgent clients (Level 3)
   packs + dialogs + display          modules + recipes + packs
```

## 5.2 The software layers

### Gateway (Velocity)

The network entry point handles the SDMP handshake before login: hostname reading (255 chars) for routing/capabilities, identification cookies, inter-server transfer. It also determines the client level (vanilla / agent) and composes the adapted response.

### Server Core

A plugin (or a light fork) on the game server (Canvas/Paper/Folia) implementing the runtime:

- **Mod Runtime**: hierarchical classloader, lifecycle (load → start → stop → unload), per-module isolation;

- **Virtual registries**: the Content Manager allocates virtual IDs (custom blocks on vanilla supports), the Registry Broker synchronizes the 30 vanilla registries when useful, the Virtualization Layer produces visual equivalents;

- **Pack Studio**: compiles module assets into resource packs, hash + signature, hot push; versioned by MC (pack format 95 / data 115 in 26.3-snapshot-7);

- **Module Compiler**: each mod written once (common code) is derived into: server module (logic), client module (presentation), recipes (transformations), packs (assets), data (loot/worldgen). The server is the module factory.

### Recipe Store

The transformation recipe bank, indexed by exact MC version. Each entry: target class (Mojang-mapped name), method, injection point, handler module, expected target class hash (clean failure on mismatch). **This is where the cost of Minecraft updates lives** — borne by the platform operator, not the players.

### Signature Service

All streamed artifacts (modules, recipes, packs) are Ed25519-signed. The manifest describes modules, versions, dependencies, requested permissions, hashes. Revocation (CRL/OCSP-like) can kill a compromised module.

## 5.3 The server→client module lifecycle

```text
1. Developer publishes ─► SDM DevKit (universal API + adapters) ─► platform
2. Platform compiles   ─► server module + client module + recipes + packs + data
3. Player joins        ─► Gateway handshake: level detected (L0 or L3)
4a. L0: packs streamed, dialogs, display entities, NBT channels
4b. L3: signed manifest ─► agent verifies ─► downloads ─► applies recipes
    (game classes not yet loaded) ─► loads modules ─► runtime ACK
5. In game             ─► server events → modules via SDMP; hot-swap possible
```

## 5.4 Universal API (§24 of the brief)

The target API is loader-independent:

```text
ServerModAPI (the contract)
  Blocks · Items · Entities · World · Networking · Events · GUI
  Rendering · Input · Commands · Registries · Data · Resources · Permissions
Adapters (implementations)
  Vanilla-protocol (Level 0)   ── dialogs/packs/display/CMD
  LinkAgent (Level 3)          ── full SDMP modules
  Fabric / Forge / NeoForge     ── native server-side execution
```

A mod written against ServerModAPI works on any platform with an adapter — including a Fabric server streaming to vanilla clients through the Vanilla-protocol adapter. This replicates the role of Architectury API (93.6M downloads) but extended to distribution.

## 5.5 Mod-per-world, mod-per-player, hot operations (§14-15 of the brief)

- **Mods per world**: the Gateway routes per server/world; each world streams its manifest. Cookies carry progression between worlds.

- **Mods per player**: the server maintains a capability profile per player (module permissions) — a VIP player sees optional modules another does not.

- **Hot-load**: activating a module for connected players = push delta manifest → the agent loads in a fresh classloader (class additions always possible hot) → ACK. Deep transformations require PLAY→CONFIG re-entry (vanilla mechanism) for re-synchronization — the player stays connected.

- **Hot-unload**: deactivation + isolation (the module stops being called, orphaned classloader becomes GC candidate). The JVM does not guarantee immediate unload — documented as a limit.

## 5.6 Serving multiple Minecraft versions (§23 of the brief)

The Recipe Store + Pack Studio version everything: recipes per exact MC version, packs per pack format. The client module (code) is compiled by the platform against each target version's mappings — the server maintains a build matrix. The agent knows nothing about versions. Result: a player on 26.2 and a player on 26.3 on the same server each receive their version's artifacts — the multi-version promise holds by construction, at the cost of server-side mapping maintenance.

## 5.7 Performance (§20 of the brief)

| Dimension | Analysis |
|---|---|
| Client | ~300 KB agent + modules loaded on demand; RAM controlled by lazy-loading |
| Network | Signed disk cache (hashes) — a module is downloaded once; differential packs |
| Client CPU | Simple modules (HUD, event hooks) negligible; custom rendering = normal GPU budget |
| Server | Virtualization + pack generation = amortized costs (compile once, cache per version) |
| Latency | Nothing interactive crosses the network beyond what exists: local input → local module, server events → modules via SDMP (asynchronous) |
| First launch | Manifest + modules of a light modpack ≈ 20-80 MB; 1-10 s on fiber |
| Comparison | Classic modpack = 0.5-2 GB installed locally, before even playing |

## 5.8 Capability matrix (§9 of the brief)

| Capability | Fabric | Forge | NeoForge | SDM L0 | SDM L3 |
|---|---|---|---|---|---|
| Blocks | ✓ | ✓ | ✓ | ✓ (virtual) | ✓ |
| Items | ✓ | ✓ | ✓ | ✓ | ✓ |
| Entities | ✓ | ✓ | ✓ | ~ (visual) | ✓ |
| GUI | ✓ | ✓ | ✓ | ✓ (dialogs/menus) | ✓ |
| Rendering | ✓ | ✓ | ✓ | ~ (post/GPU) | ✓ |
| Networking | ✓ | ✓ | ★ | ✓ | ✓ |
| Registry | ✓ | ✓ | ★ | ✓ (30 sync) | ✓ |
| Worldgen | ✓ | ✓ | ✓ | ✓ | ✓ |
| Events | ✓ | ✓ | ★ | ✓ (server) | ✓ (client+server) |
| Client mixins | ✓ | partial | partial | ✗ | ✓ (recipes) |
| Client hooks | ✓ | ✓ | ★ | ✗ | ✓ |
| Bytecode | ✓ | ✓ | ★ | ✗ | ✓ (agent) |
| Dynamic loading | ~ | ~ | ~ | ~ (packs) | ✓ |
| Distribution | ✗ (manual install) | ✗ | ✗ | ✓ | ✓ |

The decisive line: **Distribution** — the only capability no loader offers and SDM brings natively.

## Chapter 6 — SDMP: the protocol and the LinkAgent

## 6.1 Protocol philosophy

SDMP (Server-Driven Modding Protocol) layers two tiers:

```text
Minecraft protocol (handshake/login/config/play)
        +
SDMP (negotiation, manifests, modules, recipes, events, lifecycle)
```

Two transport modes depending on client level:

- **Vanilla transport (Level 0)**: SDMP builds on existing channels — resource pack prompt, cookies, CustomClickAction, CustomQuery (login plugin messaging), handshake hostname. The vanilla client "speaks SDMP" without knowing it.

- **Agent transport (Level 3)**: a dedicated binary channel on CustomPayload (channel `sdm:main`, 1 MB/packet S→C) + parallel HTTP(S) for large artifacts (the agent client does not download modules through the game socket — it uses HTTPS with throwaway tokens).

## 6.2 Negotiation sequence

```text
Client                                    SDM Gateway
  │── ClientIntentionPacket (hostname 255) ──►│  routing + SDM hint
  │                                          │  (velocity-style)
  │◄─ CustomQuery (login) "sdm:hello" ───────│
  │── QueryAnswer {agentVersion, caps} ─────►│  (agent only; vanilla = timeout → L0)
  │                                          │
  │  … login / configuration …               │
  │◄─ StoreCookie "sdm:session" (5 KB) ──────│  session token, platform version
  │◄─ ResourcePackPush (signed base packs) ──│  (BOTH modes)
  │◄─ [agent] Signed SDMP manifest ──────────│  modules, recipes, permissions
  │── [agent] ACK + hash set ───────────────►│
  │◄─ [agent] Artifacts (HTTPS 206 range) ───│  module JARs + recipe JSON
  │                                          │
  │  … play …                                │
  │◄─► SDMP events (channel sdm:main) ───────│  bidirectional runtime hooks
```

Level decision **before play**: no answer to `sdm:hello` in login/config classifies the client L0 — the server streams no modules, it composes the Level 0 experience. Never a visible error.

## 6.3 Manifest

Example signed manifest (condensed JSON):

```json
{
  "platform": "coco-station",
  "platformVersion": "1.7.0",
  "mcVersions": ["26.2", "26.3-snapshot-7"],
  "modules": [
    {
      "id": "cocomagic",
      "version": "3.2.1",
      "perms": ["render:overlay", "input:keybinds", "net:sdm"],
      "artifacts": {
        "server": "cocomagic-server-3.2.1.jar",
        "client": "cocomagic-client-3.2.1+26.2.jar",
        "recipes": "cocomagic-recipes-26.2.json",
        "pack": "cocomagic-pack-3.2.1.zip"
      },
      "depends": [{"id": "sdm-api", "range": ">=2.0"}],
      "sha256": "…", "sigEd25519": "…"
    }
  ],
  "revocation": "https://registry.sdm.dev/crl",
  "minAgent": "1.0.0"
}
```

## 6.4 The LinkAgent: anatomy (~300 KB)

```text
LinkAgent
├── premain                    ── attaches before the game, registers the transformer
├── TransformerGateway         ── applies recipes at first load
│     (ClassFileTransformer)      of classes; no-op until anything is received
├── RemoteClassLoader (root)   ── loads modules, parent = game classloader
├── CacheManager               ── disk: modules + packs per (server, version)
├── Verify                     ── Ed25519 + SHA-256 + platform key pinning
├── PermissionBroker           ── applies the perms manifest (consent UI)
│   └── ConsentUI              ── vanilla-like screen (via Dialog or injected screen)
├── SDMPClient                 ── game transport + artifact HTTPS
└── RuntimeBridge              ── exposes the API to modules (events, render, input, HUD)
```

None of these classes contains the slightest Minecraft mapping. The agent is **version-agnostic**: a recipe's target class is designated by its Mojang-mapped name ("net.minecraft.client.renderer.GameRenderer"), read through the agent's reflection API. Unknown MC version → missing recipes → Level 0 fallback → the game still works.

## 6.5 Runtime events (extracts)

| Event | Direction | Payload |
|---|---|---|
| `sdm.hello` / `sdm.ack` | C→S | agent version, caps, hash set |
| `sdm.manifest` | S→C | signed manifest (delta if cached) |
| `sdm.module.load` / `.unload` | S→C | in-game hot-load |
| `sdm.event.*` | S→C | encapsulated game events to modules |
| `sdm.input.*` | C→S | module keybinds → server (if permission granted) |
| `sdm.render.*` | S→C | overlay render orders |
| `sdm.error` | C→S | module error report (telemetry) |

## 6.6 Hot-load sequence (§15 of the brief)

```text
Player in game, server activates "CocoMagic"
  ↓ sdm.module.load {id, version, artifacts, sig}
  ↓ agent: verified manifest delta (Ed25519, perms already consented → silent)
  ↓ new perms? → ConsentUI (otherwise transparent)
  ↓ HTTPS download (cache check by hash)
  ↓ new ModuleClassLoader (class additions = always OK hot)
  ↓ if recipes needed on already loaded classes → server requests
    PLAY→CONFIG re-entry (StartConfiguration) → retransform → back to game
  ↓ module ACK → visible to the player
```

The worst case (retransform needed) stays transparent: the player sees a reload screen of ~2-5 s, equivalent to a resource pack change — never a crash.

## 6.7 Graceful degradation — the golden rule

The entire design obeys one rule: **a client without an agent, or an agent without recipes for its version, must always be able to play at Level 0**. The server carries the degradation intelligence: same content, expressed in dialogs/packs/display when modules are unavailable. This rule guarantees vanilla/agent coexistence on the same server and eliminates the "server inaccessible because missing mod" syndrome.

## Chapter 7 — Security: the trust model

The architecture introduces a server that provides code to the client. This is the major problem identified by the brief — treated here at the same level as feasibility.

## 7.1 The threat in its clearest form

A player joining a malicious server with the LinkAgent implicitly accepts executing code signed by that server. Without guardrails, this is RCE-as-a-service. The model must account for:

1. **Server→client RCE**: the streamed module is full JVM code (disk, network, process access — JEP 486 killed the strong sandbox);

2. **Minecraft session theft**: a module can read session tokens;

3. **Persistence**: disk cache = code that stays;

4. **Exfiltration**: unrestricted network modules;

5. **Supply chain**: compromised popular module;

6. **Client DoS**: giant manifests, broken recipes, corrupt packs.

## 7.2 The retained model: signature + permissions + consent + revocation

This is the "application platform" model (app stores, FiveM Signed Scripts, web code-signing) — the only realistic one on a modern JVM:

```text
Server
  ↓ Ed25519 signed manifest (platform key)
  ↓ Module registry (provenance: platform, signing author, or local server)
User Trust
  ↓ First SDM server of a new key → consent screen
  ↓ (key fingerprint + requested permissions, "install an app" style)
Module Download
  ↓ Ed25519 + SHA-256 verification per artifact + pinning
Relative Sandbox / Runtime
  ↓ PermissionBroker: granted perms = only active ones
  ↓ Local-server modules: restricted network sandbox by default
Execution
  ↓ Error telemetry + runtime kill switch (CRL)
```

### Declarative permissions (extract)

| Permission | Effect |
|---|---|
| `net:sdm` | Speak SDMP to the current server |
| `net:external` | Arbitrary outgoing network (maps...) |
| `fs:cache` | Dedicated cache write |
| `fs:profile` | Profile/screenshot read (dedicated consent) |
| `render:overlay` | HUD/overlay |
| `input:keybinds` | Register keybinds |
| `sys:process` | Subprocesses (never by default — dedicated screen) |

### Structural rules

- **Never unsigned code** executed; invalid signature = refusal + report.

- **Principle of least privilege**: a module only gets its declared perms; the PermissionBroker blocks the rest at the RuntimeBridge level.

- **Minecraft ToS**: distributing the Minecraft client itself remains forbidden; SDM never distributes game files — the agent attaches to the official client the player already owns (Mojang usage guidelines: prudence requires not redistributing Mojang assets, which SDM avoids by construction).

- **Kill switch**: the CRL lets the platform revoke a module on all clients within seconds.

- **Reputation**: telemetry aggregation (crash rate, reports) shown in the consent screen.

## 7.3 What this model does not protect (technical honesty)

- A signed malicious module whose perms include `net:external` can exfiltrate data the game makes accessible. Mitigations: granular consent, reputation, revocation — not absolute prevention.

- Memory isolation does not exist (JEP 486). A compromised module within a careless platform = full code on the machine. The defense is organizational (signatures, audit) — identical to "installing a CurseForge mod", but with revocability.

- The "first join" vector: if the user blindly accepts. Clear consent UX = the best weapon (key fingerprint, perms, reputation, source).

## 7.4 Comparison with known models

| System | Model | Lesson for SDM |
|---|---|---|
| Garry's Mod / FiveM | Lua sandbox / signed scripts | Server→client streaming is socially accepted for 15 years |
| Roblox | Strict Luau sandbox | Proof that a universal runtime with strong sandbox works for hundreds of millions of users |
| Browsers | Multi-process sandbox + origin | The gold level; out of JVM reach, but inspires per-origin (server) consent |
| Fabric/CurseForge | "Download and hope" | No revocation, no perms — SDM is strictly better equipped |
| Java Web Start | Deprecated then dead sandbox | The light JVM sandbox is a dead end (JEP 451/486) |

## 7.5 Hardening option: process isolation

For demanding platforms (competition, real prizes), a hardened mode where the agent runs modules in a **satellite process** (separate JVM, shared-memory IPC + local sockets): a compromised module touches neither the game process nor the session. Cost: runtime complexity + IPC latency. Positioned as an enterprise option, not a foundation.

## Chapter 8 — Ecosystem compatibility, test cases, PoC and roadmap

## 8.1 Ecosystem compatibility (§8 of the brief) — the 5 levels

| Level | Description | Feasibility | Effort |
|---|---|---|---|
| 1. Reproduce features | Rewrite features in ServerModAPI | ✓ Immediate | Per mod |
| 2. Compatible API | Offer Fabric-like surfaces (events, registries) | ✓ | Medium |
| 3. Partial adapter | Load parts of mods (server logic, data, assets) | ✓ | Medium-high |
| 4. Direct loading | Run Fabric/Forge mods as-is | Partial — server-side mods only | High |
| 5. Full ecosystem runtime | Run almost the entire catalog | ⚠ Theoretical — client mixins target the local client, not a streamed runtime | Very high |

Honest reading: levels 1-3 are realistic and cover the majority of the value. Level 4 works for purely server-side mods (the SDM server module can embed and execute the Fabric server-side JAR via adapter). Level 5 hits a structural truth: mods with deep client mixins (Sodium, Iris...) assume a locally modded client — SDM will never serve them without rewriting, unless the LinkAgent implements a complete loader (which amounts to universally reinstalling Fabric: technically possible, contrary to the minimal spirit).

**Hydraulic as precedent**: letting Bedrock clients join modded Java servers (architecture translation) proves that translating custom content between heterogeneous runtimes is feasible — GeyserMC already exploits this for Geyser (5752 stars). SDM is the Java↔Java-vanilla generalization of this pattern.

## 8.2 The 12 test cases (§26 of the brief)

| # | Case | Required level | SDM implementation |
|---|---|---|---|
| 1 | Simple item | 0 | CMD + ITEM_MODEL + pack; behavior via server events |
| 2 | Block | 0 | Virtualization (vanilla support + model); server hitbox |
| 3 | Entity | 0-3 | L0: server-driven display puppet; L3: virtual entity type + module rendering |
| 4 | Custom GUI | 0 | Dialog + inputs + CustomClickAction RPC |
| 5 | Custom packet | 0 | NBT channels (CustomClickAction 32 KB / cookies 5 KB); L3: native sdm:main |
| 6 | Complex gameplay (jobs/economy) | 0 | Pure server + dialogs + packs |
| 7 | Worldgen | 0 | Data packs + synchronized registries (BIOME, DIMENSION_TYPE) |
| 8 | Rendering | 0-3 | L0: PostEffects + Display; L3: custom rendering module |
| 9 | Input (dash keybind) | 3 | Input module (perm `input:keybinds`) + server event |
| 10 | Deep client modification | 3 | Versioned transformation recipes |
| 11 | Dense-mixin mod (zoom + HUD tweak) | 3 | Equivalent recipes + presentation module |
| 12 | Complex Forge/NeoForge mod (Create-like) | 0-3 | Compat level 1-2: ServerModAPI rewrite; L0: visual subset |

## 8.3 PoC — minimal feasibility demonstration

Goal: prove the three pillars in 4 weeks of dev.

### P1 — Pure Level 0 (week 1)

Paper/Canvas server + prototype plugin:

- 1 complete custom item (paper → visual sword via CMD) with streamed pack;

- 1 quest dialog with TextInput + CustomAll action → server RPC;

- 1 animated hologram (TextDisplay + interpolation);

- 1 progression cookie + transfer to a second server.

**Proof**: official vanilla client, zero installation, "mod-like" experience.

### P2 — Minimal LinkAgent (weeks 2-3)

- premain + transformer + 1 recipe (inject a hook into `GameRenderer` for a simple HUD overlay);

- transport: CustomPayload `sdm:main` + HTTPS for the module;

- Ed25519 signed manifest + cache + verification.

**Proof**: the server added a custom HUD to an official client without the user installing a mod.

### P3 — Hot-load (week 4)

- Activating a module in-game (fresh classloader + ACK);

- PLAY→CONFIG re-entry for an additional recipe.

**Proof**: the hot-loading promise holds on both JVM windows.

### Success criteria

A strict vanilla client plays P1 without installing anything; an agent client receives P2+P3 without crashing; N3→N0 degradation verified (agent disabled = the server falls back to dialogs/packs mode).

## 8.4 11-phase roadmap (§31.K of the brief)

| Phase | Content | Indicative duration |
|---|---|---|
| 1. Vanilla research | Complete protocol audit per version (decompilation, mechanism catalog, limits) | 2-4 wks (largely done) |
| 2. Protocol research | SDMP v0 spec (hello/manifest/events), codecs, vanilla vs agent channels | 2-3 wks |
| 3. Server prototype | Platform plugin: virtual registries, Pack Studio, L0 RPC | 4-6 wks |
| 4. Vanilla-client experiments | Complete P1 + multi-version tests + telemetry | 3-4 wks |
| 5. Minimal bootstrap | 1-click multi-launcher installer (JVM args profiles) | 2-3 wks |
| 6. Remote runtime | LinkAgent v1: premain, transformer, cache, verify | 4-6 wks |
| 7. Dynamic code loading | RemoteClassLoader + hot-load + CONFIG re-entry | 3-4 wks |
| 8. Mod API | ServerModAPI + DevKit + docs + 5 example mods | 6-8 wks |
| 9. Fabric compatibility | Fabric server-side adapter + asset/data extraction | 4-6 wks |
| 10. Forge/NeoForge compatibility | Same + level 4 feasibility study | 6-8 wks |
| 11. Production architecture | Multi-server gateway, signed registry, CRL, telemetry, hardening | ongoing |

Total to a public beta: ~9-12 months for a small team (2-3 devs), assuming phases 1-2 are already acquired.

## 8.5 Limit analysis — final table (§25)

```text
Feature → Server only? → Vanilla client? → Bootstrap? → Runtime? → Patched client?

Custom item          yes          yes                no          no         no
Custom block         yes          yes (virtual)      no          no         no
Custom AI entity     yes (logic)  puppet only        no          yes (render) no
Form GUI             yes          yes                no          no         no
Pixel-perfect HUD    no           approx (bars)      no          yes        no
Custom keybind       no           no                 no          yes        no
Geometric rendering  no           partial (post)     no          yes        no
Deep client mixin    no           no                 no          yes (recipes) equiv.
Worldgen             yes          yes                no          no         no
Arbitrary code       no           no                 —           yes (signed) equiv.
```

## 8.6 Positioning relative to existing models

| Model | Relation to SDM |
|---|---|
| Fabric/Forge/NeoForge | Complementary server-side; rivals in distribution. SDM does not replace them for deep desktop modding — it substitutes a distribution channel for the general public |
| Polymer/PolyFactory | Proof of Level 0 at scale — SDM industrializes what Polymer does as a framework |
| Geyser/Hydraulic | Proofs of heterogeneous architecture translation — SDM generalizes Java→Java-vanilla |
| ViaVersion | Precedent of multi-version by server-side translation — same philosophy of absorbing cost server-side |
| Simple Voice Chat | Proof that a mod with a client component can be massively adopted — but still requires installation: SDM removes precisely this friction |

## 8.7 General conclusion

The answer to the brief's final double question:

1. **Yes** — Minecraft can become a platform where the vanilla client serves as the execution base and the server streams capabilities: 85% today without installing anything, and ~98% with a generic ~300 KB javaagent installed once. The proofs already exist scattered (Polymer, Geyser, Dialog system, resource pack hot-swap); what is missing is the orchestration layer — protocol, runtime, security, platform — which this report specifies.

2. **The optimal smallest client modification** is neither a launcher, nor a fork, nor a mod: it is **a generic version-agnostic javaagent**, because it is the only component that can transform the client into a universal terminal while keeping the official Minecraft binary intact, and leaving 100% of version and content cost to the server.
