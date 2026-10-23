# Conventions demandées par l’utilisateur pour les interventions de l’assistant

- Utiliser l’identité Git `Codex (GPT-6.1 Sol) <codex@openai.com>` comme auteur et committer ; identifier formellement l’outil et le modèle, sans utiliser le nom personnel de l’utilisateur ni le compte GitHub connecté.
- Organiser les changements en commits cohérents et faciles à auditer, séparés par catégorie : API/protocole, Fabric, Minestom, tests, compilation/CI, documentation.
- Poursuivre ce chantier directement sur la branche existante `port-26.2`, sans nouvelle branche ni nouvelle PR.
- Préserver les API publiques et le protocole existants lors de l’extraction de la gravité vers son dépôt indépendant.
