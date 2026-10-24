# Charter (Fan Recreation) for Minecraft 1.20.1 (Fabric)

An **unofficial fan recreation** of the *Charter* mod from the ContentSMP: golden contracts that bind a soul, the
golden sigils, beams and chains of a judgement, the weapons that can **permanently ban** a player, and the
exile that comes after the patch.

> **Read this first.** This is a clean-room recreation written from the ContentSMP videos (the five transcripts you
> supplied) and the public ContentSMP wiki page for Charter. The original mod is private, none of its code was seen,
> and this project is not affiliated with its authors or with the ContentSMP. Everything the videos show is
> implemented; where they leave a gap, the gap was filled with a reasonable guess and is marked **(inferred)**
> below. The project could not be compiled in the environment it was written in, so see
> [If the build fails](#if-the-build-fails).

## Getting the jar

### Easiest: let GitHub build it (no Java needed)

1. Create a new, empty repository on GitHub and upload **everything in this folder** (keep the folder structure,
   including the hidden `.github` folder).
2. Open the repository's **Actions** tab. The `build` workflow starts by itself on every push (or press
   *Run workflow*).
3. When it turns green, open the run and download the artifact **`charter-jar`**. Inside is
   `charter-fabric-mc1.20.1-1.0.0.jar`.
4. To publish it as a release instead, push a tag such as `v1.0.0`: the jar is attached to the release.

### Or build it yourself

You need **JDK 25** (the current Fabric Loom needs it to *run*; the mod itself is compiled for Java 17):

```
./gradlew build            # Windows: gradlew.bat build
```

The jar appears in `build/libs/`. Use the one without `-dev` or `-sources` in its name.

## Installing

* Minecraft **1.20.1**, Fabric Loader **0.14.21 or newer**, **Fabric API** (built against 0.92.12+1.20.1), Java 17+.
* Put the jar in `mods/`. **Install it on the server *and* on every client**: the effects, the items and the
  missing-arm model need the mod on both sides.
* Nothing needs a resource pack. The first launch writes `config/charter.json`.

## What is in it

| Item / block | What it is |
| --- | --- |
| **Charter Contract** | A blank contract. Use it to sign yourself over to Charter, or use it on a bound player to take their burden. |
| **Signed Contract** | Remembers who signed it. Sneak and use it to tear it up (it costs you your left arm). |
| **Lesser Divinity** | Three uses of a permanent ban. The third destroys it and leaves a crater. |
| **Lesser Divinity Fragment** | A shard brushed out of the crater. |
| **Fractured Lesser Divinity** | The patched, rebuilt version: it exiles instead of banning and never runs out. |
| **Dusk's Epitaph** | A huge rusted greatsword. One full-strength blow on a player chains and bans them. |
| **Divine Dominance** | A tablet that raises a golden cage which slowly closes in. |

Everything except the Signed Contract (you get that by signing) is in the creative tab **Charter**, and every item works with `/give @s charter:<item>` (the ids are `charter_contract`, `signed_contract`, `lesser_divinity`, `lesser_divinity_fragment`, `fractured_lesser_divinity`, `dusk_epitaph` and `divine_dominance`).

### Crafting

* **Charter Contract**: paper + ink sac + gold nugget (shapeless).
* **Fractured Lesser Divinity**: eight Lesser Divinity Fragments in a ring around a **Signed Contract**. *(inferred)*
* The Lesser Divinity, Dusk's Epitaph and Divine Dominance cannot be crafted; on the ContentSMP they were obtained by
  deals and trades. Hand them out with `/give`, or have the crater give you the fragments.

## How it plays

### Contracts and the left arm
* Using a blank contract binds you to Charter. You are wreathed in slowly circling golden motes, and you can walk
  through the walls of Divine Dominance.
* A contract cannot just be dropped. **Severing it frees whoever bore it, but costs them their left arm**, and the
  burden does not vanish: it passes to whoever takes it. Use a blank contract on a bound player (they must crouch to
  accept) and *they* are freed and lose the arm while *you* become bound.
* Tearing up your own signed contract (sneak + use) also frees you and also costs the arm.
* A player without a left arm has it **missing from their model** (third person and first person, for everyone who
  has the mod) and **cannot use their off hand**: whatever lands there is put away. `/charter arm restore <player>`
  gives it back; by default dying does not.

### Lesser Divinity
Point it at the ground or at a creature (up to 96 blocks). A golden **sigil** is drawn, burns brighter for four
seconds, and then a **beam** falls: every player standing in the sigil (except the wielder) is **permanently
banned**: their inventory drops where they stood, they are added to the server's ban list, and they are disconnected
with the Charter message. Charter also keeps its own list and kicks the judged on join even if the ban list is edited.
The **third use destroys the item**: it blows up into a huge crater whose **suspicious sand and gravel** each hide a
**Lesser Divinity Fragment** (brush them out with a brush).

