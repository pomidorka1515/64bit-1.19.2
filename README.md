# 64-bit Minecraft. It's real!
What this projects does: ports all X/Z coordinates to `long`, with a new sector-based movement/collision system.
The original, incomplete mod was made by mckuhei. 
Their websites are now down. If you want to get the original mod's code, you can checkout a specific commit, before my modifications:
 - `f1b40fcf28655ebf01aa5dd77bc8e0cdc9dc97af`
Beware that the original mod is not even functional and has lots of bugs. Use this fork for a playable version.

# Development notice
As of **1st September**, development will be drastically slower.
This is because im still in school and, unfortunately, will have less free time.
If you want to help and contribute, dm me on discord! `@pomidorka1515`

# Playing this
**Currently the mod is quite unstable. Dont expect a smoooth experience.**
 - download the jar from the [latest release](https://pomi.site/ec952ebae0f530313056/pomi/64bit-1.19.2/releases)
 - install [prism](https://prismlauncher.org) if you havent
 - open Prism, create a **vanilla** 1.19.2 instance
 - open its settings, then click "Version"
 - click "Add to Minecraft.jar" -> select the jar you've downloaded
 - playy


# TODOs (regular people can ignore this)
- Make all entities use SectorVec3
- Make all block entities use SectorVec3
- Sound system
- ~~Particles~~
- ~~Structures crash game at boundary with no logs??~~
- ~~Random collision issues at boundary, quicksand like~~
- ~~Light update border (black outline) flashing after chunk reload~~ keeping this in
- A billion generation issues deadlocking the server thread with also no logs

## Original mod history & backport status
```
==== history ====
1.0.0:
	  Inital release [+]
=================
1.0.1:
      Add floating point precision display. [+]
      Fix "No chunk holder after ticket has been added" exception [+]
=================
1.0.2:
      Removed world border. [+]
      Add a patch to fix chunk stripe lands. [+]
      Add a patch to fix Frustum cause game froze. [+]
      Add a patch to fix 'chunk out of bound' exception. [+]
=================
1.0.3:
      Add a patch to restore farlands. [+/-]
=================
1.0.4:
       Fix game stuck at "Saving level..." screen. [-]
       Fix cannot interact with entity. [-]
=================
```
