# worldgen overflow values

nominal block-coordinate magnitudes N; table boundaries on both axes are ±N.
seed offsets and shifted inputs can change actual onset, so these are scale-based
lattice boundaries, not guaranteed first broken blocks or surface transitions

the seed-0 section below uses actual octave offsets instead

for sharp 64-bit / precise walls use the exact catalog below, not the nominal
64-bit column. some nominal groups also omit call-site scales or combine noises
whose highest active octaves differ; the catalog separates those cases

## Noise inputs

| Input | 32-bit N | 64-bit N |
| --- | ---: | ---: |
| temperature | 2,159,871,506,195 | 9,276,577,482,669,747,516,696 |
| vegetation / erosion_large | 1,079,935,753,098 | 4,638,288,741,334,873,758,348 |
| continentalness / shift_x / shift_z / aquifer_barrier | 16,873,996,143 | 72,473,261,583,357,402,475 |
| erosion / ridge / aquifer_fluid_level_floodedness | 269,983,938,275 | 1,159,572,185,333,718,439,587 |
| temperature_large | 8,639,486,024,780 | 37,106,309,930,678,990,066,784 |
| vegetation_large | 4,319,743,012,390 | 18,553,154,965,339,495,033,392 |
| continentalness_large / aquifer_fluid_level_spread / spaghetti_roughness / ore_vein_a / ore_vein_b / ore_gap | 67,495,984,569 | 289,893,046,333,429,609,897 |
| aquifer_lava / cave_cheese | 4,218,499,036 | 18,118,315,395,839,350,619 |
| pillar | 5,399,678,766 | 23,191,443,706,674,368,388 |
| pillar_rareness / pillar_thickness / spaghetti_2d_elevation / spaghetti_3d_thickness / spaghetti_roughness_modulator / cave_layer / noodle / noodle_thickness | 539,967,876,549 | 2,319,144,370,667,436,879,174 |
| spaghetti_2d_modulator / spaghetti_2d_thickness / spaghetti_3d_rarity | 2,159,871,506,195 | 9,276,577,482,669,747,516,696 |
| cave_entrance | 89,994,646,092 | 386,524,061,777,906,146,529 |
| ore_veininess | 359,978,584,366 | 1,546,096,247,111,624,586,116 |
| noodle_ridge_a / noodle_ridge_b | 101,243,976,853 | 434,839,569,500,144,462,263 |
| jagged | 2,812,333 | 12,078,876,930,559,568 |

## Variable-scale spaghetti

| Rarity | Inputs | 32-bit N | 64-bit N |
| ---: | --- | ---: | ---: |
| 0.5 | spaghetti_2d | 134,991,969,138 | 579,786,092,666,859,219,794 |
| 0.75 | spaghetti_2d / spaghetti_3d_1 / spaghetti_3d_2 | 202,487,953,706 | 869,679,139,000,288,924,525 |
| 1 | spaghetti_2d / spaghetti_3d_1 / spaghetti_3d_2 | 269,983,938,275 | 1,159,572,185,333,718,439,587 |
| 1.5 | spaghetti_3d_1 / spaghetti_3d_2 | 404,975,907,412 | 1,739,358,278,000,577,849,050 |
| 2 | spaghetti_2d / spaghetti_3d_1 / spaghetti_3d_2 | 539,967,876,549 | 2,319,144,370,667,436,879,174 |
| 3 | spaghetti_2d | 809,951,814,824 | 3,478,716,556,001,155,698,100 |

## Base 3D noise (BlendedNoise / Far Lands)

| Stack | 32-bit N | 64-bit N |
| --- | ---: | ---: |
| min/max limits | 12,550,825 | 53,905,378,846,979,746 |
| main selector | 1,004,065,925 | 4,312,430,307,758,379,258 |

## SurfaceSystem / SurfaceRules noise inputs

| Input / use | Scale | Highest active octave | 32-bit N | 64-bit N |
| --- | ---: | ---: | ---: | ---: |
| surface | 1 | -4 | 33,747,992,285 | 144,946,523,166,714,804,949 |
| surface_secondary | 1 | -3 | 16,873,996,143 | 72,473,261,583,357,402,475 |
| clay_bands_offset | 1 | -8 | 539,967,876,549 | 2,319,144,370,667,436,879,174 |
| badlands_pillar | 0.2 | 1 | 5,273,123,795 | 22,647,894,244,799,187,656 |
| badlands_pillar_roof | 0.75 | -8 | 719,957,168,732 | 3,092,192,494,223,249,172,232 |
| badlands_surface / iceberg_surface | 1 | -4 | 33,747,992,285 | 144,946,523,166,714,804,949 |
| iceberg_pillar | 1.28 | -3 | 13,182,809,487 | 56,619,735,611,997,971,069 |
| iceberg_pillar_roof | 1.17 | -3 | 14,422,218,925 | 61,942,958,618,254,190,842 |
| swamp | 1 | -2 | 8,436,998,072 | 36,236,630,791,678,701,238 |
| calcite | 1 | -6 | 134,991,969,138 | 579,786,092,666,859,219,794 |
| gravel / packed_ice | 1 | -5 | 67,495,984,569 | 289,893,046,333,429,609,897 |
| powder_snow / nether_state_selector | 1 | -3 | 16,873,996,143 | 72,473,261,583,357,402,475 |
| ice | 1 | -1 | 4,218,499,036 | 18,118,315,395,839,350,619 |
| soul_sand_layer / gravel_layer | 1 | 0 | 2,109,249,518 | 9,059,157,697,919,675,310 |
| patch / netherrack / nether_wart | 1 | 0 | 2,109,249,518 | 9,059,157,697,919,675,310 |

