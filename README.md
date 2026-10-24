![packpng](https://github.com/birdflop/birdmoji/blob/main/common/src/main/resources/pack.png?raw=true)
# Birdmoji

An addon for [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) and [DiscordSRV Ascension](https://github.com/DiscordSRV/Ascension) by [Birdflop](https://birdflop.com) that brings your Discord server's custom emojis into Minecraft chat as sprites.

Birdmoji comes as two plugins. Install the one for your DiscordSRV:

| Plugin | For |
| --- | --- |
| `Birdmoji-DiscordSRV.jar` | DiscordSRV 1.30.5+ |
| `Birdmoji-Ascension.jar` | DiscordSRV Ascension (DiscordSRV 3) |

- Emojis sent from Discord show up as sprites in Minecraft chat. Animated emojis stay animated.
- Players can type `:emoji:` in Minecraft chat, with tab completion. Other players see the sprite, and Discord shows the real emoji.
- Birdmoji builds the resource pack from your Discord server's emojis. It rebuilds the pack when emojis are added, removed or renamed.
- The pack is hosted by the plugin and sent to players when they join. It is sent alongside your server's own resource pack instead of replacing it.
- Players who haven't loaded the pack see `:emoji:` instead of a missing texture.
- Emojis sent with FakeNitro (sent as a link like `[emoji](https://cdn.discordapp.com/emojis/...)`) show as sprites too. Emojis from other Discord servers aren't in the pack, so they show as `:emoji:` instead of a link.
- Players can also type standard Unicode emoji shortcodes, like `:smile:` or `:+1:`, the same way Discord itself resolves them. These turn into the real Unicode emoji character rather than a sprite, so they reach Discord as an actual emoji. Minecraft doesn't render most Unicode emojis on its own; pair this with a font resource pack like [Twemoji Visibility](https://modrinth.com/resourcepack/twemoji-visibility) if you want them to show as proper glyphs instead of your client's fallback. Disable with `unicode-shortcodes: false` in `config.yml`.
  - If a Discord emoji has the same name as a Unicode shortcode (e.g. a custom `:fire:`), the Unicode emoji wins for `:fire:` and the Discord emoji is typed as `:fire~1:` instead.

## Requirements

- Paper 1.21.9+ (sprite text components were added in 1.21.9)
- DiscordSRV 1.30.5+ with `DiscordChatChannelEmoteBehavior: "name"` (the default), or DiscordSRV Ascension with `custom-emoji-behaviour` not set to `hide`

## Setup

1. Put the Birdmoji jar for your DiscordSRV in `plugins/` and restart.
2. Open the port in `resource-pack.port` (default `8164`). On most hosts, this means adding another port (allocation) to your server and setting it in `config.yml`.
3. If players connect through a domain or a proxy, set `resource-pack.public-url` to the address players can reach, e.g. `http://play.example.com:8164`.

To include the emojis in your own resource pack instead, set `resource-pack.enabled: false` and merge `Birdmoji.zip` from Birdmoji's plugin folder into your pack.

### DiscordSRV Ascension

Ascension only shows custom emojis from Discord if it caches them, which an addon has to ask for before Ascension connects to Discord. Since that happens before Birdmoji loads, Birdmoji reconnects Ascension to Discord once at startup. You'll see `Reconnecting DiscordSRV to Discord so it can see custom emojis` in the console.

If Ascension's bot is in more than one Discord server, set `guild-id` in `config.yml`.

Emojis can also be used anywhere MiniMessage is supported:

```
<sprite:"minecraft:gui":"birdflop:gif/pog">
```

Texture names are the emoji name in lowercase. If two emojis only differ by capitalization, the newer one gets `-1` appended.

Emojis are forced to white by default so a colored message or name doesn't tint them. Set `prevent-tint: false` in `config.yml` to let them pick up the surrounding color instead.

## Commands

- `/emojis [page]` (`birdmoji.list`, everyone by default): lists the emojis. Click one to put it in the chat box, or shift-click to insert it where you're typing. Aliases: `/emoji`, `/emotes`.
- `/birdmoji reload` (`birdmoji.reload`): reloads `config.yml` and rebuilds the pack.

## Limitations

- With DiscordSRV 1.x, sprites in messages from Discord are only shown when DiscordSRV isn't hooked into a chat plugin. Chat hooks send messages through the chat plugin, which usually converts them to legacy text.

## Building

```sh
./gradlew build
```

The plugins are written to `discordsrv/build/libs/` and `ascension/build/libs/`. Shared code is in `common` and bundled into both.

DiscordSRV Ascension's API isn't published to a Maven repository yet, so `ascension/libs/` has a copy built from [its source](https://github.com/DiscordSRV/Ascension) with `./gradlew :api:shadowJar`.
