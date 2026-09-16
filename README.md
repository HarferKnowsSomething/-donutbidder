# DonutBidder

Client-side Fabric mod for running chat auctions on Minecraft **1.21.11**.

It reads the payment messages the server already sends you, tracks who the highest bidder is,
runs the countdown, and tells you exactly who to refund when it's over. It never sends, fakes,
or simulates a payment — everything comes from real server output.

## What changed in this rebuild

The first version failed to build because its Gradle setup was guesswork. This one is copied
from the **official FabricMC example mod for 1.21.11**, so the plugin, Gradle version and
dependency versions are the real ones:

| | before (broken) | now |
| --- | --- | --- |
| Gradle plugin | `fabric-loom` 1.11-SNAPSHOT | `net.fabricmc.fabric-loom-remap` 1.17-SNAPSHOT |
| Gradle | 8.11 | 9.5.1 |
| Mappings | Yarn (guessed build number) | `loom.officialMojangMappings()` — no version to get wrong |
| Loader / API | guessed | 0.19.5 / 0.141.6+1.21.11 |
| Config cache | on (caused your error report) | off, as Fabric recommends |

Because 1.21.11 uses Mojang mappings, the Minecraft-facing code was rewritten
(`Minecraft`, `GuiGraphics`, `Component`, `Identifier`, `KeyMapping`…). Every API used here was
checked against Fabric's actual 1.21.11 source rather than from memory — including the renamed
`net.minecraft.resources.Identifier` and the new `KeyMapping.Category.register(...)`.

## Features

- **Detection** — matches `<player> paid you $ <amount>` with `K / M / B / T` suffixes, commas,
  and leftover colour codes. `You paid <player> $ ...` is ignored so your own refunds never
  count as bids.
- **Highest bidder tracking** — updates the moment someone outbids, naming who got outbid.
- **Timer + anti-snipe** — a bid in the last N seconds pushes the clock back out.
- **Minimum raise** — payments that don't reach `highest + raise` are rejected and flagged for
  refund instead of taking the lead.
- **Bid modes** — payments from one player either stack up, or only their largest counts.
- **Refund list** — `/bidder refunds` prints `/pay <name> <amount>` for every losing bidder.
- **Auto log** — each finished auction is saved to `.minecraft/donutbidder/auction_<time>.txt`.
- **HUD panel** — item, live clock, leader, progress bar, standings. Money in `0x00FC00`.

## Controls

Open with `/bidder` for a status readout and the full command list.

| Command | What it does |
| --- | --- |
| `/bidder` | status + help |
| `/bidder item <name>` | what you're selling |
| `/bidder startbid <amount>` | opening bid (`5m`, `750k`, `1200000`) |
| `/bidder raise <amount>` | minimum raise between bids |
| `/bidder time <seconds>` | auction length |
| `/bidder snipe <seconds>` | anti-snipe window (`0` disables) |
| `/bidder start` / `stop` / `reset` | run it |
| `/bidder refunds` | who to pay back |
| `/bidder mode` / `hud` | toggle stacking bids / the panel |
| **Right Shift** | expand or shrink the panel |

## Getting the jar

I couldn't compile it for you — building a Fabric mod downloads Minecraft from Mojang and the
mappings from `maven.fabricmc.net`, and both are blocked in the sandbox I run in. Two ways to
get the jar yourself:

**GitHub Actions (no local setup).** Push this folder to a GitHub repo. `.github/workflows/build.yml`
is already included, so the Actions tab builds it automatically and the finished jar appears as a
downloadable artifact on the run.

**Locally.** Install a JDK 21, then in this folder run `./gradlew build` (`gradlew.bat build` on
Windows). First run downloads Gradle, Minecraft and the libraries, so give it a few minutes.
The jar lands in `build/libs/`.

Then drop the jar in `mods/` alongside Fabric API.

## What was verified, and what wasn't

Tested here before shipping:

- the payment parser, against every line from your screenshot plus commas and colour codes
- the full auction lifecycle — rejected low bids, stacking, outbids, anti-snipe extension,
  winner selection, refund list and the exported log — run end-to-end against stubs

Not testable here: the Minecraft API calls themselves, since the game can't be downloaded in my
sandbox. Those were checked line by line against Fabric's 1.21.11 sources. If something still
doesn't compile, paste me the error text and it'll be a quick fix.

## Deliberately left out

To keep the build dependency-free and certain to compile, three nice-to-haves were dropped:
a bid alert sound, a clipboard copy button, and a full click-driven settings screen (mouse and
keyboard event signatures changed again in 1.21.11). All three are easy to add once you've
confirmed the jar builds — just ask.

## Tuning the parser

If your server words it differently, the only thing to edit is `PayParser.PAID_YOU`.
Everything else just consumes `(player, amount)`.
