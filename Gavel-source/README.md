# Gavel

All-in-one punishment plugin for Paper 1.21.11. Warns, mutes, kicks, bans, blacklists, freezing, command spy, staff chat, reports, alt lookup, full history. Everything is stored in a local SQLite file (`plugins/Gavel/gavel.db`), no MySQL needed.

## How it works

Type `/warn Steve`, `/mute Steve`, `/ban Steve` etc. with just a name and a menu opens with preset reasons. Click one and it's done. There's also:

- **Custom reason**: opens a popup with a text box for the reason, a duration box (for mutes/bans) and a silent checkbox.
- **Silent toggle**: silent punishments only show up for staff.
- **History**: everything that player has had.

You can still do it all from chat if you prefer: `/ban Steve 7d x-ray -s`.

`/punish Steve` opens one menu with every option (warn, mute, kick, freeze, ban, blacklist, history).

Ban and blacklist presets ask "are you sure?" first (change `confirm` in the config).

## Commands

| command | aliases | |
| --- | --- | --- |
| `/warn <player> [reason] [-s]` | | warn, opens menu without a reason |
| `/mute <player> [duration] [reason] [-s]` | tempmute | |
| `/kick <player> [reason] [-s]` | | |
| `/ban <player> [duration] [reason] [-s]` | tempban | no duration = permanent |
| `/blacklist <player> [reason] [-s]` | | permanent ban on the account **and** their IP, kicks every account on it |
| `/unwarn <id \| player>` | delwarn | id removes one warning, a name clears them all |
| `/unmute`, `/unban`, `/unblacklist <player> [reason]` | pardon | |
| `/freeze <player>` | ss | toggle |
| `/punish <player>` | p | the main menu |
| `/history <player>` | hist, warnings | menu, shift-click active punishments to remove them |
| `/check <player>` | lookup | banned/muted/blacklisted, warnings, alts, frozen |
| `/alts <player>` | | accounts that joined from the same IP |
| `/commandspy` | cspy, spy | see commands other players run |
| `/staffchat [message]` | sc | no message = toggle, all your chat goes to staff |
| `/report <player> <reason>` | | anyone can use it, staff get a clickable [Punish] button |
| `/gavel reload` | | |

Durations: `30m`, `2h`, `7d`, `2w`, `1mo`, `1y`, combos like `1d12h`, or `perm`.

## Freeze

Frozen players can look around but can't move, break, place, hit, drop items, open their inventory or use commands (except the ones in `freeze.allowed-commands`). They can't take damage either. A big FROZEN title stays on their screen, and their chat only goes to staff, so you can talk them through a screenshare.

If they log out while frozen they get banned automatically (`freeze.ban-on-logout`). Server restarts and kicks don't count.

## Ban screen

The kick/join screens for bans, blacklists and kicks are fully custom in `screens:` in the config. Placeholders: `<reason> <staff> <duration> <expires> <id> <appeal> <player>`. Set your discord in `appeal:`.

## Other stuff

- **Warn escalation**: 3 warnings = 1h mute, 5 = 1 day ban, 7 = perm ban by default. Change or turn off in `warn-escalation`.
- Muted players also can't use `/msg`, `/r` and so on (`muted-commands`).
- Staff get told when a banned player tries to join.
- Blacklisted IPs stop new alt accounts from joining too.
- Every message is in the config, MiniMessage format.

## Permissions

`gavel.*` gives everything (ops have it by default). Individual ones: `gavel.warn`, `gavel.mute`, `gavel.kick`, `gavel.ban`, `gavel.blacklist`, `gavel.unwarn`, `gavel.unmute`, `gavel.unban`, `gavel.unblacklist`, `gavel.freeze`, `gavel.history`, `gavel.check`, `gavel.alts`, `gavel.alts.ip` (see IPs in /check), `gavel.punish`, `gavel.commandspy`, `gavel.staffchat`, `gavel.notify` (see staff alerts), `gavel.reload`.

`gavel.report` is for everyone by default.

Not given to ops automatically:
- `gavel.bypass`: can't be punished or frozen except from console. Give it to owners.
- `gavel.commandspy.exempt`: your commands don't show in command spy.

## Building

```
mvn package
```
