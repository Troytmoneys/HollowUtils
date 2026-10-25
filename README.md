# HollowUtils

[Download the built plugin JAR](downloads/hollowutils-1.0.0.jar?raw=true). Stop Paper, put this file in `plugins/` alongside Multiverse-Core and Multiverse-Inventories, then start Paper. This build has passed automated tests; live integration testing remains.

Java 21 plugin targeting **Paper 1.21.11**, with **Multiverse-Core 5.x** and **Multiverse-Inventories 5.x**. Use recent releases exposing the current `MultiverseCoreApi` and `MultiverseInventoriesApi` APIs (including advancement, statistics and recipe shares). Older Multiverse 4.x is unsupported. Folia is unsupported.

A “server” is a logical group of three worlds on one Paper process. `/server` opens a paginated inventory selector; `/server survival` joins directly. The initially loaded primary world and its loaded vanilla Nether/End become the reserved `lobby` group. A created server gets `hu_<name>`, `hu_<name>_nether`, and `hu_<name>_the_end`, using one seed.

## Build and install

1. Install a **JDK 21** and Maven 3.9+, then run `mvn -B verify` in this directory.
2. Stop your Paper 1.21.11 server. Back up the entire existing server first.
3. Install compatible Multiverse-Core and Multiverse-Inventories releases in `plugins/` along with `target/hollowutils-1.0.0.jar`. Start normally; do not use `/reload` or plugin hot loaders.
4. Check the console for `Logical servers loaded`. The primary lobby is automatically registered. Create your first server with `/createserver survival` from the console or as an operator. Generation can pause ticks.
5. Join using `/server survival`, stand in a safe location and use `/setspawn`.

If an existing MVI group contains exactly the lobby worlds, its existing profile identity is preserved. Conflicting overlapping groups cause startup to lock player joins; resolve the memberships in MVI before restarting. New managed worlds must not be included in wildcard/regex groups belonging to other servers. Do not grant MVI bypass permissions to isolated players, and avoid gamemode-specific profiles unless you intentionally want additional separation. The plugin enables MVI optional advancement, statistics, recipe and ender-chest shares and saves that setting; it does not manage economy balances.

`servers.yml` persists world membership, UUID whitelists, maintenance, deletion recovery state and each server's spawn. Preserve it and the entire Multiverse plugin data alongside your worlds. Treat world membership as configuration: do not manually move a world between profiles while players are online.

## Commands

| Command | Behavior |
| --- | --- |
| `/server [name] [player]` | Open selector, join a logical server or move a target player |
| `/createserver <name> [numeric-seed]` | Create all three dimensions and their MVI profile group |
| `/backupserver <name>` | Back up an empty non-lobby server's world directories |
| `/whitelistserver <name> on\|off` | Enable/disable UUID access list |
| `/whitelistserver <name> add\|remove <online-name-or-UUID>` | Change access list |
| `/whitelistserver <name> list` | Show UUID access list |
| `/maintenanceserver <name> on\|off` | Restrict entry; move non-bypass occupants to lobby |
| `/deleteserver <name>` | Request deletion confirmation valid for 30 seconds |
| `/deleteserver <name> confirm` | Back up and permanently remove the three empty worlds |
| `/spawn [player]` | Visit this server's spawn or send a target to their server's spawn |
| `/setspawn [player]` | Set the server spawn at your position or a target's position, including facing |
| `/gmc [player]` | Creative mode |
| `/gms [player]` | Survival mode |
| `/gmsp [player]` | Spectator mode |
| `/gma [player]` | Adventure mode |
| `/gamemode <mode> [player]`, `/gm <mode> [player]` | Change mode using a full name, `s/c/a/sp`, or `0/1/2/3` |
| `/heal [player]` | Restore maximum hearts, full hunger/saturation, clear exhaustion, extinguish fire and reset fall distance |

Names: 1–24 lowercase letters, digits, underscores or hyphens, starting with a letter. Name collisions with existing world directories are rejected. The fallback lobby cannot be deleted, whitelisted or placed in maintenance.

