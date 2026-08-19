<div align="center">
  <a href="https://github.com/Lyecocoworld/irium">
    <img src="docs/img/banner.svg" alt="Irium — the rare element of modding" width="880"/>
  </a>

  <br/>

  <p>
    <a href="#statut"><img src="docs/img/badge-status.svg" alt="Status: in development"/></a>
    <a href="plugin/pom.xml"><img src="docs/img/badge-version.svg" alt="Irium v0.1.0"/></a>
    <a href="#compatibilité"><img src="docs/img/badge-mc.svg" alt="Minecraft 26.1.2+"/></a>
    <a href="LICENSE"><img src="docs/img/badge-license.svg" alt="License MIT"/></a>
    <a href="#technologie"><img src="docs/img/badge-java.svg" alt="Java 21+"/></a>
  </p>

  <p><strong>Le serveur gère le client et le serveur.<br/>C'est du streaming de mods pur et dur.</strong></p>
</div>

---

## Vue d'ensemble

Installer des mods Minecraft aujourd'hui impose au joueur :

```text
fabric installer  →  api  →  14 mods  →  versions  →  dépendances  →  mods/  →  reboot
```

Et à refaire pour chaque serveur, chaque modpack, chaque mise à jour. Irium supprime cette route.

Irium est une plateforme de modding **server-driven** : le serveur héberge le runtime, les modules, les dépendances et le contenu. Le client n'exécute qu'un agent JVM minimal installé une seule fois. Chaque serveur Irium fournit ensuite dynamiquement tout ce dont le joueur a besoin — HUD, interfaces riches, rendering, keybinds, gameplay — directement dans la session de jeu. **Le module est le produit ; les packs sont le filet.**

## Comment ça fonctionne

### 1 · L'agent JVM — une installation, tous les launchers

L'agent est un `javaagent` d'environ 300 Ko qui ne modifie **ni le jeu, ni ses fichiers**. Il s'injecte au niveau de la JVM (`premain` au lancement, attach à chaud si le jeu tourne déjà), ce qui le rend indépendant du launcher :

| | |
|---|---|
| Launchers | officiel, SKLauncher, Prism, CurseForge, ATLauncher, Modrinth App — tout ce qui lance une JVM |
| Instances | n'importe quelle version, n'importe quel profil, plusieurs instances en parallèle |
| Installation | une seule fois, via l'app (un clic) ; l'agent s'active ensuite partout, automatiquement |
| Contenu | rien d'autre que le protocole — ASM, runtime, crypto. Toutes les features vivent côté serveur |

L'agent ne grandira jamais. Il ne contient aucune feature : il est la **porte**, pas la maison.

### 2 · La chaîne de confiance — un serveur non validé n'obtient rien

L'agent joue aussi le rôle de **clé d'authentification**. La règle est simple :

```text
serveur enrôlé + manifest signé + agent authentique  →  expérience complète
serveur non enrôlé, clé invalide, signature absente  →  rien du tout
```

Concrètement : un opérateur qui installerait le système Irium sur son serveur **sans enrôlement validé par la plateforme** ne streamera rien. L'agent rejette ses manifests, le joueur reste sur l'expérience vanilla de base (datapacks et resource packs standards). C'est la condition pour que la technologie soit sûre pour les joueurs — et sereine pour les serveurs légitimes :

- chaque serveur est enrôlé et identifié auprès de la plateforme ;
- chaque manifest de modules est signé (Ed25519) et vérifié avant exécution ;
- révocation possible (liste de révocation, kill-switch) ;
- sandbox de session : tout ce que le serveur ajoute disparaît à la déconnexion — le client redevient vanilla, comportementalement.

### 3 · Streaming pur — le serveur pilote les deux bouts

C'est le cœur de la technologie : **un seul côté à développer, un seul côté à déployer**. Le serveur compile, signe et sert les modules ; l'agent les télécharge, les vérifie et les exécute dans le client.

```text
        client vanilla
              │
              ▼
    agent irium · ~300 Ko · installé 1×
              │   enrôlement · manifest signé · signature vérifiée
              ▼
    serveur irium ─── runtime ─ modules ─ api ─ contenu
              │
              ▼
    hud · ui riche · rendering · input · gameplay
    tout apparaît dans la session · tout disparaît en quittant
```

Le dual-track reste actif en permanence : un joueur sans agent (ou un serveur non enrôlé) reçoit l'expérience de base via packs ; un joueur équipé sur un serveur enrôlé reçoit le flux complet. Les modules peuvent être activés à chaud, en pleine session.

### 4 · Plugins et mods — deux voies, une plateforme

Irium réunit les deux mondes qui étaient séparés depuis toujours :

| Voie | Rôle | Ce qu'elle gagne |
|---|---|---|
| **Plugins** (le cerveau) | logique serveur : économie, persistance, gameplay, grades | l'accès aux super-pouvoirs des mods via l'**API Irium** — HUD, interfaces riches, rendering, input — sans jamais quitter le modèle plugin |
| **Modules** (la peau) | ce que le serveur streame au client | un runtime signé, sandboxé, chargé à la demande |

Les deux voies communiquent par un pont d'événements (`irium.event.*`) : un plugin déclenche, le module affiche. Un serveur Paper classique peut donc offrir une expérience visuelle équivalente à un modpack, sans qu'aucun joueur n'installe quoi que ce soit.

### 5 · Compatibilité loaders — une voie à la fois, comme un vrai loader

