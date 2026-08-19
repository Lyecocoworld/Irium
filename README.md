<div align="center">
  <a href="https://github.com/Lyecocoworld/irium">
    <img src="docs/img/banner.svg" alt="Irium — the rare element of modding" width="880"/>
  </a>

  <br/>

  <p>
    <a href="#status"><img src="docs/img/badge-status.svg" alt="Status: in development"/></a>
    <a href="plugin/pom.xml"><img src="docs/img/badge-version.svg" alt="Irium v0.1.0"/></a>
    <a href="#compatibilité"><img src="docs/img/badge-mc.svg" alt="Minecraft 26.1.2+"/></a>
    <a href="LICENSE"><img src="docs/img/badge-license.svg" alt="License MIT"/></a>
    <a href="#technologie" ><img src="docs/img/badge-java.svg" alt="Java 21+"/></a>
  </p>

  <p><strong>Le serveur devient la plateforme de modding.<br/>Le client reste vanilla.</strong></p>
</div>

---

## Vue d'ensemble

Installer des mods Minecraft aujourd'hui impose au joueur :

```text
fabric installer  →  api  →  14 mods  →  versions  →  dépendances  →  mods/  →  reboot
```

Et à refaire pour chaque serveur, chaque modpack, chaque mise à jour. Irium supprime cette route.

Irium est une plateforme de modding **server-driven** : le serveur héberge le runtime, les modules et le contenu ; le client n'exécute qu'un agent minimal installé une seule fois. Chaque serveur Irium fournit ensuite dynamiquement tout ce dont le joueur a besoin — HUD, interfaces riches, rendering, keybinds, gameplay — directement dans la session de jeu.

```text
        client vanilla
              │
              ▼
    agent irium · ~300 Ko · installé 1×
              │   handshake · manifest · signature
              ▼
    serveur irium ─── runtime ─ modules ─ api ─ contenu
              │
              ▼
    hud · ui riche · rendering · input · gameplay
    tout apparaît dans la session · tout disparaît en quittant
```

## Principes

| | |
|---|---|
| **Le module est le produit** | Les resource packs et datapacks sont le filet de sécurité, pas le cœur. La valeur vient du code streamé. |
| **Session sandbox** | Tout ce que le serveur ajoute disparaît à la déconnexion. Le client redevient vanilla, comportementalement. |
| **Dual-track** | Joueur sans agent : expérience de base (packs). Joueur équipé : expérience complète (code). |
| **Agent minimal éternel** | ~300 Ko, rien d'autre que le protocole — ASM, runtime, crypto. Il ne grandira jamais. Toutes les features vivent côté serveur. |

## Architecture

```text
                        ┌─────────────────────────────────────┐
                        │              client                 │
                        │  ┌───────────────┐  ┌────────────┐  │
                        │  │ minecraft     │  │agent irium │  │
                        │  │ vanilla       │◄─┤  ~300 ko   │  │
                        │  │               │  └─────┬──────┘  │
                        │  └───────────────┘        │         │
                        └───────────────────────────┼─────────┘
                                                    │ tls
                        ┌───────────────────────────┼─────────┐
                        │            serveur        │         │
                        │  ┌────────────────────────▼───────┐ │
                        │  │ irium hub · runtime · modules  │ │
                        │  │ signatures · cache · sandbox   │ │
                        │  └────────────────────────────────┘ │
                        │         ▲          ▲         ▲      │
                        │   plugins   economy   persistence   │
                        └─────────────────────────────────────┘
```

## Recherche

Ce projet repose sur cinq ans de recherche technique (2021 → 2026) autour d'une question : *jusqu'où peut-on pousser un client vanilla avant d'avoir besoin de le modifier ?* Verdict : **conditional go** — tous les verrous identifiés sont de l'ingénierie, aucun n'est théorique.

Rapports complets en français dans [`docs/research/`](docs/research/) :

| Rapport | Contenu |
|---|---|
| [`RAPPORT_SDM`](docs/research/RAPPORT_SDM.pdf) | Niveaux d'escalade N0→N6 · classloaders · agents JVM · protocole · architecture serveur · sécurité |
| [`RAPPORT_SDM2`](docs/research/RAPPORT_SDM2.pdf) | Les 7 verrous · JDK_JAVA_OPTIONS · version fantôme · Attach API · distribution Store |
| [`VALIDATION_SDM`](docs/research/VALIDATION_SDM.pdf) | Falsification en labo JDK 25 · preuves premain / retransform / attach à chaud |
| [`CONCLUSION_ET_PLAN`](docs/research/CONCLUSION_ET_PLAN.pdf) | Verdict conditional go · méthodologie · roadmap MVP · plan P1→P10 |

## Statut

<span id="status"></span>

Development phase 0 — construction des fondations. Rien n'est utilisable en production.

| Jalon | Contenu | État |
|---|---|---|
| **J1** | Plugin serveur : dialog de consentement natif, fallback chat, persistance, canal `irium:hello`, Folia-safe | **fait** |
| **J2** | Agent JVM : injection HUD dans une session live (PoC) | **suivant** |
| **Phase 1** | Handshake complet + capability manifest | planifié |
| **Phase 2** | Streaming de modules signés + cache vérifié | planifié |
| **Phase 3** | Session sandbox complète + hot loading | planifié |
| **Phase 4** | Irium Studio (devkit) + API publique | planifié |
| **Phase 5** | Pont de compatibilité Fabric (3 tiers) | planifié |

## Compatibilité

<span id="compatibilit"></span>

| Élément | Statut |
|---|---|
| Canvas 26.1.2 · Folia · Paper | supporté (plugin J1) |
| Clients vanilla ≥ 1.21.7 (protocole 767+) | dialog natif |
| Clients plus anciens | fallback chat cliquable |
| Agent | recherche validée (lab JDK 25), binaire non distribué |
| Fabric / Forge / NeoForge | pont 3 tiers planifié (tier 1 : extraction d'assets) |

## Technologie

<span id="technologie"></span>

| Composant | Choix |
|---|---|
| Serveur | Paper / Canvas / Folia — plugin Maven, `paper-api 26.1.2.build.74-stable` |
| Langage | Java 21 |
| Agent | Java agent (premain + attach), ASM, Ed25519 |
| Signature | Ed25519 sur manifestes et modules |
| Distribution | Microsoft Store (runFullTrust), fallback exe signé |

## Structure

```text
irium/
├── plugin/          plugin serveur Paper/Canvas (J1)
├── docs/
│   ├── research/    4 rapports de recherche (pdf + md)
│   ├── brand/       direction artistique (html)
│   └── img/         assets svg (banner, badges)
└── LICENSE          MIT
```

## Identité

Direction artistique **gemstone** : l'or brut dans la roche noire — jaune `#FFD84D`, noir `#0A0A0B`, gris `#8B8B93`. Le jaune est rare : il ne marque que ce qui compte. [`DA_IRIUM.html`](docs/brand/DA_IRIUM.html)

---

<div align="center">

**irium**

*la route de l'installation s'arrête ici*

</div>