Permissions: `hollowutils.use` defaults to everyone; `hollowutils.admin` defaults to operators; `hollowutils.bypass` defaults to operators and allows entry during maintenance or without whitelist membership. Bypass never permits entry into incomplete/deleting groups.

## Essentials-style permissions and command priority

HollowUtils takes ownership of its **unqualified command names**, including `/spawn`, `/gamemode`, `/gm`, `/gmc` and `/heal`, even if Essentials or another plugin registered them first. It claims the Paper command map on enable, again after server startup and after later plugin enables, and refreshes the client command tree. Player/console command events route these names explicitly to `hollowutils:<command>`. It restores displaced command-map entries when disabled. Restart after replacing the JAR; hot loading remains unsupported.

Other plugins' namespaced commands (for example `essentials:gmc`) are not replaced. They remain blocked for players by the normal command allowlist unless explicitly permitted, and console can still invoke them deliberately. This prioritizes normal registration conflicts; another plugin deliberately cancelling or rewriting command events can still interfere. It does not replace unrelated commands that HollowUtils does not implement.

The new utility permissions default to operators:

| Permission | Allows |
| --- | --- |
| `hollowutils.gamemode` | Use gamemode commands |
| `hollowutils.gamemode.creative` / `.survival` / `.adventure` / `.spectator` | Select each corresponding mode; base gamemode permission also required |
| `hollowutils.gamemode.others` | Change another player's mode |
| `hollowutils.heal` | Heal yourself |
| `hollowutils.heal.others` | Heal another player; base heal permission also required |
| `hollowutils.spawn.others` | Use `/spawn <player>` |
| `hollowutils.setspawn.others` | Use `/setspawn <player>`; `hollowutils.admin` also required |
| `hollowutils.server.others` | Use `/server <name> <player>` |

Player targets use exact online names, case-insensitively. Selectors and partial names are not supported. Players can only target visible players in their own logical server, including players in its Nether/End; operator status does not bypass this restriction. Player-name suggestions use those same rules and only offer other players when the caller has the command's `others` permission. Explicitly naming yourself does not require an `others` permission. Console must supply a player for player actions and can administer any server.

`/heal` respects custom maximum-health attributes, refuses dead players, and preserves potion effects. Gamemode commands report cancellation if another plugin vetoes the change.

## Isolation provided

- Each group's overworld, Nether and End share MVI inventory, armor, offhand, ender chest, health, hunger, XP, potion effects, bed spawn, advancements, statistics and recipes. Different groups have separate profiles for those enabled shares.
- Paper chat audiences, join/quit/death messages and advancement announcements are scoped to the current group; console keeps chat auditing.
- Cross-group players are hidden using Paper's player visibility API, including the normal tab list and player entities. HollowUtils never re-shows players another plugin has hidden.
- Portal destinations are redirected to the current group's dimensions. Bed/respawn destinations outside the group fall back to its spawn. Non-player portal travel is blocked to prevent item/mob transfers.
- All player commands except the HollowUtils commands are blocked by default, including namespaced alternatives. Only authorized roots appear in the command tree. HollowUtils completions never fall back to Bukkit's global player-name suggestions.
- Whitelist/maintenance applies to teleports by other plugins too. Direct console administration remains available.

`allowed-commands` in `config.yml` can enable additional exact command labels. Only add commands whose permissions, completion, player lookup and stored data are safe across groups. Adding commands such as `execute`, `tp`, `msg`, global player lists, or their aliases can defeat isolation. Restart after changes.

## Backups and recovery

Move every player out of the target server first. Backup saves and temporarily **unloads all three worlds** through Multiverse, writes a ZIP in `plugins/HollowUtils/backups/`, and reloads the previously loaded worlds in a `finally` block. It rejects symlinks, omits `session.lock`, cleans partial archives and never overwrites an existing ZIP. A companion `.servers.yml` records configuration. File copying is synchronous: large worlds can pause the main thread and trigger watchdog limits. Prefer stopped-process backups for large deployments. The primary lobby cannot be unloaded and must be backed up offline.

