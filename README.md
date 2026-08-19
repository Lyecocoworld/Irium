<div align="center">

```
╔══════════════════════════════════════════════╗
║                                              ║
║     ██╗██████╗ ██╗ ██████╗ ██╗  ██╗          ║
║     ██║██╔══██╗██║██╔════╝ ██║  ██║          ║
║     ██║██████╔╝██║██║  ███╗███████║          ║
║██   ██║██╔══██╗██║██║   ██║██╔══██║          ║
║╚█████╔╝██║  ██║██║╚██████╔╝██║  ██║          ║
║ ╚════╝ ╚═╝  ╚═╝╚═╝ ╚═════╝ ╚═╝  ╚═╝          ║
║                                              ║
║        THE RARE ELEMENT OF MODDING           ║
║                                              ║
╚══════════════════════════════════════════════╝
```

# Irium

**Une nouvelle génération de plateforme de modding Minecraft — le serveur devient la plateforme, le client reste vanilla.**

*Zéro installation de mods. Un clic, une fois. Tout le modding.*

</div>

---

## 📜 Le problème

Installer des mods aujourd'hui, c'est un chemin de croix :

```text
Installer Fabric/Forge/NeoForge
+ Installer l'API du loader
+ Télécharger 14 mods
+ Vérifier les versions compatibles
+ Gérer les dépendances
+ Copier les fichiers dans .minecraft/mods
+ Relancer le jeu
= Et recommencer par serveur, par modpack, par mise à jour.
```

Chaque serveur moddé exige son propre client moddé. C'est le talon d'Achille du modding Minecraft depuis 15 ans.

## 💎 La thèse Irium

> **Le module est le produit. Les packs sont le filet.**

Irium inverse l'équation : le **serveur** devient la plateforme de modding. Il héberge le runtime, les modules, les dépendances, la logique et le contenu. Le client n'exécute qu'un **agent minimal (~300 Ko)** installé une seule fois — puis n'importe quel serveur Irium lui fournit dynamiquement tout ce dont il a besoin : HUD, interfaces riches, rendering custom, keybinds, effets, entités, gameplay complet.

```text
CLIENT VANILLA
      │
      ▼
AGENT IRIUM (~300 Ko, installé 1×)
      │  handshake + capability manifest
      ▼
SERVEUR IRIUM ──► runtime, modules, API, contenu, sécurité
      │
      ▼
HUD custom · UI riche · rendering · input · gameplay
— tout apparaît dans la session, tout disparaît en quittant —
```

- **Session sandbox** : tout ce que le serveur ajoute disparaît à la déconnexion — le client redevient vanilla comportementalement.
- **Dual-track** : les joueurs sans agent jouent en mode « N0 » (datapacks + resource packs = ~20 % de la magie) ; les joueurs équipés obtiennent les ~80 % restants (HUD, UI riche, rendering, input).
- **L'agent ne grandira jamais** : il ne contient rien d'autre que le protocole (ASM + runtime minimal + crypto JDK). Toutes les features vivent côté serveur.

## 🔬 D'où ça vient : 5 ans de recherche

Ce projet est né d'une recherche technique exhaustive (2021 → 2026) sur la question : *« Jusqu'où peut-on pousser un client Minecraft vanilla avant d'avoir besoin de le modifier ? »*

Les rapports complets (français, PDF) sont dans [`docs/research/`](docs/research/) :

| Document | Contenu |
|---|---|
| [`RAPPORT_SDM.pdf`](docs/research/RAPPORT_SDM.pdf) | Recherche complète : niveaux d'escalade N0→N6, classloaders, agents JVM, protocole, architecture serveur, sécurité |
| [`RAPPORT_SDM2.pdf`](docs/research/RAPPORT_SDM2.pdf) | Deuxième vague : les 7 verrous, JDK_JAVA_OPTIONS, version fantôme, Attach API, distribution Microsoft Store |
| [`VALIDATION_SDM.pdf`](docs/research/VALIDATION_SDM.pdf) | Validation expérimentale : falsification en labo (JDK 25), preuves premain / retransform / attach à chaud |
| [`CONCLUSION_ET_PLAN.pdf`](docs/research/CONCLUSION_ET_PLAN.pdf) | Verdict CONDITIONAL GO, méthodologie, roadmap MVP 6 semaines, plan P1→P10 |

## 🚧 Statut : IN DEV

Ce projet est en **développement actif et précoce**. Rien n'est utilisable en production.

```text
[x] Phase 0 / J1 — Plugin serveur : dialog de consentement natif (Paper Dialog API)
    · Dialog natif client (confirmation Activer / Continuer sans)
    · Fallback chat cliquable (clients protocole < 767)
    · Persistance du consentement (jamais redemandé)
    · Canal plugin irium:hello (poche pour le handshake agent J2)
    · Folia/Canvas-safe (EntityScheduler, callbacks reschedulés)
[ ] Phase 0 / J2 — Agent JVM : injection HUD dans la session live (PoC)
[ ] Phase 1 — Handshake complet + capability manifest
[ ] Phase 2 — Streaming de modules signés
```

Le code du plugin est dans [`plugin/`](plugin/) (Maven, Java 21, `paper-api 26.1.2.build.74-stable`, compatible Canvas/Folia).

## 🎨 Identité

Direction artistique : **jaune / noir / gris**, identité **gemstone** — l'or brut dans la roche noire. L'élément rare. Voir [`docs/brand/DA_IRIUM.html`](docs/brand/DA_IRIUM.html).

```text
#0A0A0B  Obsidienne    — fond
#161619  Roche         — surfaces secondaires
#26262B  Faille        — lignes, bordures
#8B8B93  Gris clair    — texte secondaire
#FFD84D  Irium Or      — LA couleur (rare : logo, CTA, actif)
#FFB800  Or profond    — dégradés
```

## 🛣️ Roadmap

| Phase | Contenu | Statut |
|---|---|---|
| Phase 0 | Plugin Canvas + consent dialog + canal hello | ✅ J1 fait |
| Phase 1 | Agent JVM (premain, ClassFileTransformer, HUD) | 🔬 recherche validée |
| Phase 2 | Handshake + manifest + cache signé | 📐 conception |
| Phase 3 | Streaming modules + session sandbox | 📐 conception |
| Phase 4 | Irium Studio (DevKit) + API publique | 📝 planifiée |
| Phase 5 | Compat Fabric (tiers 1-3 : extraction → adapter → émulation) | 📝 planifiée |

## ⚖️ Licence & usage

Code de ce dépôt sous licence MIT. Les documents de recherche sont fournis à titre informatif — recherche indépendante, non affiliée à Mojang ni Microsoft.

*Minecraft est une marque de Mojang Studios. Irium n'est pas affilié à Mojang, Microsoft ou les projets Fabric/Forge/NeoForge.*

---

<div align="center">

**Irium — the rare element of modding.**

*La route de l'installation s'arrête ici.* 💎

</div>