## Other walls, seed 0, 64-bit / precise

N below is the first integer input with an extrapolating fraction in the
highest active octave, **after** NoiseSampling.route. both stacks, X/Z and
both signs were searched independently, checking the inward neighbor each time.
for these unshifted tests all four magnitudes match, so boundaries are ±N on
each axis. entries are not rounded nominal limits or earlier fallback entries

these are noise-input walls, not a guarantee that every surface has a visible
wall. later stacks/octaves and branch selection still matter. where this input
feeds the overworld's 4-block interpolated density, the first affected positive
grid plane is `4 * ceil(N / 4)`; its inward plane is four blocks earlier.
negative planes mirror those. surface rules are not sampled on that same grid

### Fixed-scale inputs

| Input / use | horizontal scale | second stack N | first stack N |
| --- | ---: | ---: | ---: |
| aquifer_lava / cave_cheese / ice | 1 | 18,118,315,395,839,352,631 | 18,446,744,073,709,553,665 |
| pillar | 25 | 23,191,443,706,674,373,537 | 23,611,832,414,348,231,312 |
| aquifer_barrier | 0.5 | 144,946,523,166,714,829,087 | 147,573,952,589,676,437,504 |
| aquifer_fluid_level_floodedness | 0.67 | 1,730,704,754,229,430,738,800 | 1,762,077,045,846,882,639,053 |
| aquifer_fluid_level_spread | 0.7142857142857143 | 405,850,264,866,801,513,398 | 413,207,067,251,093,998,633 |
| pillar_rareness / pillar_thickness / spaghetti_2d_elevation / spaghetti_3d_thickness / spaghetti_roughness_modulator / cave_layer / noodle / noodle_thickness | 1 | 2,319,144,370,667,437,144,697 | 2,361,183,241,434,822,877,184 |
| spaghetti_roughness / ore_gap | 1 | 289,893,046,333,429,650,128 | 295,147,905,179,352,866,816 |
| ore_vein_a / ore_vein_b | 4 | 289,893,046,333,429,650,128 | 295,147,905,179,352,866,816 |
| spaghetti_2d_modulator / spaghetti_2d_thickness / spaghetti_3d_rarity | 2 | 9,276,577,482,669,748,554,649 | 9,444,732,965,739,291,484,160 |
| cave_entrance | 0.75 | 386,524,061,777,906,194,806 | 393,530,540,239,137,150,294 |
| ore_veininess | 1.5 | 1,546,096,247,111,624,763,132 | 1,574,122,160,956,548,584,790 |
| noodle_ridge_a / noodle_ridge_b | 2.6666666666666665 | 434,839,569,500,144,516,574 | 442,721,857,769,029,318,657 |
| jagged | 1500 | 12,078,876,930,559,569 | 12,297,829,382,473,036 |

the single-scale cave_layer call's `8` scales Y only. the spaghetti thickness
modulator's two-scale call uses X/Z=2; these are different overloads

### Shifted climate inputs, zero-shift references

these rows turn off the added shift for the boundary calculation, **not** for
world generation. actual climate inputs add shift_x / shift_z, which depend on
position and can already have overflowed. these are exact zero-shift reference
walls, not exact global biome walls. offset is the noise underlying shift_x/z;
ShiftB rotates world X into the noise Y axis

| Input | horizontal scale | second stack N | first stack N |
| --- | ---: | ---: | ---: |
| temperature | 0.25 | 9,276,577,482,669,748,554,649 | 9,444,732,965,739,291,484,160 |
| vegetation / erosion_large | 0.25 | 4,638,288,741,334,874,281,348 | 4,722,366,482,869,645,746,176 |
| continentalness / offset | 0.25 | 72,473,261,583,357,418,567 | 73,786,976,294,838,222,848 |
| erosion / ridge | 0.25 | 1,159,572,185,333,718,576,372 | 1,180,591,620,717,411,442,688 |
| temperature_large | 0.25 | 37,106,309,930,678,994,194,458 | 37,778,931,862,957,165,912,064 |
| vegetation_large | 0.25 | 18,553,154,965,339,497,101,252 | 18,889,465,931,478,582,960,128 |
| continentalness_large | 0.25 | 289,893,046,333,429,650,128 | 295,147,905,179,352,866,816 |

### Variable-scale spaghetti

each rarity is tested as a fixed branch. the actual rarity mapper selects the
branch from position-dependent noise; there is no single global spaghetti wall

| Rarity | Inputs | second stack N | first stack N |
| ---: | --- | ---: | ---: |
| 0.5 | spaghetti_2d | 579,786,092,666,859,292,209 | 590,295,810,358,705,725,440 |
| 0.75 | spaghetti_2d / spaghetti_3d_1 / spaghetti_3d_2 | 869,679,139,000,289,027,114 | 885,443,715,538,058,631,169 |
| 1 | spaghetti_2d / spaghetti_3d_1 / spaghetti_3d_2 | 1,159,572,185,333,718,576,372 | 1,180,591,620,717,411,442,688 |
| 1.5 | spaghetti_3d_1 / spaghetti_3d_2 | 1,739,358,278,000,578,048,193 | 1,770,887,431,076,117,256,193 |
| 2 | spaghetti_2d / spaghetti_3d_1 / spaghetti_3d_2 | 2,319,144,370,667,437,144,697 | 2,361,183,241,434,822,877,184 |
| 3 | spaghetti_2d | 3,478,716,556,001,156,090,350 | 3,541,774,862,152,234,506,241 |

