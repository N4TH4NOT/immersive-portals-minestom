# Constats de migration Minecraft 1.21 → 26.2

[English](migration-audit-26.2.md) · [Français](migration-audit-26.2.fr.md)

Revue du 22 octobre 2026 contre la branche 1.21 héritée. Le [périmètre amont](upstream-1.21-review.fr.md) fixe la référence ; la [validation en jeu](port-26.2.fr.md) est maintenue séparément.

## Corrections issues de la revue

| Domaine | Constat et résolution |
| --- | --- |
| Création de monde | Le mixin de constructeur capturait des arguments 1.21 obsolètes ; ne capturer que le callback utilisé. |
| Ressources d’objets | Ajouter les définitions actuelles référençant les modèles conservés. |
| Génération inverse | Enregistrer les règles inverses validées pour toutes les familles de déclencheurs. Tests des règles valides, invalides et à sens unique. |
| Hooks de téléportation | Distinguer déplacement local et changement de dimension pour éviter les effets sur génération/trackers. |
| Chargement de chunks | Attendre la promotion de la sentinelle native au lieu de libérer une place encore en attente. |
| Terrain distant | Copier les deltas réutilisables par vue, reprendre les chunks initiaux et propager transitions vide/non vide, remplacement et déchargement. |
| Datapack d’exemple | Migrer chemins de registre, tags et format ; déclarer les dimensions par datapack plutôt que DimLib. |

## Preuves et limites

La revue couvre sources/ressources, signatures Mixin, access widener, codecs, rendu, physique et adaptateurs optionnels. L’inspection du bytecode et les essais réels complètent la compilation : un build réussi ne prouve pas le chargement des ressources ni les arguments capturés par les mixins.

Les scénarios couvrent création de monde/datapacks Fabric, formes, vues récursives, rendu GUI, traversées/conteneurs Minestom, gravité et Sodium/Iris. Les références d’entités et transitions natives 26.2 remplacent les anciens mixins de propriétaires de projectiles et de perles.

Les intégrations indisponibles figurent dans le [README](../README.fr.md). L’absence de version correspondante n’implique pas leur abandon. Les composants tiers propres à l’application et l’ensemble des GPU/shaderpacks ne sont pas couverts.