**These are world backups, not complete player-profile backups.** MVI's active profile caches and plugin-owned data are not captured by `/backupserver`. For disaster recovery, stop Paper and copy the entire server directory, including `plugins/Multiverse-Inventories`, `plugins/Multiverse-Core`, `plugins/HollowUtils`, all worlds and all other plugin data. Keep copies outside this host. Restore only while stopped, with the matching registry and MVI profiles. Do not restore only one player's vanilla `playerdata` and expect it to override MVI.

Deletion refuses occupied worlds, requires confirmation, and completes a backup before removing anything. Incomplete creation/deletion stays locked in `servers.yml` across restarts; inspect the console, resolve failed MV operations, then retry deletion. Existing unrelated world directories are never intentionally removed. Backups and old MVI player profiles remain after world deletion; archive them before reusing a deleted server name to avoid inheriting old profiles.

## Limits and validation

This is world isolation within one JVM, **not a replacement for separate backend servers behind Velocity**. There is one plugin list, global permission system, scoreboard infrastructure, console, resource pool and server-list ping/player count. It cannot make arbitrary third-party broadcasts, custom GUIs, plugin messages, economy, permissions or plugin-owned player data private. Other plugins can override event results. MVI owns advancement restoration, including its reward behavior; test datapack advancements with loot/function rewards before enabling them across groups. For a strict “everything separate” guarantee, use separate backend processes.

Validated here: Java 21 bytecode compilation against Paper 1.21.11 API, packaged plugin resources, and 20 passing automated tests for access rules, names, dimension naming, ZIP safety, command-priority conflicts, gamemode changes, healing and scoped player targeting. **A live Paper + Multiverse integration run has not been performed.** The Multiverse bridge checks API availability at runtime and fails closed, retaining a join-locking listener if startup configuration fails. Validate compatible MV release versions on staging before production.

Staging checklist with two non-op accounts:

1. Create `alpha` and `beta`; use the selector and direct join. Verify separate inventory, armor, XP, hunger, effects, ender chest, advancements, statistics and recipes; switch back and restart to check persistence.
2. Confirm players in different groups cannot see one another in chat/tab, entities, death/advancement announcements or command suggestions. Try namespaced commands and forbidden aliases.
3. Enter both Nether and End portals and return; confirm destinations stay inside the group. Test beds, death, End exit, and `/spawn` facing.
4. Enable whitelist and maintenance while a non-bypass account is inside. Confirm evacuation and denied reentry; verify operator bypass separately.
5. Move players out, back up, inspect the ZIP, and test a restore on a separate stopped staging server. Confirm occupied deletion is rejected, confirmation expires, and lobby deletion is refused.
6. Restart after profile changes and review all installed plugins for global broadcasts/data/commands. Test the same scenarios without operator or MVI bypass permissions.


Essentials coexistence checklist: with Essentials installed, confirm `/gmc`, `/gm spectator <player>`, `/heal <player>` and `/spawn` execute HollowUtils behavior from players and console. Deny each permission separately and check suggestions plus execution. Place a non-op target in another logical server and confirm neither lookup nor completion reveals that player. Test custom max-health, burning, low hunger and a dead target. Confirm a plugin cancelling `PlayerGameModeChangeEvent` results in a failure message rather than a false success.


## Death return, backups, broadcasts and kits

