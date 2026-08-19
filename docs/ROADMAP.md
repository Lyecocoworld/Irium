# Irium Development Roadmap

This roadmap turns the research conclusions into concrete milestones. Each milestone has a verifiable exit criterion — no milestone is considered done without a real execution proof (log, jar, or in-game capture).

## Current state

| Piece | Status |
|---|---|
| Server plugin (consent flow, join listener, FR messages) | v0.1.0 built and pushed |
| Client agent | not started — next milestone |
| Protocol (Irium handshake) | not started |
| Module runtime | not started |

## Milestones

### M1 — Client agent skeleton (in progress)

The ~300 KB agent, built from scratch with the discipline established during the research:

- `premain` entry point, dormant by default: the agent detects whether the host process is a Minecraft client (main class + known libraries); if not, it exits immediately and touches nothing.

- An observation-only `ClassFileTransformer` that logs class loads during the startup window (no transformation yet) — this gives us the real anchoring map for M4 recipes.

- Verified on JDK 25 in the lab: dormant on non-Minecraft processes, active observation on a Minecraft-like target, space-safe path quoting.

- Exit: agent jar builds, runs dormant on any Java process, and produces a class-load log on a Minecraft-like run.

### M2 — Handshake (plugin + agent)

The plugin asks, the agent answers:

- Login-phase plugin message `irium:hello` sent by the plugin (velocity login-query pattern).

- The agent answers with its version and capabilities; silence classifies the client as vanilla (Level 0).

- The plugin reacts: dialog shown only to vanilla clients, silent full path for agent clients.

- Exit: two logs agreeing on one real connection (dev server + client).

### M3 — First streamed artifact

The trust chain, exercised end to end:

- Ed25519 keypair, manifest format (modules, versions, hashes, permissions), signed pack containing one trivial module.

- The agent verifies the signature, checks the hash, caches, and loads the module in a child classloader.

- Exit: module code executing in the client JVM, with the signature deliberately corrupted in a second run to prove refusal.

### M4 — First recipe (HUD)

The visible proof:

- One transformation recipe: inject a hook into the GUI render path (class + method targeted per Minecraft version, anchor hash checked).

- A one-line HUD drawn by the loaded module ("Irium linked").

- Exit: screenshot of a vanilla client, zero modification installed, showing the HUD — plus the graceful fallback (recipe absent = clean vanilla, no crash).

### M5 — Session sandbox

Everything the server adds disappears on disconnect:

- Modules deactivated, hooks iterating empty lists, resource packs popped, settings snapshot restored.

- Exit: log-documented sequence join → module active → disconnect → state byte-identical to vanilla.

### M6+ — Hardening and platform

Key pinning, permission broker, revocation list, Pack Studio, DevKit, Store packaging. These follow the phased plan in [`docs/research/CONCLUSION_AND_PLAN.md`](research/CONCLUSION_AND_PLAN.md) and are intentionally not scheduled until M1-M4 prove the chain.

## Engineering rules (from the validation report)

1. The lab experiments become CI non-regression tests.

2. A recipe without a clean Level 0 fallback is never merged.

3. Signature and manifest code is written test-first.

4. No deadline pressure on security milestones.

5. Every commit builds: `mvn -q package` on both plugin and agent must stay green.
