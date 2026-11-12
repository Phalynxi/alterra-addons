# Field guide pages

Any mod can add pages to the field guide: put `<modid>_guide.json` in the root of your jar.

```json
{
  "chapters": [ { "id": "example", "title": "Example", "icon": "alterra_example:thing", "mod": "create" } ],
  "entries": [
    {
      "id": "thing", "chapter": "example", "title": "The Thing", "icon": "alterra_example:thing",
      "unlock": ["item:alterra_example:thing", "biome:alterra:redwood_forest"],
      "hint": "Look for it in the redwoods.",
      "pages": [
        { "text": "Plain text. About 300 characters fit on a page." },
        { "item": "alterra_example:thing", "text": "An item drawn big, with a note." },
        { "recipe": "alterra_example:thing", "text": "A crafting recipe, by recipe id." },
        { "steps": [ { "in": ["minecraft:wheat"], "via": "create:millstone", "out": "alterra_example:flour", "amount": "x2", "text": "One step." } ],
          "text": "Intro to the steps." }
      ]
    }
  ],
  "watch": ["minecraft:glass_bottle"]
}
```

- Entries can go in one of Alterra's chapters (`world`, `cold`, `temperate`, `warm`, `dry`, `seas`, `caves`, `nether`, `end`, `fruit`, `woods`, `plants`, `kitchen` if Kitchen is installed) or in your own.
- `unlock`: the page opens when the player has any of these. `biome:<id>` means they've been in it, `item:<id>` that they've had the item, `place:sky` that they've reached the sky islands. Leave it empty and the page is open from the start. Locked pages show ??? and the hint.
- `mod` on a chapter or entry means it only shows if that mod is installed. Pages that show things from a mod that isn't installed are dropped automatically.
- `watch` is extra items (outside the alterra namespaces) that count as a discovery when picked up.
- New pages get a red ! until they're opened.