### SurfaceSystem / SurfaceRules

these inputs only affect locations where the corresponding surface rule or
biome feature is used. they do not all create terrain-density walls

| Input / use | horizontal scale | second stack N | first stack N |
| --- | ---: | ---: | ---: |
| surface / badlands_surface / iceberg_surface / packed_ice / nether_state_selector | 1 | 144,946,523,166,714,829,087 | 147,573,952,589,676,437,504 |
| surface_secondary / powder_snow | 1 | 72,473,261,583,357,418,567 | 73,786,976,294,838,222,848 |
| clay_bands_offset | 1 | 2,319,144,370,667,437,144,697 | 2,361,183,241,434,822,877,184 |
| badlands_pillar | 0.2 | 22,647,894,244,799,192,685 | 23,058,430,092,136,943,360 |
| badlands_pillar_roof | 0.75 | 3,092,192,494,223,249,520,899 | 3,148,244,321,913,097,164,118 |
| iceberg_pillar | 1.28 | 56,619,735,611,997,983,641 | 57,646,075,230,342,360,400 |
| iceberg_pillar_roof | 1.17 | 61,942,958,618,254,204,596 | 63,065,791,704,989,937,889 |
| swamp (surface_swamp) | 1 | 36,236,630,791,678,709,284 | 36,893,488,147,419,111,424 |
| calcite | 1 | 579,786,092,666,859,292,209 | 590,295,810,358,705,725,440 |
| gravel | 1 | 289,893,046,333,429,650,128 | 295,147,905,179,352,866,816 |
| ice | 1 | 18,118,315,395,839,352,631 | 18,446,744,073,709,553,665 |
| soul_sand_layer / gravel_layer / patch / netherrack / nether_wart | 1 | 9,059,157,697,919,676,316 | 9,223,372,036,854,776,833 |

### Base 3D noise

default overworld BlendedNoise: xzScale=0.25, xzFactor=80. these are the highest
frequency octave's first extrapolation, not the overflow of every octave.
min/max were instantiated and searched separately; results match

| Stack | N | first affected positive 4-block grid plane |
| --- | ---: | ---: |
| min limit | 53,905,378,846,979,752 | 53,905,378,846,979,752 |
| max limit | 53,905,378,846,979,752 | 53,905,378,846,979,752 |
| main selector | 4,312,430,307,758,379,737 | 4,312,430,307,758,379,740 |

## The End

seed 0 unless stated otherwise. these are the current implementation's End
paths, not vanilla's older int-square End-ring distances. the End uses island
simplex noise and BlendedNoise, not the overworld's climate shifts, cave noises,
aquifers, ore veins or noise-based surface materials. the F3 erosion slot holds
the island density; it does **not** sample the `erosion` NormalNoise above

the End's density interpolation grid is **8 blocks horizontally and 4 vertically**.
for an unshifted horizontal noise boundary N the first positive density plane is
`8 * ceil(N / 8)`, not the overworld's 4-block formula. biome selection samples
the island field at section centers (`16 * section + 8`) instead; a density-input
boundary is not an exact first visible block or biome boundary

### Base 3D noise

End settings: xzScale=0.25, yScale=0.25, xzFactor=80, yFactor=160,
smearScaleMultiplier=4. horizontal nominal scales match the earlier base-noise
table, but the End uses `LegacyRandomSource(seed)` rather than the overworld's
hashed xoroshiro terrain source. exact 32-bit octave offsets therefore differ

highest-frequency octave, independently checked X/Z and both signs, with an
inward-neighbor check. these are input-floor failures, not guaranteed terrain
transitions; selector branch selection can skip a limit stack

#### 32-bit / lossy

| Stack | +X | −X | +Z | −Z |
| --- | ---: | ---: | ---: | ---: |
| min limit | 12,550,823 | -12,550,826 | 12,550,824 | -12,550,826 |
| max limit | 12,550,824 | -12,550,825 | 12,550,824 | -12,550,826 |
| main selector | 1,004,065,851 | -1,004,065,998 | 1,004,065,821 | -1,004,066,028 |

#### 64-bit / precise

these first-extrapolation values match the overworld catalog, despite the
different seed source. all four signed directions were checked, not inferred
from the nominal scale. the grid planes below use the End's 8-block cells

| Stack | N | first affected positive 8-block grid plane |
| --- | ---: | ---: |
| min limit | 53,905,378,846,979,752 | 53,905,378,846,979,752 |
| max limit | 53,905,378,846,979,752 | 53,905,378,846,979,752 |
| main selector | 4,312,430,307,758,379,737 | 4,312,430,307,758,379,744 |

### Island simplex and lossy island sizes

let `qX = trunc(X / 8)`, `qZ = trunc(Z / 8)`. the island search tests the
25 by 25 candidate window
`u = trunc(qX / 2) + dx`, `v = trunc(qZ / 2) + dz`, with dx,dz in [-12,12].
the candidate's simplex output must be below -0.9 to create an outer island.
simplex uses no seed-dependent coordinate offset here; the permutation and
which candidates pass the threshold **are** seed-dependent

tables below are exact **sampler input** boundaries on specified transects.
`16 * N` is the corresponding candidate-center block reference, not the first
player block affected: the window reaches 12 candidate steps outward, and can
reach a failure while its center is still healthy. diagonals specify both
input coordinates, not radial distance. swapping X/Z gives the same arithmetic
threshold, but not necessarily the same noise output

