# Command permissions

Every registered HollowUtils command is checked at command registration, execution and completion. Aliases share their canonical command permission. Default `true` means everyone; `op` means operators. Give additional `.others` permissions only when staff should target other players.

| Command | Required permission | Default |
| --- | --- | --- |
| `/back` | `hollowutils.back` | `true` |
| `/backup` | `hollowutils.backup` | `op` |
| `/backupserver` | `hollowutils.backupserver` | `op` |
| `/ban` | `hollowutils.ban` | `op` |
| `/banip` | `hollowutils.banip` | `op` |
| `/bc` | `hollowutils.broadcast` | `op` |
| `/bottom` | `hollowutils.bottom` | `op` |
| `/broadcast` | `hollowutils.broadcast` | `op` |
| `/ci` | `hollowutils.clear` | `op` |
| `/clear` | `hollowutils.clear` | `op` |
| `/clearinventory` | `hollowutils.clear` | `op` |
| `/createhome` | `hollowutils.createhome` | `true` |
| `/createkit` | `hollowutils.createkit` | `op` |
| `/createserver` | `hollowutils.createserver` | `op` |
| `/createwarp` | `hollowutils.createwarp` | `op` |
| `/day` | `hollowutils.day` | `op` |
| `/deletehome` | `hollowutils.deletehome` | `true` |
| `/deletekit` | `hollowutils.deletekit` | `op` |
| `/deleteserver` | `hollowutils.deleteserver` | `op` |
| `/deletewarp` | `hollowutils.deletewarp` | `op` |
| `/enchant` | `hollowutils.enchant` | `op` |
| `/fly` | `hollowutils.fly` | `op` |
| `/flyspeed` | `hollowutils.flyspeed` | `op` |
| `/gamemode` | `hollowutils.gamemode` | `op` |
| `/give` | `hollowutils.give` | `op` |
| `/gm` | `hollowutils.gamemode` | `op` |
| `/gma` | `hollowutils.gamemode` | `op` |
| `/gmc` | `hollowutils.gamemode` | `op` |
| `/gms` | `hollowutils.gamemode` | `op` |
| `/gmsp` | `hollowutils.gamemode` | `op` |
| `/god` | `hollowutils.god` | `op` |
| `/heal` | `hollowutils.heal` | `op` |
| `/history` | `hollowutils.history` | `op` |
| `/home` | `hollowutils.home` | `true` |
| `/homegui` | `hollowutils.homegui` | `true` |
| `/i` | `hollowutils.i` | `op` |
| `/kick` | `hollowutils.kick` | `op` |
| `/kit` | `hollowutils.kit` | `op` |
| `/loadserver` | `hollowutils.loadserver` | `op` |
| `/maintenanceserver` | `hollowutils.maintenanceserver` | `op` |
| `/message` | `hollowutils.msg` | `true` |
| `/msg` | `hollowutils.msg` | `true` |
| `/mute` | `hollowutils.mute` | `op` |
| `/night` | `hollowutils.night` | `op` |
| `/pm` | `hollowutils.msg` | `true` |
| `/privatemanager` | `hollowutils.msg` | `true` |
| `/punishments` | `hollowutils.punishments` | `op` |
| `/rain` | `hollowutils.rain` | `op` |
| `/server` | `hollowutils.server` | `true` |
| `/setspawn` | `hollowutils.setspawn` | `op` |
| `/socialspy` | `hollowutils.socialspy` | `op` |
| `/spawn` | `hollowutils.spawn` | `true` |
| `/staffrollback` | `hollowutils.staffrollback` | `op` |
| `/sun` | `hollowutils.sun` | `op` |
| `/sunrise` | `hollowutils.sunrise` | `op` |
| `/sunset` | `hollowutils.sunset` | `op` |
| `/thunder` | `hollowutils.thunder` | `op` |
| `/togglebreakdelay` | `hollowutils.togglebreakdelay` | `op` |
| `/unban` | `hollowutils.unban` | `op` |
| `/unbanip` | `hollowutils.unbanip` | `op` |
| `/unloadserver` | `hollowutils.unloadserver` | `op` |
| `/unmute` | `hollowutils.unmute` | `op` |
| `/unwarn` | `hollowutils.unwarn` | `op` |
| `/walkspeed` | `hollowutils.walkspeed` | `op` |
| `/warn` | `hollowutils.warn` | `op` |
| `/warns` | `hollowutils.warns` | `op` |
| `/warp` | `hollowutils.warp` | `true` |
| `/warpgui` | `hollowutils.warpgui` | `true` |
| `/whisper` | `hollowutils.msg` | `true` |
| `/whitelistserver` | `hollowutils.whitelistserver` | `op` |

Additional and legacy permissions:

| Permission | Default |
| --- | --- |
| `hollowutils.admin` | `op` |
| `hollowutils.bottom.others` | `op` |
| `hollowutils.bypass` | `op` |
| `hollowutils.clear.others` | `op` |
| `hollowutils.enchant.unsafe` | `op` |
| `hollowutils.fly.others` | `op` |
| `hollowutils.flyspeed.others` | `op` |
| `hollowutils.gamemode.adventure` | `op` |
| `hollowutils.gamemode.creative` | `op` |
| `hollowutils.gamemode.others` | `op` |
| `hollowutils.gamemode.spectator` | `op` |
| `hollowutils.gamemode.survival` | `op` |
| `hollowutils.give.others` | `op` |
| `hollowutils.god.others` | `op` |
| `hollowutils.heal.others` | `op` |
| `hollowutils.kit.others` | `op` |
| `hollowutils.server.others` | `op` |
| `hollowutils.setspawn.others` | `op` |
| `hollowutils.spawn.others` | `op` |
| `hollowutils.use` | `true` |
| `hollowutils.walkspeed.others` | `op` |

`hollowutils.use` remains a parent for `hollowutils.server` and `hollowutils.spawn`. `hollowutils.admin` remains a parent for the original core administrative commands: createserver, backupserver, whitelistserver, deleteserver, maintenanceserver and setspawn. Other command families use the explicit permissions above. The runtime gate cannot be bypassed by adding a HollowUtils command to `allowed-commands`.

`hollowutils.enchant.unsafe` is required for enchantments above their normal maximum or for incompatible/conflicting enchantments; it cannot bypass Minecraft’s native level 255 storage limit. `hollowutils.bypass` bypasses whitelist and maintenance, not bans or unloaded/deleting groups. God mode granted to someone else does not require them to hold the command permission; staff can turn it off and it clears on logout or logical-server change.

Legacy grants to `hollowutils.admin`/`hollowutils.use` remain supported. Granting a single core command no longer requires granting the entire legacy admin group.
