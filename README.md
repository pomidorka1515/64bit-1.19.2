# 64-bit Minecraft. It's real!
I didnt make the original base mod, its made by mckuhei.
But it had a shitton of issues, so im slowly fixing all of them.
# Playing this
There are no releases or builds yet because this is still in development.
If you REALLY want to play:
 - clone this repo
 - `./gradlew runClient`
 - pray to god it works
 - (prob wont work on windows. windows is ass... wait for a release)


# TODOs (regular people can ignore this)
- Make all entities use SectorVec3
- Make all block entities use SectorVec3
- Particles
- Structures crash game at boundary with no logs??
- Random collision issues at boundary, quicksand like
- Light update border (black outline) flashing after chunk reload
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
