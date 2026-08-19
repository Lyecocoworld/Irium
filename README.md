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

## Le problème

Jouer sur un serveur moddé aujourd'hui :

```text
fabric installer  →  api  →  14 mods  →  versions  →  dépendances  →  mods/  →  reboot
```

À refaire pour chaque serveur, chaque modpack, chaque mise à jour. Résultat : les serveurs "plugins" et les serveurs "mods" sont deux mondes fermés. Un serveur Paper ne peut pas proposer d'interfaces riches, de HUD custom, de rendering — parce que le joueur devrait installer les mods lui-même. Et la plupart ne le font jamais.

## Le projet

Irium supprime cette frontière. C'est une plateforme de modding **server-driven** :

- le **serveur** héberge le runtime, les modules, les dépendances et le contenu — il gère le client et le serveur ;
- le **joueur** installe une seule fois un petit agent JVM (~300 Ko) via une application, un clic ;
- ensuite, sur n'importe quel serveur Irium, tout arrive automatiquement dans la session : HUD, interfaces riches, rendering, keybinds, gameplay. Rien à télécharger, rien à configurer.

Le joueur garde son launcher habituel, ses instances, ses versions. Un joueur sans agent voit simplement le serveur comme un serveur plugin normal.

## Comment ça fonctionne

### L'agent JVM

L'agent est un `javaagent` qui ne modifie ni le jeu, ni ses fichiers. Il s'injecte au niveau de la JVM (au lancement, ou à chaud si le jeu tourne déjà), ce qui le rend indépendant du launcher :

| | |
| Launchers | officiel, SKLauncher, Prism, CurseForge, ATLauncher, Modrinth App |
| Instances | n'importe quelle version, n'importe quel profil, en parallèle |
| Installation | une seule fois, un clic ; l'agent s'active ensuite partout |
| Contenu | rien d'autre que le protocole — ASM, runtime, crypto |

L'agent ne grandira jamais. Il est la **porte**, pas la maison : toutes les features vivent côté serveur.

### La chaîne de confiance

L'agent est aussi une **clé d'authentification** :

```text
serveur enrôlé + manifest signé + agent authentique  →  expérience complète
serveur non enrôlé · clé invalide · signature absente →  rien du tout
```

Un opérateur qui installe le système Irium sur son serveur **sans enrôlement validé par la plateforme** ne streamera rien. L'agent rejette ses manifests, le joueur reste en vanilla. C'est ce qui rend la technologie sûre pour les joueurs et sereine pour les serveurs légitimes : chaque serveur est identifié, chaque manifest est signé et vérifié avant exécution, tout est révocable.

### Le streaming

Le serveur compile, signe et sert les modules. L'agent les télécharge, les vérifie et les exécute dans le client. Un seul côté à développer, un seul côté à déployer.

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

Tout ce que le serveur ajoute est **sandboxé dans la session** : à la déconnexion, le client redevient vanilla. Les modules peuvent être activés à chaud, en pleine partie.

### Plugins et modules

Irium réunit les deux mondes : les **plugins** (le cerveau : économie, persistance, gameplay, grades — tout ce qui existe déjà côté serveur) et les **modules** (la peau : ce que le serveur streame au client). Les deux communiquent par un pont d'événements. Un serveur Paper classique peut donc offrir une expérience visuelle équivalente à un modpack, sans qu'aucun joueur n'installe quoi que ce soit.

À terme, les plugins bénéficient du plein potentiel des mods grâce à l'**API Irium** : HUD, interfaces riches, rendering, input — sans jamais quitter le modèle plugin.

### Compatibilité loaders

La compatibilité Fabric / Forge / NeoForge suivra les règles réelles des loaders :

- **une voie par serveur** : Fabric *ou* Forge *ou* NeoForge — comme un joueur choisit son loader aujourd'hui ;
- **pas de cross-compatibilité gratuite** : un mod d'une autre voie ne fonctionnera pas, sauf pont précis construit au cas par cas ;
- **le gateway est la dernière phase** du projet — on construit d'abord notre propre fondation.

### L'API native

Avant tout gateway, Irium fournit sa propre API de développement, conçue pour être plus simple et plus sûre que le modding classique : décrire un module sans écrire de code client quand c'est possible, manifestes validés au build, signatures et sandbox intégrés. Porter un mod existant est peu coûteux : le fonctionnement global est conservé, seule la **couche communication** change.

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
│   ├── research/    documents de recherche (md)
│   ├── brand/       direction artistique (html)
│   └── img/         assets svg (banner, badges)
└── LICENSE          MIT
```

## Licence

MIT — voir [LICENSE](LICENSE). Projet indépendant, non affilié à Mojang Studios ni Microsoft. Minecraft est une marque de Mojang Studios.
