# Conventions de contribution

[English](AGENTS.md) · [Français](AGENTS.fr.md)

Ces règles s’appliquent à toutes les interventions de l’assistant dans ce dépôt.

- Utiliser `Codex (GPT-6.1 Sol) <codex@openai.com>` comme auteur et committer Git de l’assistant. Employer Git pour conserver les deux identités, sans reprendre le nom ou compte de l’utilisateur connecté.
- Rédiger titres et descriptions des commits en anglais. Séparer les commits cohérents et auditables par API/protocole, Fabric, Minestom, tests, compilation/CI ou documentation. Éviter le commit global et les corrections artificielles.
- Poursuivre la migration Minecraft 26.2 et l’extraction de gravité sur la branche existante `port-26.2` du dépôt de portails, sans nouvelle branche ni PR.
- Préserver les API Java publiques et la compatibilité réseau lors de l’extraction de gravité.
- Rédiger une documentation concise pour développeurs Minecraft/Minestom expérimentés. Anglais par défaut avec fichiers français `<nom>.fr.md` (`README.fr.md` pour `README.md`) et liens de langue réciproques. Maintenir les deux langues ensemble ; référencer sources/configuration racine au lieu de répéter les détails évidents.
