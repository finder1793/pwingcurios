# PwingCurios

A flexible, GUI-driven accessories framework for Paper/Spigot servers. Define any number of custom Curio slots (Necklaces, Rings, Charms, Trinkets, Elytra, etc.), persist them in YAML or SQLite, and grant effects. It also exposes:
- Plugin API for other server plugins to hook into.
- Simple Mod API over Plugin Messaging for client-side integration.

## Features
- Unlimited, configurable Curio slots.
- Per-slot acceptance rules:
  - Lore keyword matching (case-insensitive, matches anywhere in a lore line)
  - Optional materials whitelist (e.g., allow only ELYTRA)
- Per-slot placeholder item, with optional custom model data.
- Fully configurable GUI (size, title, background filler).
- Persistent storage per-player via YAML or SQLite (selectable in config).
- Elytra auto-equip: when an Elytra is equipped in a Curio slot, it is auto-equipped to the armor chest slot and your original chestplate stats are mirrored so players keep their armor/toughness bonuses.
- Optional integration detection (MMOItems, Crucible, Nexo).
- If MMOItems or Crucible is installed, Curio items that carry vanilla AttributeModifiers will have those attributes applied to players while equipped (and removed cleanly when unequipped).
- Internal direct integrations: when MMOItems/Crucible are detected, PwingCurios invokes built-in hook points to apply/clear external stats alongside your Curios (safe no-ops if APIs are unavailable).
- Direct MMOItems/MythicLib stat support (soft): when MythicLib/MMOItems are present, Curio items’ MMO stats are aggregated and best‑effort applied to players via MythicLib (Critical Chance, Mana/Max Mana/Mana Regen, Skill Damage, Damage Types, Defense/Toughness/Health/Health Regen). All calls are reflective and fail-safe.

## Installation
1. Place the built jar into your `plugins/` folder.
2. Start the server once to generate `plugins/PwingCurios/config.yml`.
3. Configure the GUI and slots under the `curios` section. Reload or restart.
4. Use `/curios open` in-game to see the GUI, or `/curios give` to get a test accessory.

Paper API target: 1.21.x.

## Commands & Permissions
- `/curios open` — Opens the Curios GUI (default).
- `/curios give` — Gives a demo "Swift Charm" accessory.
- Permission: `pwingcurios.use` (default: true)

## Configuration Overview
File: `plugins/PwingCurios/config.yml`

```
storage:
  type: YAML # YAML or SQLITE
  sqlite:
    file: data.db

curios:
  gui:
    title: "Curios"
    size: 27
    background:
      material: GRAY_STAINED_GLASS_PANE
      name: " "
    placeholder:
      material: LIGHT_GRAY_STAINED_GLASS_PANE
      name: "§7Empty Slot"
      custom-model-data: -1 # -1 to disable globally
  slots:
    - id: necklace_1
      index: 10
      name: "Necklace"
      keywords: ["necklace"]
      placeholder:
        material: LIGHT_BLUE_STAINED_GLASS_PANE
        name: "§bEmpty Necklace"
        custom-model-data: 1001
    - id: ring_1
      index: 12
      name: "Ring"
      keywords: ["ring"]
      placeholder:
        material: YELLOW_STAINED_GLASS_PANE
        name: "§eEmpty Ring"
    - id: charm_1
      index: 14
      name: "Charm"
      keywords: ["charm"]
    - id: trinket_1
      index: 16
      name: "Trinket"
      keywords: ["trinket"]
    - id: elytra_1
      index: 22
      name: "Elytra"
      materials: ["ELYTRA"]
      placeholder:
        material: LIME_STAINED_GLASS_PANE
        name: "§aEmpty Elytra"
```

Notes:
- If a slot has `materials`, those are matched against item type name (case-insensitive) before keywords.
- If a slot has neither `materials` nor `keywords`, any item is accepted.
- Legacy keys (`gui.*` and `curio-slots`) are still read for compatibility.

## Storage Backends
- YAML (default): stores to `plugins/PwingCurios/data.yml`.
- SQLite: set `storage.type` to `SQLITE` and configure `storage.sqlite.file`.