La compatibilité Fabric / Forge / NeoForge suivra les règles réelles des loaders, pas de magie :

- **une voie par instance** : le serveur choisit Fabric *ou* Forge *ou* NeoForge — exactement comme un joueur choisit son loader aujourd'hui ;
- **pas de cross-compatibilité gratuite** : un mod d'une autre voie ne fonctionnera pas, sauf via un pont précis, construit au cas par cas ;
- **le gateway est la dernière phase** du projet — pas la première. On ne promet pas de faire tourner l'écosystème existant tel quel ; on construit d'abord notre propre fondation.

### 6 · L'API native d'abord — plus simple, plus safe

Avant tout gateway de compatibilité, Irium fournit sa propre API de développement :

- décrire un module (HUD, UI, rendering, input) **sans écrire de code client** quand c'est possible ;
- des manifestes validés au build — les conflits module/module sont éliminés par design ;
- signatures, permissions et sandbox intégrés dès la première ligne de code.

Et porter un mod existant est peu coûteux : **le fonctionnement global du mod est conservé** (items, blocs, logique, registries). Seule la **couche communication** change — les canaux du loader sont remplacés par le protocole Irium, le serveur orchestrant la synchronisation. La majorité des mods ne demandent donc qu'un rework ciblé, pas une réécriture.

## Principes

| | |
|---|---|
| **Le module est le produit** | Les resource packs et datapacks sont le filet de sécurité, pas le cœur. La valeur vient du code streamé. |
| **Session sandbox** | Tout ce que le serveur ajoute disparaît à la déconnexion. Le client redevient vanilla, comportementalement. |
| **Agent minimal éternel** | ~300 Ko, rien d'autre que le protocole. Il ne grandira jamais. Toutes les features vivent côté serveur. |
| **Confiance vérifiée** | Serveurs enrôlés, manifests signés, révocation possible. Un serveur non validé n'obtient rien. |
| **Une voie loader à la fois** | Fabric, Forge ou NeoForge — les règles des loaders restent vraies. Les ponts viennent en dernier. |

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
                                                    │ tls · signature
                        ┌───────────────────────────┼─────────┐
                        │            serveur        │         │
                        │  ┌────────────────────────▼───────┐ │
                        │  │ irium hub · runtime · modules  │ │
                        │  │ enrôlement · cache · sandbox   │ │
                        │  └────────────────────────────────┘ │
                        │         ▲          ▲         ▲      │
                        │   plugins   economy   persistence   │
                        └─────────────────────────────────────┘
```

## Recherche

Ce projet repose sur cinq ans de recherche technique (2021 → 2026) autour d'une question : *jusqu'où peut-on pousser un client vanilla avant d'avoir besoin de le modifier ?*

Rapports complets en français dans [`docs/research/`](docs/research/) :

| Rapport | Contenu |
|---|---|
| [`RAPPORT_SDM`](docs/research/RAPPORT_SDM.pdf) | Niveaux d'escalade N0→N6 · classloaders · agents JVM · protocole · architecture serveur · sécurité |
| [`RAPPORT_SDM2`](docs/research/RAPPORT_SDM2.pdf) | Les 7 verrous · JDK_JAVA_OPTIONS · version fantôme · Attach API · distribution Store |
| [`VALIDATION_SDM`](docs/research/VALIDATION_SDM.pdf) | Falsification en labo JDK 25 · preuves premain / retransform / attach à chaud |
| [`CONCLUSION_ET_PLAN`](docs/research/CONCLUSION_ET_PLAN.pdf) | Verdict conditional go · méthodologie · roadmap MVP · plan P1→P10 |

## Statut

Phase 0 — construction des fondations. Rien n'est utilisable en production.

| Jalon | Contenu | État |
|---|---|---|
| **J1** | Plugin serveur : dialog de consentement natif, fallback chat, persistance, canal `irium:hello`, Folia-safe | **fait** |
| **J2** | Agent JVM : injection HUD dans une session live (PoC) | **suivant** |
| **Phase 1** | Handshake complet + manifest de capacités | planifié |
| **Phase 2** | Streaming de modules signés + cache vérifié | planifié |
| **Phase 3** | Session sandbox complète + hot loading + enrôlement serveurs | planifié |
| **Phase 4** | API native (plugins ↔ modules) + devkit | planifié |
| **Phase 5** | Gateway de compatibilité Fabric / Forge / NeoForge (3 tiers) | dernier |

## Compatibilité

| Élément | Statut |
|---|---|
| Canvas 26.1.2 · Folia · Paper | supporté (plugin J1) |
| Clients vanilla ≥ 1.21.7 (protocole 767+) | dialog natif |
| Clients plus anciens | fallback chat cliquable |
| Agent | recherche validée (lab JDK 25), binaire non distribué |
| Mods Fabric / Forge / NeoForge | gateway 3 tiers — dernière phase, une voie à la fois |

## Technologie

| Composant | Choix |
|---|---|
| Serveur | Paper / Canvas / Folia — plugin Maven, `paper-api 26.1.2.build.74-stable` |
| Langage | Java 21 |
| Agent | javaagent (premain + attach à chaud), ASM, Ed25519 |
| Signature | Ed25519 sur manifests et modules · révocation centralisée |
| Distribution | application un clic (Store, runFullTrust), fallback exe signé |

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

---

<div align="center">

**irium**

*la route de l'installation s'arrête ici*

</div>