| Command | Behavior |
| --- | --- |
| `/back` | Return to your last death location in the current logical server; saved across restarts |
| `/backup [server] [local\|drive\|both]` | Evacuate the target group to lobby, create a local three-world ZIP and optionally upload it to Drive (default: both) |
| `/bottom [player]` | Go down to the lowest full solid block with two air blocks above it at the same X/Z coordinates; refuses if none exists |
| `/bc <message>`, `/broadcast <message>` | Broadcast plain text to **all logical servers on this Paper process**, regardless of their chat separation |
| `/ci [player]`, `/clearinventory [player]`, `/clear [player]` | Clear inventory, armor, offhand and cursor; preserve ender chest |
| `/createkit <name> INVENTORY` | Copy your inventory, armor and offhand into a persistent kit without consuming items |
| `/createkit <name> GUI` | Open a blank template editor |
| `/kit [name] [player]` | List kits or apply a kit to an empty player inventory |
| `/deletekit <name>` | Delete a stored kit |

`/back` uses `hollowutils.back` (everyone by default). Other new commands use `hollowutils.<canonical-command>` with operator defaults: `backup`, `bottom`, `broadcast`, `clear`, `createkit`, `kit`, `deletekit`. Targeting someone else additionally needs `hollowutils.bottom.others`, `hollowutils.clear.others` or `hollowutils.kit.others`. Target lookup/completion remains restricted to visible players in the same logical server; console can target any player. Broadcast is deliberately global. Kit templates are global administrative definitions; granted item stacks belong to each player's current MVI profile. Kits have no cooldown; only grant kit access to trusted players or kits intended for repeat use.

`/back` returns to the exact death spot, which may still be dangerous, including lava or the void. It never crosses logical server boundaries. `/bottom` searches strictly below the player's feet, all the way to world minimum height, excluding magma floors and non-air headroom.

### Kit editor controls

The GUI is a **template**, not an item-transfer container:

- Click an item in your own inventory to select a copy as the brush. Left-click a template slot to place that copy; right-click to clear a slot. Your original items are never consumed or withdrawn from the template.
- Slots 0–8 are hotbar; 9–35 are main inventory. Slots 36–39 are boots, leggings, chestplate and helmet; slot 40 is offhand. Use suitable equipment in those armor slots.
- Shift-right-click an occupied template slot to open its enchantment editor. Left-click an enchantment to increase its level, right-click to decrease/remove it. The GUI enforces compatibility, conflicts and normal maximum levels. Navigation arrows show more enchantments; Back returns to the template.
- Green saves, red discards. Closing the editor without saving discards it. Kits preserve item metadata, enchantments and stack amounts using Bukkit item serialization in `kits.yml`; inventory capture also preserves stored-book enchantments and other existing metadata.
- Applying a kit requires empty inventory, armor, offhand and cursor, so existing items cannot be silently lost. Existing kit names cannot be overwritten; delete first to replace.

### Google Drive setup