### Fractured Lesser Divinity
The patched version. It no longer bans: the victim is **exiled 7,777,840 blocks** from the world spawn. Exiled
players **cannot die** (lethal damage leaves them on half a heart) until they are within 25,000 blocks of home, **cannot
break, place or use blocks**, and the exile ends when they get back within 1,000 blocks of where they were thrown
from. It does not run out.

### Dusk's Epitaph
A slow, heavy greatsword. Against anything but a player it is just a heavy blade. A **fully charged** blow on a
player is not damage: golden chains seize them, they are held in place while a sigil burns under them, then the beam
falls and they are banned.

### Divine Dominance *(inferred from the wiki)*
Place the tablet and a golden square wall rises (48 blocks wide, 48 up and 24 down). After five seconds it starts to
close in and takes two minutes to meet in the middle. Whoever is inside is held in, whoever is outside is kept out;
the placer, Charter-bound players, and spectators pass freely. When the walls meet, everyone left inside is chained
and judged (ban by default). The tablet can only be broken by its owner, a bound player or an operator, and it
shatters once the judgements are done. Right-click it to read how long is left.

## Commands

All need permission level 2 (operator).

| Command | Effect |
| --- | --- |
| `/charter bind <players>` | Bind to Charter at no price. |
| `/charter unbind <players>` | Lift a contract at no price (no arm lost). |
| `/charter sever <players>` | Sever a contract: they are freed and lose the left arm. |
| `/charter arm remove\|restore <players>` | Take or give back the left arm. |
| `/charter judge <players> [ban\|exile\|kick\|kill]` | Chain and judge (default `ban`). |
| `/charter sigil <pos> <verdict> [radius]` | Draw a sigil that judges whoever stands in it. |
| `/charter exile <players>` | Exile at once. |
| `/charter release <players> [home]` | End an exile (optionally teleporting them home). |
| `/charter pardon <name>` | Remove a ban placed by Charter. |
| `/charter crater <pos>` | Test the crater of the Lesser Divinity. |
| `/charter list` | Who is bound, armless, exiled and judged. |
| `/charter reload` | Re-read `config/charter.json`. |

## Configuration

`config/charter.json`, re-read with `/charter reload`. Missing keys fall back to the defaults; delete the file to
reset it. Tick values are in game ticks (20 per second).

#### Permanent ban

| Key | Default | What it does |
| --- | --- | --- |
| `permanentBans` | `true` | Master switch. When false every "BAN" verdict is replaced by `fallbackVerdict`. |
| `fallbackVerdict` | `EXILE` | What happens instead of a ban when a ban is switched off or the target is exempt: EXILE or KICK. |
| `exemptOperators` | `false` | Operators cannot be banned when true. Off by default: on the ContentSMP even the server owner was banned. |
| `exemptSingleplayerHost` | `true` | The host of a singleplayer / LAN world is never banned (they would lose their own world). |
| `dropInventoryOnBan` | `true` | Banned players drop their whole inventory where they stood, as if they had died. |
| `useVanillaBanList` | `true` | Add the player to the vanilla ban list (banned-players.json). |
| `enforceOnJoin` | `true` | Charter keeps its own list of judged players and kicks them on join even if the vanilla list was edited. |
| `banSource` | `Charter` | Source name written to the vanilla ban list. |
| `banReason` | `Judged by Charter.` | Reason written to the vanilla ban list. |
| `kickMessage` | `Charter has judged you.\nYour name has been struck from this world.` | Shown on the disconnect screen. "\n" starts a new line. |
| `broadcastJudgements` | `true` | Announce every ban in chat. |
| `broadcastMessage` | `%s has been judged by Charter.` | %s is replaced by the player's name. |

#### Telegraph

| Key | Default | What it does |
| --- | --- | --- |
| `sigilTelegraphTicks` | `80` | Ticks (20 per second) between the golden sigil appearing and the strike. |
| `chainTelegraphTicks` | `40` | Ticks a target stays bound in golden chains before Dusk's Epitaph finishes the judgement. |
| `strikeBeamTicks` | `30` | How long the golden beam of the strike stays visible. |
| `sigilRadius` | `5.0` | Radius in blocks of the sigil the Lesser Divinity draws; everyone inside it is judged. |

#### Lesser Divinity

| Key | Default | What it does |
| --- | --- | --- |
| `lesserDivinityCharges` | `3` | Uses before the Lesser Divinity destroys itself. |
| `lesserDivinityRange` | `96.0` | How far (in blocks) the wielder can aim it. |
| `lesserDivinityCooldownTicks` | `60` | Item cooldown after each use. |
| `fracturedCooldownTicks` | `60` | Item cooldown after each use of the Fractured Lesser Divinity. |
| `craterRadius` | `10` | Radius in blocks of the crater left by the last use. |
| `craterDepth` | `5` | Depth in blocks at the centre of that crater. |
| `craterSuspiciousChance` | `0.4` | Chance that a block of the crater becomes suspicious gravel holding a fragment. |
| `detonationPower` | `4.0` | Strength of the blast of the last use. It hurts and throws creatures but never breaks blocks. 0 turns it off. |

