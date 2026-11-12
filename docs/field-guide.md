# Field guide pages

Alterra's field guide is a book that fills in as the player discovers things. Any mod can add chapters and entries: a file named
`<modid>_guide.json` in the root of its jar.

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
        { "item": "alterra_example:thing", "text": "An item, drawn large, with a note." },
        { "recipe": "alterra_example:thing", "text": "A crafting recipe, by recipe id." },
        { "steps": [ { "in": ["minecraft:wheat"], "via": "create:millstone", "out": "alterra_example:flour", "amount": "x2", "text": "One step of a process." } ],
          "text": "Intro to the steps." }
      ]
    }
  ],
  "watch": ["minecraft:glass_bottle"]
}
```

* An entry can go into one of Alterra's chapters: `world`, `cold`, `temperate`, `warm`, `dry`, `seas`, `caves`, `nether`, `end`, `fruit`,
  `woods`, `plants`, `kitchen` (when Kitchen is installed), or into a chapter of your own.
* **unlock**: the entry opens when the player has any of these. Keys: `biome:<id>` (stood in it), `item:<id>` (carried it),
  `place:sky` (reached the sky islands). Empty list: known from the start. Locked entries show `???` and the **hint**.
* **mod** on a chapter or entry: only shown when that mod is installed. Pages that show an item, recipe or machine of a mod that is not
  installed are left out automatically.
* **watch**: extra items (outside the `alterra*` namespaces) whose pickup counts as a discovery.
* New entries get a red `!` until they are opened.