## Stat stacking semantics & additivity
- MMOItems/MythicLib stats: PwingCurios aggregates supported stats across all equipped Curios and applies the aggregated total to the player via MythicLib using a single source key "PwingCurios". On each refresh we first remove the previous modifiers from that source, then re-apply the new totals. This means:
  - Multiple Curios stack additively with each other.
  - Refreshing does not double-count because we clear the previous source first.
  - Removing or changing a Curio recomputes totals and reapplies cleanly.
- Vanilla AttributeModifiers: If MMOItems/Crucible are installed and Curio items carry vanilla AttributeModifiers in their ItemMeta, we mirror those onto the player as transient modifiers. We keep each modifier's original operation (e.g., ADD_NUMBER, ADD_SCALAR, MULTIPLY), so their math behaves the same as on the item. We track and remove them cleanly when Curios change or on quit.

## Elytra Auto-Equip
- Putting an Elytra into any Curio slot that accepts it auto-equips it to the Armor Chest slot.
- If the player had a chestplate, its attribute modifiers are mirrored as transient player modifiers so the player keeps stats while flying.
- When the Elytra Curio is removed, the mirrored stats are cleared and the preserved chestplate is restored.

## Developer API (for plugins)
PwingCurios registers a service you can fetch from Bukkit Services:

```java
PwingCuriosAPI api = Bukkit.getServicesManager().load(PwingCuriosAPI.class);
if (api != null) {
    List<SlotDefinition> slots = api.getSlots();
}
```

Interface summary:
- `List<SlotDefinition> getSlots()` — current slot definitions.
- `Map<String, ItemStack> getEquipped(Player|UUID)` — current equipment.
- `Optional<String> firstEmptyCompatibleSlot(Player, ItemStack)` — where this item can go.
- `boolean equip(Player, ItemStack, String slotId)` — equip into slot (fires CurioEquipEvent, cancellable).
- `Optional<ItemStack> unequip(Player, String slotId)` — unequip from slot (fires CurioUnequipEvent, cancellable).
- `void openGui(Player)` — open the Curios GUI.

Events:
- `CurioEquipEvent` (cancellable): player, slotId, item to equip.
- `CurioUnequipEvent` (cancellable): player, slotId, item being removed.
- `CurioExternalStatsClearEvent` (cancellable): fire before re-apply or on player quit to let integrations clear any external stats.
- `CurioExternalStatsApplyEvent` (cancellable): fire after built-in effects so integrations (MMOItems/Crucible) can apply their own stats like Critical Chance, Mana, Skill Damage based on equipped Curios.

Example listener:
```java
@EventHandler
public void onCurioEquip(CurioEquipEvent e) {
    if (e.getItem() != null && e.getItem().hasItemMeta()) {
        // Cancel or modify behavior based on your logic
    }
}
```

## Mod API (plugin messaging channel)
A minimal plugin messaging API is provided on channel `pwingcurios:api`.

Client -> Server (UTF packet):
- `OPEN` — opens the GUI for the sending player.
- `SLOTS` — server replies with a `SLOTS` packet encoding slot definitions.

Server -> Client `SLOTS` packet layout:
- UTF: `SLOTS`
- int: count
- For each slot (repeat count times):
  - UTF: slotId
  - int: index
  - UTF: displayName

Example (client mod pseudocode):
```java
// Request slots
ByteArrayDataOutput out = ByteStreams.newDataOutput();
out.writeUTF("SLOTS");
player.sendPluginMessage("pwingcurios:api", out.toByteArray());

// Listen for response on same channel and parse as described above.
```

We can extend this channel with additional subcommands on request (GET_EQUIPPED, EQUIP, UNEQUIP, etc.).

## Building
- JDK 21+
- Gradle: `gradlew build` (produces a shaded jar with SQLite driver via Shadow plugin).
- For quick testing on Paper: `gradlew runServer` (uses xyz.jpenilla.run-paper).

## Compatibility
- Paper 1.21.x API. Some attribute methods are deprecated in 1.21 and may change in 1.22; we keep them for now for broader compatibility.

## Contributing
PRs and feature requests are welcome. Ideas:
- More built-in effects and configurable effect mapping.
- Extra storage backends (MySQL) and migrations.
- Deeper integrations (MMOItems/Nexo attribute mirroring).
- Richer Mod API subcommands.