#### 32-bit / lossy: simplex floor and unskew-sum overflow

`F2 = (sqrt(3) - 1) / 2`, `i = floor(u + (u+v)*F2)`,
`j = floor(v + (u+v)*F2)`. before either floor fails, the **int addition**
`i+j` can already wrap in `floorSum`. the resulting wrong unskew displaces
the corners outside their support; this is not ImprovedNoise's quintic
extrapolation. the simplex candidate can become zero and stop passing -0.9

| Failure / input transect | positive N | negative N | positive candidate-center block reference |
| --- | ---: | ---: | ---: |
| int unskew sum / (n,0) | 1,239,850,263 | -1,239,850,263 | 19,837,604,208 |
| int unskew sum / (n,n) | 619,925,132 | -619,925,132 | 9,918,802,112 |
| individual floor / (n,0) | 1,572,067,139 | -1,572,067,139 | 25,153,074,224 |
| individual floor / (n,n) | 1,239,850,263 | -1,239,850,263 | 19,837,604,208 |
| individual floor / (n,-n) | 2,147,483,648 | -2,147,483,648 | 34,359,738,368 |

the anti-diagonal has no earlier int-sum overflow. candidate-window offsets
mean the actual generator does not exclusively sample any one listed line

#### 64-bit / lossy: individual simplex floors

the unskew sum is added as doubles, so there is no int-sum stage. converting
the candidate integers to doubles still loses precision; the following are
the later floor-range failures, **not** the first loss of island detail

| Input transect | positive N | negative N |
| --- | ---: | ---: |
| (n,0) | 6,751,976,948,087,738,881 | -6,751,976,948,087,740,929 |
| (n,n) | 5,325,116,328,314,171,905 | -5,325,116,328,314,172,928 |
| (n,-n) | 9,223,372,036,854,775,296 | -9,223,372,036,854,775,296 |

negative floor failure tests strictly below -2^63; positive failure starts at
2^63. double rounding explains the asymmetric integer-input thresholds

| Lossy candidate conversion | first positive integer not retained exactly | candidate-center block reference |
| --- | ---: | ---: |
| integer to double / simplex input | 9,007,199,254,740,993 | 144,115,188,075,855,888 |
| integer to float / size input | 16,777,217 | 268,435,472 |

negative conversion losses mirror these magnitudes. simplex skew arithmetic
can lose local detail before the integer-to-double conversion itself fails;
the size calculation's earlier multiplication-rounding examples are below.
these conversion rows are not additional sharp terrain walls

#### Precise simplex

both 32-bit / precise and 64-bit / precise use `getValueAt`, not the lossy
floor path. it keeps the local skew fraction and only needs lattice indices
modulo 256 for the permutation. wrapping lattice indices is intentional here;
the `failureMagnitude` flag is not routed through `NoiseSampling.route`.
there is therefore **no corresponding 2^31 or 2^63 simplex extrapolation wall**
on this path. this does not protect the separate BlendedNoise or End-rings
arithmetic. full-width smoke checks through candidate inputs near ±2^123
retained finite simplex output within [-1,1]; this is not an exhaustive scan
of every coordinate

#### Lossy size variation

the lossy candidate-size formula is
`(abs((float)u)*3439 + abs((float)v)*147) % 13 + 9`.
float multiplication/addition can round before the remainder, **well before**
the integer-to-float conversion itself loses whole candidate coordinates.
these are the first mismatches from exact integer arithmetic on the specified
nonnegative candidate-input lines, exhaustively checked from zero

| Input transect | first candidate N | candidate-center block reference | lossy size | exact size |
| --- | ---: | ---: | ---: | ---: |
| (n,0) | 4,879 | 78,064 | 10 | 11 |
| (0,n) | 114,131 | 1,826,096 | 11 | 12 |
| (n,n) | 4,879 | 78,064 | 12 | 14 |

these are rounding differences, not overflow walls or proof that those
candidates actually create islands for seed 0. mixed-coordinate lines can
have different first mismatches. precise mode reduces the integer coordinates
modulo 13 before multiplication, avoiding this lossy size calculation

### end rings: squared-distance overflow

`EndRingsMode=true` evaluates `qX*qX + qZ*qZ` as a signed **long**, then
converts it to float for sqrt. this is independent of the selected 32/64-bit
noise lattice. the first negative wrapped sum makes sqrt and island density
NaN. `Math.max` with nearby island candidates does not recover a NaN base
height. these arithmetic edges are seed-independent; the neighboring finite
island shapes are not

| Transect | first overflowing q magnitude | first NaN block-coordinate magnitude |
| --- | ---: | ---: |
| axis, other coordinate=0 | 3,037,000,500 | 24,296,004,000 |
| diagonal, X=Z | 2,147,483,648 | 17,179,869,184 |

both signs were checked through the actual seed-0 island density in
64-bit / precise, including the inward block: finite before, NaN at the
boundary. disabling End rings retained finite values at both edges.

#### Ring widths and why they vary

let `R = qX^2 + qZ^2` in mathematical integers. the square expression's signed
result is negative when `R mod 2^64` is in `[2^63, 2^64)`.
before discrete sampling becomes important, the kth NaN annulus therefore has
q-space radii `sqrt(k*2^64 + 2^63)` through `sqrt((k+1)*2^64)`.
the radial widths shrink overall, but **a width measured along X or Z is not
the radial width**: at a fixed transverse coordinate z it follows
`sqrt(radius^2 - z^2)`, and can be much wider near a tangent. a diagonal's
coordinate width differs by a factor of sqrt(2) from its radial width.
island gaps, already-broken base noise, lossy candidate sampling, height and
interpolation further change the visible terrain bands

