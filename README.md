<div align="center">
  <a href="https://github.com/Lyecocoworld/irium">
    <img src="docs/img/banner.svg" alt="Irium — the rare element of modding" width="880"/>
  </a>

  <br/>

  <p>
    <a href="#status"><img src="docs/img/badge-status.svg" alt="Status: in development"/></a>
    <a href="plugin/pom.xml"><img src="docs/img/badge-version.svg" alt="Irium v0.1.0"/></a>
    <a href="#compatibility"><img src="docs/img/badge-mc.svg" alt="Minecraft 26.1.2+"/></a>
    <a href="LICENSE"><img src="docs/img/badge-license.svg" alt="License ELv2"/></a>
    <a href="#technology"><img src="docs/img/badge-java.svg" alt="Java 21+"/></a>
  </p>

  <p><strong>The server handles both sides.<br/>Mods are streamed, not installed.</strong></p>
</div>

---

## The problem

Playing on a modded server today means installing a loader, an API, and every mod by hand — for each server, each pack, each update. Most players never do it. So plugin servers can't offer what modded servers offer, and the two worlds stay separate.

## What Irium is

Two parts:

- **A server plugin.** Install it like any Paper plugin. It adds a `mods/` folder to your server directory, next to `world/` and `plugins/`. Drop mods into it — that's the whole setup. Irium loads them and streams what clients need.
- **A client agent.** A small JVM agent (~300 KB) installed once, via a one-click app. It works with any launcher and any instance. After that, every Irium server works automatically.

Mods keep the usual loader rules: Irium-native mods first, Fabric / Forge / NeoForge support later through a gateway — one loader at a time, no cross-loading.

## How it works

**Player side.** Join an Irium server and you get a simple prompt, like the resource pack prompt: *do you want the full version?* Accept, and modules load into your session — interfaces, HUD, rendering, gameplay. Decline, and you play the baseline. On disconnect, everything the server added disappears; the client is vanilla again.

**Server side.** To stream mods, a server must be enrolled with the Irium platform and hold a valid authentication key. An unenrolled server or an invalid key gets nothing — the agent rejects it. Every module is signed and verified before it runs.

```text
        vanilla client
              │
              ▼
    irium agent · ~300 KB · installed once
              │   enrollment · signed manifest · verified
              ▼
    irium server ─── runtime ─ mods ─ api ─ content
              │
              ▼
    hud · interfaces · rendering · input · gameplay
    appears in the session · gone on disconnect
```

## Compatibility

| | |
|---|---|
| Server | Paper / Canvas / Folia — standard plugin |
| Client | any launcher, any instance · agent required for the full version |
| Mods | Irium-native via drag and drop · Fabric / Forge / NeoForge via gateway (planned, one loader at a time) |
| Without agent | the server plays as a normal plugin server |

## Status

In development — phase 0. Nothing is production-ready yet.

| Milestone | State |
|---|---|
| Server plugin: join prompt, session channel | done |
| Agent: live HUD injection (PoC) | next |
| Handshake, signed module streaming, session sandbox | planned |
| Native mod API + devkit | planned |
| Fabric / Forge / NeoForge gateway | last |

## Research

This project builds on five years of technical research: how far a vanilla client can be pushed before it needs modification. Full documents (French) in [`docs/research/`](docs/research/):

| Document | Contents |
|---|---|
| [`RAPPORT_SDM`](docs/research/RAPPORT_SDM.md) | Escalation levels, classloaders, JVM agents, protocol, server architecture, security |
| [`RAPPORT_SDM2`](docs/research/RAPPORT_SDM2.md) | Injection rails, ghost versions, hot attach, distribution |
| [`VALIDATION_SDM`](docs/research/VALIDATION_SDM.md) | Lab falsification on JDK 25: premain, retransform, hot attach |
| [`CONCLUSION_ET_PLAN`](docs/research/CONCLUSION_ET_PLAN.md) | Findings, methodology, MVP roadmap |

## Technology


|---|---|
| Server | Paper / Canvas / Folia plugin, Java 21 |
| Agent | JVM agent (startup + hot attach), ASM, Ed25519 |
| Security | enrolled servers, signed modules, revocation |

## Structure

```text
irium/
├── plugin/          server plugin (Paper/Canvas)
├── docs/
│   ├── research/    research documents (md)
│   ├── brand/       visual identity (html)
│   └── img/         svg assets (banner, badges)
└── LICENSE          Elastic License 2.0
```

## License

Elastic License 2.0 — see [LICENSE](LICENSE). The code is free to use, study, modify, and redistribute; the only restrictions: don't offer it as a hosted service, don't remove or obscure licensing notices, and don't circumvent the license key. Independent project, not affiliated with Mojang Studios or Microsoft. Minecraft is a trademark of Mojang Studios.