#### Dusk's Epitaph

| Key | Default | What it does |
| --- | --- | --- |
| `epitaphMinCharge` | `0.85` | Attack cooldown progress (0 to 1) needed for a blow to ban. 0.85 means "nearly a full swing". |

#### Exile

| Key | Default | What it does |
| --- | --- | --- |
| `exileDistance` | `7777840.0` | Blocks from the world spawn that an exiled player is thrown. |
| `exileDimension` | `minecraft:overworld` | Dimension the exile takes place in. |
| `exileReturnRadius` | `1000` | An exiled player is freed on coming this close to where they were exiled from. |
| `exileUnkillable` | `true` | Exiled players cannot die. Lethal damage leaves them on exileMinHealth. |
| `exileLethalRadius` | `25000` | The exile stops protecting from death this close to home ("now this can actually kill me"). |
| `exileMinHealth` | `1.0` | Health an exiled player is left on instead of dying (1.0 is half a heart). |
| `exileLocksBlocks` | `true` | Exiled players cannot break, place or use blocks: they may look, but not touch. |
| `exileMessages` | `true` | Tell exiled players how far from home they are. |

#### Divine Dominance

| Key | Default | What it does |
| --- | --- | --- |
| `dominanceStartRadius` | `24.0` | Half width of the golden square when the cage closes (blocks from the tablet to each wall). |
| `dominanceEndRadius` | `1.0` | Half width at which the cage has fully closed. |
| `dominanceArmTicks` | `100` | Ticks between placing the tablet and the walls starting to move. |
| `dominanceShrinkTicks` | `2400` | Ticks the walls take to close in. |
| `dominanceHeightUp` | `48` | Blocks the walls reach above the tablet. |
| `dominanceHeightDown` | `24` | Blocks the walls reach below the tablet. |
| `dominanceVerdict` | `BAN` | What happens to everyone still inside when the cage closes: BAN or KILL. |
| `dominanceCrushDamage` | `4.0` | Damage per second once the walls are squeezing (only the last few blocks). |
| `dominanceProtectedTablet` | `true` | The tablet can only be broken by its owner or a Charter-bound player. |

#### Contracts

| Key | Default | What it does |
| --- | --- | --- |
| `contractTakeoverNeedsConsent` | `true` | Using a blank contract on a bound player takes their contract over: they are freed (and lose their left arm) and the taker is bound instead. When true the bound player has to be sneaking, which is their consent. |
| `contractMayBeTornUp` | `true` | A bound player may tear up their own signed contract: they are freed, and lose their left arm. |
| `severedLocksOffhand` | `true` | A severed player cannot use their off hand. |
| `severedClearsOnDeath` | `false` | When true a lost arm grows back on death. Off by default: in the lore only Charter can give it back. |
| `boundParticles` | `true` | Charter-bound players are wreathed in golden chain particles. |
| `weaponsRequireContract` | `false` | When true the Lesser Divinity, Dusk's Epitaph and Divine Dominance only answer to bound players. |


## What is faithful and what is a guess

**Taken from the videos:** the golden beams and chains; the permanent ban (title screen, never again); the contract
that costs the *freed* bearer their left arm while the burden moves to whoever takes it; a severed player not showing
their left arm and not using their off hand; the Lesser Divinity (a sigil, then a beam, three uses, the last
detonating into a giant crater of suspicious blocks hiding pieces); the patched version that exiles about
7,777,840 blocks, cannot die until about 25,000 blocks from home, and cannot touch blocks; Dusk's Epitaph as a rusted
greatsword that chains and bans.

**Inferred (the sources do not say):** exact timings, radii and damage numbers; the crafting recipes; the textures
and models; how Divine Dominance behaves in detail (it comes from the wiki, not the videos); the consent rule when
taking over a contract; the particle effects; the wording of every message.

**Not included:** the *Mark of the Benefactor*, the "Charter clothes", the Mouthpiece NPC, and the features of the
public 1.19.2 Charter mod (claims, vessels and the rest). The design is ready for them but they were out of scope.

## If the build fails

The code was checked as far as possible without a compiler (every import and every Minecraft call was checked
against the 1.20.1 Yarn mappings, and all sources pass a Java syntax check), but it could not be compiled here. If
GitHub's build goes red, open the failed run, copy the first few lines that contain `error:` and send them back:
a mistake of this kind is normally a one-line fix.

## Project layout

```
src/main/java/net/charter/     common code (items, block, commands, logic, state, config)
src/client/java/net/charter/   client code (renderer for the golden effects, missing-arm mixin)
src/main/resources/            mod metadata, assets (models, textures, lang), data (recipes, loot, damage types)
docs/SPEC.md                   the design notes behind each mechanic
```

## Credits

Original Charter mod and the ContentSMP belong to their creators. This fan recreation is MIT licensed (see
`LICENSE`); the original mod's own licence is not affected by it. Built with Fabric.