exact positive block intervals below are the first six negative-square bands;
negative transects mirror them. diagonal intervals are values of each axis,
not diagonal travel distances. widths include both endpoints. the next finite
width describes a **nonnegative square**, not guaranteed solid terrain

| Transect | band | first NaN block | last NaN block | width | next finite width |
| --- | ---: | ---: | ---: | ---: | ---: |
| axis | 1 | 24,296,004,000 | 34,359,738,367 | 10,063,734,368 | 7,722,174,984 |
| axis | 2 | 42,081,913,352 | 48,592,007,999 | 6,510,094,648 | 5,735,508,528 |
| axis | 3 | 54,327,516,528 | 59,512,812,591 | 5,185,296,064 | 4,768,371,848 |
| axis | 4 | 64,281,184,440 | 68,719,476,735 | 4,438,292,296 | 4,168,535,264 |
| axis | 5 | 72,888,012,000 | 76,830,710,679 | 3,942,698,680 | 3,750,018,496 |
| axis | 6 | 80,580,729,176 | 84,163,826,703 | 3,583,097,528 | 3,436,661,512 |
| X=Z | 1 | 17,179,869,184 | 24,296,003,999 | 7,116,134,816 | 5,460,402,296 |
| X=Z | 2 | 29,756,406,296 | 34,359,738,367 | 4,603,332,072 | 4,055,616,976 |
| X=Z | 3 | 38,415,355,344 | 42,081,913,351 | 3,666,558,008 | 3,371,748,072 |
| X=Z | 4 | 45,453,661,424 | 48,592,007,999 | 3,138,346,576 | 2,947,599,552 |
| X=Z | 5 | 51,539,607,552 | 54,327,516,527 | 2,787,908,976 | 2,651,663,504 |
| X=Z | 6 | 56,979,180,032 | 59,512,812,591 | 2,533,632,560 | 2,430,086,664 |

with End rings disabled, precise mode bypasses the distant central square
entirely. lossy mode uses double squares there, avoiding this particular
long-square NaN but not the lossy simplex, size or candidate-gate issues

## Shift-driven slicing and lava collapse

`64bit-stable-shifts` patches only `ShiftA` / `shift_x` and `ShiftB` / `shift_z`,
in both NormalNoise stacks. Their scaled inputs retain all 128 coordinate bits,
including world X rotated into noise Y by ShiftB. All other samplers keep the
ordinary `64bit` overflow behavior, including shifted climate noise itself;
this removes shift extrapolation, not every possible distant terrain failure.

its a **later failure than cheese/pillar overflow**. a huge cave_cheese,
pillar, or BlendedNoise value does not mean the shifts have failed.

### Cause

`ShiftA` (shift_x) samples offset noise at `(X/4, 0, Z/4)` and multiplies
the result by 4. `ShiftB` (shift_z) samples **`(Z/4, X/4, 0)`** and also
multiplies by 4. thus world X tests noise **Y** for shift_z; using only
the noise's X/Z offsets gives the wrong X boundary.

when the cell floor fails, the fractional displacement leaves [0,1).
ImprovedNoise's quintic smoothstep extrapolates and the shifts become huge.
Those shifts are then **coordinates**, not density contributions: they are
added to the inputs of continentalness, erosion, ridge, temperature and
vegetation. and after that everything falls apart.
arithmetic grows to infinity/NaN; terrain splines and finally density eventually become NaN.

the surviving finite-density regions among failed regions can look like
slices/pillars. These are not necessarily the cave noise named `pillar`.
the shift noise varies across the other horizontal coordinate, so failure
can advance unevenly across a cross-section rather than as one flat wall.
the 4-block horizontal density grid and interpolation also affect the edge;
a NaN corner can contaminate interpolated samples within its cell.

`Aquifer.computeSubstance` only keeps the solid branch when `density > 0`.
for NaN that comparison is false, so the fluid/air branch runs. The global
fluid picker supplies lava below Y=-54; removing terrain exposes that lava.
the already-overflowed aquifer_lava noise can additionally select lava in
eligible underground aquifers. **NaN does not mean lava at every height.**

### Exact boundaries and what “first slice” means

seed 0, default overworld as usual.

a visible first slice is **not** uniquely specified by an axis and seed:
it also requires the other horizontal coordinate, height, a definition of
the slice, and actual interpolated chunk/block evaluation. the tables must
not be read as exact first visible blocks. we separately measure the first
nonfinite final-density **grid sample on a defined transect**: other axis=0,
Y=64, outward 4-block planes starting one plane inward of the first shift
failure. This is an exhaustive scan, not a binary search of a potentially
nonmonotonic noise value.

#### 32-bit / lossy: shift input failure

| Stack / function | +X | −X | +Z | −Z |
| --- | ---: | ---: | ---: | ---: |
| second / shift_x | 16,873,994,542 | -16,873,997,744 | 16,873,995,662 | -16,873,996,623 |
| second / shift_z | 16,873,995,483 | -16,873,996,803 | 16,873,994,542 | -16,873,997,744 |
| first / shift_x | 17,179,868,388 | -17,179,869,981 | 17,179,867,495 | -17,179,870,874 |
| first / shift_z | 17,179,868,680 | -17,179,869,689 | 17,179,868,388 | -17,179,869,981 |

