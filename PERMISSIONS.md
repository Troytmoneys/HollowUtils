# Command permissions

Every registered HollowUtils command is checked at registration, execution and completion. Aliases share their canonical permission. Default `true` means everyone; `op` means operators. Give additional `.others` permissions only when staff should target other players.

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
| `/disguise` | `hollowutils.disguise` | `op` |
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
| `/instabreak` | `hollowutils.togglebreakdelay` | `op` |
| `/kick` | `hollowutils.kick` | `op` |
| `/kit` | `hollowutils.kit` | `op` |
| `/loadserver` | `hollowutils.loadserver` | `op` |
| `/maintenanceserver` | `hollowutils.maintenanceserver` | `op` |
| `/message` | `hollowutils.msg` | `true` |
| `/msg` | `hollowutils.msg` | `true` |
| `/mute` | `hollowutils.mute` | `op` |
| `/night` | `hollowutils.night` | `op` |
| `/ping` | `hollowutils.ping` | `true` |
| `/pm` | `hollowutils.msg` | `true` |
| `/privatemanager` | `hollowutils.msg` | `true` |
| `/punishments` | `hollowutils.punishments` | `op` |
| `/rain` | `hollowutils.rain` | `op` |
| `/rtp` | `hollowutils.rtp` | `true` |
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
| `/vanish` | `hollowutils.vanish` | `op` |
| `/version` | `hollowutils.version` | `true` |
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
| `hollowutils.disguise.name` | `op` |
| `hollowutils.disguise.rank` | `op` |
| `hollowutils.disguise.skin` | `op` |
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
| `hollowutils.ping.others` | `op` |
| `hollowutils.rtp.others` | `op` |
| `hollowutils.server.others` | `op` |
| `hollowutils.setspawn.others` | `op` |
| `hollowutils.spawn.others` | `op` |
| `hollowutils.use` | `true` |
| `hollowutils.vanish.see` | `op` |
| `hollowutils.walkspeed.others` | `op` |

`hollowutils.use` remains a parent for `hollowutils.server` and `hollowutils.spawn`. `hollowutils.admin` remains a parent for the original core administrative commands: createserver, backupserver, whitelistserver, deleteserver, maintenanceserver and setspawn. Other command families use the explicit nodes above. Other plugins’ commands use their own permissions; hiding namespaced suggestions does not remove those commands.

Punishment permissions also require LuckPerms hierarchy checks for player senders: highest applicable inherited group weight must exceed the target. Operator status and cosmetic rank disguises do not bypass this. Reversals protect the original issuer; own records may be revoked and console-issued records require console. There is no player hierarchy bypass node.

`hollowutils.vanish.see` reveals vanished staff only within the same logical server. `hollowutils.disguise.name`, `.skin` and `.rank` govern the corresponding GUI choices and default to operators. Cosmetic rank selection does not grant any real permissions or weight.

`hollowutils.enchant.unsafe` permits above-normal or incompatible/conflicting enchants, but cannot bypass Minecraft’s native level 255 limit. `hollowutils.bypass` bypasses whitelist/maintenance, not bans or unloaded/deleting groups. God mode granted to another player does not require them to hold the command permission and clears on logout or group change.

The legacy allowed-commands setting is no longer used. Namespace roots are hidden from suggestions; third-party commands retain their normal permissions and behavior.
