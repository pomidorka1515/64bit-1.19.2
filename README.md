# Feature tracking
- Rewritten light system
- SectorVec3 coordinate system
- Villager AI & pathfinding converted into 64-bit
- Sky renderer fixed to use camera-relative positions & be long-safe
- Chunk border fog removed
- Client brand modified

# Original mod history & backport status (TODO)
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