The integration targets [glotlabs/gdrive v3](https://github.com/glotlabs/gdrive), not the legacy v2 command syntax. On the Minecraft host, install a supported release and authenticate its account under the **same OS user running Paper**, following the project's remote-host account import instructions when needed. Keep OAuth credentials/tokens in gdrive's protected account storage; never place them in plugin configuration or chat.

Set `gdrive.executable` to the installed executable's path and `gdrive.parent-id` to a writable Google Drive folder ID in `plugins/HollowUtils/config.yml`, then restart. Existing configs need these new keys added manually. The plugin invokes `gdrive files upload --parent <folder-id> <zip-path>` without a shell, asynchronously, with closed standard input and a ten-minute timeout. A nonzero exit or timeout reports failure and retains the local ZIP. CLI output is not echoed into Minecraft chat. `/backup name local` never contacts Drive. No Drive upload has been performed or validated in this workspace because the CLI/account is absent.

Backup remains a world backup, not a complete MVI/profile or plugin-data backup. `/backup` moves players out first and keeps them in lobby afterward; backup fails if another plugin prevents evacuation. The primary lobby still needs a stopped-process backup. ZIP creation unloads worlds and runs synchronously, so use offline backups for large worlds to avoid watchdog timeouts. Drive uploads only the ZIP; retain the accompanying local registry snapshot and follow the full-server disaster-recovery guidance above.

New features compile against Paper 1.21.11; the existing 20 automated tests pass. Live validation of death persistence, bottom teleport, kit GUI/enchantments, per-slot kit restore and gdrive upload remains required on staging. Test with full/cursor inventories, cancelled teleports, permission revocation, death in a different dimension, and two players in separate chat groups.

## Modern moderation

Punishments are scoped to the issuing player's **current logical server**, including all three dimensions. Operators do not bypass bans. Console explicitly selects scope: `ban --server survival Alex Griefing`. All commands take ownership of their unqualified labels just like the Essentials-style commands above.

| Command | Usage |
| --- | --- |
| `/ban`, `/mute`, `/warn` | `<player-or-uuid> [reason]` |
| `/tempban`, `/tempmute` | `<player-or-uuid> <duration> [reason]` |
| `/kick` | `<online-player> [reason]` |
| `/banip` | `<player-or-literal-ip> [reason]` |
| `/unban`, `/unmute` | `<player-or-uuid>` |
| `/unbanip` | `<player-or-literal-ip>` |
| `/unwarn` | `<player-or-uuid> <warning-id-or-unique-prefix\|all>` |
| `/warns` | `<player-or-uuid> [page]` — warning records, including revoked history |
| `/punishments` | `<player-or-uuid> [page]` — currently active punishments |
| `/history` | `<player-or-uuid-or-literal-ip> [page]` — full audit history |
| `/staffrollback` | `<staff-name-or-uuid-or-CONSOLE> [confirm]` — revoke that issuer's active punishments in this logical server |

Each command requires `hollowutils.<command>`, defaulting to operators. Grant staff only the actions they need; `staffrollback` is a powerful separate permission. No selectors, wildcard players or partial-name matching. Offline names must have been seen in the current logical server; explicit UUIDs support offline moderation without external identity lookups. Suggestions show visible online players in the same scope. Reasons are plain text, limited to 300 characters.

Durations accept one positive integer and `s`, `m`, `h`, `d` or `w`, for example `30m`, `12h`, `7d`. The maximum is 3650 days; expiry takes effect automatically even across restarts. Revocations retain the original reason, issuer, creation/expiry timestamps, and record who revoked them. Rollback requires repeating the command with `confirm` within 30 seconds. It revokes active bans, IP bans, mutes and warnings; it cannot undo kicks or restore gameplay changes and does **not** roll back blocks, inventories or worlds. Expired and already revoked entries remain intact.

History displays six records per page, with hover details and clickable IDs that copy the full reference. Next-page links run the corresponding history command. Active/revoked/expired/executed states remain distinct. IP bans are recorded separately; player history also shows bans matching that player's last recorded IP hash in this scope.

A ban removes a player from the affected group to an accessible lobby, or disconnects them if no fallback is available. Teleport and rejoin access checks prevent reentry; a ban in the lobby denies lobby access and disconnects when no fallback is possible. `/kick` disconnects the player from Paper. Mutes block normal Paper chat in the affected group. Third-party messaging commands or custom plugin chat channels require their own mute integration; do not allowlist commands that bypass moderation. Global staff broadcasts remain a privileged command.

`moderation.yml` stores the audit ledger, player-name index and salted IP hashes; raw IP addresses are not persisted by this feature. Literal addresses are parsed without DNS lookups. A name-based IP unban uses the last known address hash even when the player is offline or has moved to lobby. Shared IPs affect every matching player within the chosen scope; proxy deployments must configure trusted address forwarding correctly. Existing Bukkit/Essentials bans are neither imported nor removed, so explicit namespaced commands or pre-existing global ban lists can still affect access independently.

Keep the ledger private and include it in stopped-process full-server backups. World-only `/backup` archives do not include this plugin's moderation data. A malformed ledger locks startup instead of silently discarding punishments. Live moderation integration still needs staging verification: ban/rejoin/portal paths, lobby bans, two scopes with shared IPs, expiry over restart, warning removal, rollback confirmation and chat-plugin coexistence.