| Defined transect, second-stack stage | +X | −X | +Z | −Z |
| --- | ---: | ---: | ---: | ---: |
| first affected shift grid plane | 16,873,994,544 | -16,873,996,804 | 16,873,994,544 | -16,873,996,624 |
| first nonfinite continentalness/erosion/ridge grid sample | 16,874,366,164 | -16,873,996,804 | 16,874,285,508 | -16,873,996,624 |
| first nonfinite final-density grid sample | 16,874,602,452 | -16,873,996,804 | 16,874,464,128 | -16,873,996,624 |

the negative-side legacy floor wraps immediately to the positive maximum,
giving an enormous negative fraction. in this transect both negative axes
already have NaN density at their first affected plane. positive fractions
grow outward instead, allowing a longer transition. this doesnt imply
that every axis/seed has the same visual ordering.

#### 64-bit / precise: shift input failure

also seed 0.
both shift functions were checked independently, including ShiftB's noise-Y
axis, on all four signed world axes. the first-extrapolation boundaries match:

| Stack / both shift functions | +X | −X | +Z | −Z |
| --- | ---: | ---: | ---: | ---: |
| second | 72,473,261,583,357,418,567 | -72,473,261,583,357,418,567 | 72,473,261,583,357,418,567 | -72,473,261,583,357,418,567 |
| first | 73,786,976,294,838,222,848 | -73,786,976,294,838,222,848 | 73,786,976,294,838,222,848 | -73,786,976,294,838,222,848 |

The second stack's first affected grid plane is
**±72,473,261,583,357,418,568**; the inward plane is
±72,473,261,583,357,418,564. This is the earliest grid plane with this
specific shift-extrapolation failure, **not a measured first visible slice**.
interpolation can expose effects in the cell extending four blocks inward.

At other axis=0, Y=64:

| Axis | shift_x at first affected plane | shift_z | final density inward → affected |
| --- | ---: | ---: | --- |
| +X | 2.86603e20 | 2.62083e19 | -0.0142441 → -0.00787029 |
| −X | 1.53293e116 | 1.41207e115 | -0.0837727 → -0.0841879 |
| +Z | 5.62227e20 | 2.86603e20 | -0.120313 → 0.0540189 |
| −Z | 3.00695e116 | 1.53293e116 | -0.218418 → -0.239923 |

all four transects retained finite final density over the first **3,000,000 blocks**
outward (every 4-block plane tested in the completed extended scan).
negative-X continentalness was already NaN and negative-Z continentalness infinite at the first affected plane,
yet their final densities stayed finite in this window. positive-Z depth was
already -Infinity there, also without making final density NaN. this is why
“shift overflow instantly makes everything NaN/lava” is too strong,
especially for the precise path. these 64-bit results must not be replaced
with the 32-bit collapse distances multiplied by 2^32.

#### Sudden +X wall versus gradual slicing

the arithmetic explains a shared input edge, a positive/negative fraction
asymmetry, and seed/axis-dependent values. in the
seed-0 64-bit transect above, +X final density does not even change sign at
the boundary, while +Z does. a wall seen on +X can therefore depend on the
viewed cross-section/height, or be one of the earlier base/cheese/pillar
transitions rather than this shift stage. an exact first visible slice for
that observation remains unmeasured: it requires its coordinates and
generated-chunk/interpolated-block verification.

### Seed dependence 

- the nominal scale (32-bit: about 16,873,996,143 blocks): seed-independent.
- the exact 32-bit input boundaries **are seed-dependent**, because octave axis
  offsets are seed-dependent. ShiftA/ShiftB's axis rotation matters too.
- shift signs, transverse patterns, terrain branches, and visible slice/NaN
  onsets depend on the seed and cross-section, even when an arithmetic
  boundary happens to match between directions.
- precise 64-bit first-extrapolation boundaries can lose the seed offsets in
  the fallback's double rounding, unlike earlier fallback-entry boundaries.
  Matching arithmetic edges do not imply seed-independent visible terrain.

## Far Lands degradation stages

### 32-bit
seed 0, default overworld noise settings, 32-bit generator, lossy precision.

| Stage | Name | Cause |
| --- | --- | --- |
| before cheese overflow | modern / cave-cut Far Lands | broken base 3D noise, modern caves still operating |
| cheese overflow | cheese-collapse Far Lands | cheese noise escapes its normal range; cave contribution saturates |
| pillar overflow | pillar-dominated Far Lands | pillar noise can overwhelm the underground density branch |

modern cave, aquifer, biome and surface code still runs.
especially, overflowing pillars do not become positive everywhere

#### Exact boundaries

each entry is the first integer block coordinate outward from zero where the
highest active octave's cell floor is wrong. the adjacent block toward zero
still has the correct floor. X and Z are tested independently; these inputs
are unshifted, so their arithmetic boundary does not depend on the other axis

these are exact input-failure boundaries, **not** a claim that the first visible
changed block is there. density is interpolated on a 4-block horizontal grid;
branch selection, Y, the other axis and density sign determine the visible edge.
there is no single exact surface-transition coordinate covering every Y/Z or Y/X

