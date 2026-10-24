# Contribution conventions

[English](AGENTS.md) · [Français](AGENTS.fr.md)

These rules apply to assistant contributions in this repository.

- Use `Codex (GPT-6.1 Sol) <codex@openai.com>` as Git author and committer for assistant work. Use Git to preserve both identities; never substitute the connected user's name or account.
- Write commit subjects and bodies in English. Keep commits coherent and auditable, separated by API/protocol, Fabric, Minestom, tests, build/CI or documentation. Avoid a single bulk commit or artificial follow-up fixes.
- Continue the Minecraft 26.2 migration and gravity extraction on the existing `port-26.2` branch in the portal repository; create no additional branch or PR.
- Preserve public Java APIs and wire compatibility when extracting gravity.
- Write concise documentation for experienced Minecraft/Minestom developers. Use English by default with paired French `<name>.fr.md` files (`README.fr.md` for `README.md`) and reciprocal language links. Maintain both languages together; link to source/root configuration instead of duplicating obvious details.