| Input / stack | +X | −X | +Z | −Z |
| --- | ---: | ---: | ---: | ---: |
| cave_cheese / second | 4,218,498,805 | -4,218,499,267 | 4,218,498,760 | -4,218,499,312 |
| cave_cheese / first | 4,294,967,173 | -4,294,967,420 | 4,294,966,982 | -4,294,967,611 |
| pillar / second | 5,399,678,716 | -5,399,678,815 | 5,399,678,204 | -5,399,679,328 |
| pillar / first | 5,497,557,855 | -5,497,558,423 | 5,497,557,533 | -5,497,558,746 |

the map's two main bands are the **second** stack boundaries. 
the first stack fails later: each band has a second arithmetic transition, not one shared edge

| Related input / stack | +X | −X | +Z | −Z |
| --- | ---: | ---: | ---: | ---: |
| aquifer_lava / second | 4,218,498,832 | -4,218,499,240 | 4,218,498,945 | -4,218,499,127 |
| aquifer_lava / first | 4,294,967,226 | -4,294,967,367 | 4,294,967,083 | -4,294,967,510 |
| ice / second | 4,218,498,915 | -4,218,499,158 | 4,218,498,652 | -4,218,499,420 |
| ice / first | 4,294,967,266 | -4,294,967,327 | 4,294,967,294 | -4,294,967,299 |

aquifer_lava changes fluid selection, not the main terrain density; ice is a
surface-rule input. neither should be mistaken for the cheese-collapse edge

#### why it happens

NormalNoise adds two Perlin stacks. the second multiplies its input by
`1.0181268882175227`; ImprovedNoise then adds a seed-specific X/Y/Z offset.
cheese's highest active octave is -1, giving horizontal scale 1/2.
pillar's highest active octave is -6, with an additional horizontal scale of 25

important: `noise(cave_cheese, 0.6666666666666666)` scales **Y**, not X/Z

for the lossy path the actual operation order is:

`point = (((double) block * xzScale) * stackFactor) * octaveFactor + axisOffset`

at positive `point >= 2,147,483,648`, Java's int cast saturates and the floor
sticks at 2,147,483,647. below negative -2,147,483,648, Mth.floor's subtraction
wraps the saturated int to **positive** 2,147,483,647. negative-side failure is
therefore immediately much larger, not a mirrored positive-side transition

the fractional offset is no longer in [0, 1). smoothstep `t*t*t*(t*(t*6-15)+10)`
extrapolates instead of interpolating, making noise explode. 
cheese is clamped inside the underground calculation; pillars are
combined with it using max. huge positive pillars can replace the cave-cut
density, producing the more continuous, Beta-like walls; huge negative pillars
are rejected by the pillar range gate instead. this is why X and Z can look
completely different even when their overflow scales are the same

seed-0 check, other horizontal axis = 0, Y = 64, 256 blocks outward from each
second-stack boundary; raw NormalNoise signs, before router clamps/gates:

| Input | +X | −X | +Z | −Z |
| --- | --- | --- | --- | --- |
| cave_cheese | negative | negative | positive | positive |
| pillar | negative | negative | positive | positive |

### 64-bit

seed 0, same overworld settings and stage names, **64-bit / precise**.
signed block coordinates, not magnitudes; X and Z searched independently

#### Exact boundaries

the sharp degradation is the **first extrapolation**, not the earlier entry
into the overflow fallback. the original table measured the latter and was
too early for the wall. at each boundary below the highest active octave's
routed fraction first leaves [0, 1); the inward neighbor is still zero

| Input / stack | +X | −X | +Z | −Z |
| --- | ---: | ---: | ---: | ---: |
| cave_cheese / second | 18,118,315,395,839,352,631 | -18,118,315,395,839,352,631 | 18,118,315,395,839,352,631 | -18,118,315,395,839,352,631 |
| cave_cheese / first | 18,446,744,073,709,553,665 | -18,446,744,073,709,553,665 | 18,446,744,073,709,553,665 | -18,446,744,073,709,553,665 |
| pillar / second | 23,191,443,706,674,373,537 | -23,191,443,706,674,373,537 | 23,191,443,706,674,373,537 | -23,191,443,706,674,373,537 |
| pillar / first | 23,611,832,414,348,231,312 | -23,611,832,414,348,231,312 | 23,611,832,414,348,231,312 | -23,611,832,414,348,231,312 |

aquifer_lava and ice share the cheese first-extrapolation boundaries for each
stack. all four signed directions were tested, not inferred by symmetry.
matching onsets do not imply matching noise signs or terrain shapes

the first broken integer input is ...352,631; the first affected density-grid
plane is ...352,632. interpolation can therefore expose a wall before the
first broken integer input. this is a discrete arithmetic jump, not gradual
growth over thousands of blocks as previously suggested

| Input / stack | first affected +X or +Z grid plane | inward plane |
| --- | ---: | ---: |
| cave_cheese / second | 18,118,315,395,839,352,632 | 18,118,315,395,839,352,628 |
| cave_cheese / first | 18,446,744,073,709,553,668 | 18,446,744,073,709,553,664 |
| pillar / second | 23,191,443,706,674,373,540 | 23,191,443,706,674,373,536 |
| pillar / first | 23,611,832,414,348,231,312 | 23,611,832,414,348,231,308 |

negative planes are the negatives of these, with the inward plane toward zero.
the solid/air crossing within a cell still depends on the sampled densities,
but the plane where extrapolation starts does not depend on Y/Z

direct seed-0 router checks across X=...352,630 → ...352,631 reproduced:

| Y / Z | raw cheese before | raw cheese after | final density before | final density after |
| --- | ---: | ---: | ---: | ---: |
| 232 / 218 | -0.143528778 | -1.26738815e18 | 0.0444683061 | -0.305446355 |
| 248 / 4,820,284 | -0.180306477 | -2.02815896e18 | 0.00263553897 | -0.170047594 |

these are direct router evaluations, not a complete generated-chunk test;
NoiseChunk performs the grid interpolation. other sampled heights retain
similar final densities despite the raw jump because the router clamps or
selects another branch. that does not move the shared arithmetic edge

#### Earlier fallback entry (not the sharp wall)

each entry below is the first block where the highest active octave enters
NoiseSampling's fallback. the inward neighbor still uses the healthy split
route. retain these values only to distinguish the two events

| Input / stack | +X | −X | +Z | −Z |
| --- | ---: | ---: | ---: | ---: |
| cave_cheese / second | 18,118,315,395,839,350,388 | -18,118,315,395,839,350,619 | 18,118,315,395,839,350,343 | -18,118,315,395,839,350,619 |
| cave_cheese / first | 18,446,744,073,709,551,493 | -18,446,744,073,709,551,616 | 18,446,744,073,709,551,302 | -18,446,744,073,709,551,616 |
| pillar / second | 23,191,443,706,674,368,338 | -23,191,443,706,674,368,388 | 23,191,443,706,674,367,825 | -23,191,443,706,674,368,388 |
| pillar / first | 23,611,832,414,348,225,785 | -23,611,832,414,348,226,069 | 23,611,832,414,348,225,463 | -23,611,832,414,348,226,069 |

| Related input / stack | +X | −X | +Z | −Z |
| --- | ---: | ---: | ---: | ---: |
| aquifer_lava / second | 18,118,315,395,839,350,415 | -18,118,315,395,839,350,619 | 18,118,315,395,839,350,528 | -18,118,315,395,839,350,619 |
| aquifer_lava / first | 18,446,744,073,709,551,546 | -18,446,744,073,709,551,616 | 18,446,744,073,709,551,403 | -18,446,744,073,709,551,616 |
| ice / second | 18,118,315,395,839,350,498 | -18,118,315,395,839,350,619 | 18,118,315,395,839,350,235 | -18,118,315,395,839,350,619 |
| ice / first | 18,446,744,073,709,551,586 | -18,446,744,073,709,551,616 | 18,446,744,073,709,551,614 | -18,446,744,073,709,551,616 |

use the **first-extrapolation** second-stack rows for the two sharp degradation
bands, not these fallback-entry rows. first-stack extrapolation happens later

#### Why precise matters

the **nominal scale** multiplies by 4,294,967,296, because the lattice limit
changes from 2^31 to 2^63. the actual block boundary does not simply multiply:

- seed offsets are added in noise space; they are not multiplied by 2^32
- precise keeps the full-width block coordinate instead of converting it to double
- each stack uses `xzScale * stackFactor` as a double coefficient, then applies
  that coefficient and the octave power to the exact integer coordinate
- the split fraction receives the seed offset before the cell carry is checked

positive fallback entries retain X/Z differences: the second-stack pillar
fallback starts on +Z **513 blocks** nearer zero than on +X. the later sharp
extrapolation onsets coincide because the fallback rounds away those offsets

there is an important implementation asymmetry. WorldgenCoordinate.scale marks
an unshifted scaled magnitude of at least 2^63 as failed **before** adding the
seed offset. on the negative side this can select the fallback even though
-2^63 itself, or the offset-adjusted cell, would still fit a signed long.
the negative X/Z entries coincide because this earlier magnitude check wins;
they are **route boundaries**, not first mathematically unrepresentable cells

once the fallback is selected it reconstructs a double failure magnitude and
uses Mth.lfloor. at the boundary this can round to ±2^63; seed offsets below
256 disappear in that reconstruction. the apparent fraction can initially be
zero, so first fallback and first explosive extrapolation are different events.
farther outward, positive fractions grow outside [0, 1); negative inputs below
-2^63 make Mth.lfloor wrap to Long.MAX_VALUE and immediately produce huge
negative fractions. precise preserves healthy coordinates, but intentionally
does not remove the legacy failure after routing

the exact jump was checked by invoking the actual NoiseSampling.route, not
estimating 2^63 / scale. for cheese's second stack, at ...352,630 the positive
failure magnitude rounds to 2^63 and the fraction is 0. at ...352,631 it rounds
to **2^63 + 2,048**, and the fraction becomes **2,048**. on the negative side
the corresponding jump changes the fraction from 0 to about -2^64.
smoothstep is now extrapolating at a huge argument in a single block

pillar's second stack reaches that first double jump at an unrounded scaled
excess of 2,048 rather than cheese's 1,024. WorldgenCoordinate.scale reconstructs
the failure double by adding scaled product limbs; this intermediate rounding
is why simply applying a universal half-ULP formula would also give wrong values

seed-0 check, other horizontal axis = 0, Y = 64, **16,384 blocks** outward from
each second-stack boundary, before router clamps/gates:

| Input | +X | −X | +Z | −Z |
| --- | --- | --- | --- | --- |
| cave_cheese | negative | negative | positive | positive |
| pillar | negative | negative | positive | positive |

again, this is one checked cross-section, not a sign guarantee at every Y.
positive Z pillars can suppress modern cave cuts while negative X pillars are
rejected by the range gate

lossy has different exact boundaries: it rounds absolute coordinates first,
erases the seed offsets at these magnitudes and makes X/Z onsets coincide.
the old lossy table is not interchangeable with this precise table. patched
mode does not intentionally take these overflow routes
