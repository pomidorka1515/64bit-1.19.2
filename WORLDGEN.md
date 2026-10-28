# worldgen overflow values

seed 0. Noise inputs and SurfaceSystem / SurfaceRules tables below contain
separate measured **32-bit / lossy** and **64-bit / precise** signed
block-coordinate boundary tables, not nominal
scales. each highest active octave and stack was searched independently in
**+X, +Z, −Z, −X** order, checking that the adjacent integer toward zero is
healthy. inputs are grouped only when scale, stack and all four values match.
`= <column>` means exactly the same signed value (not an estimate)

## Noise inputs

climate sampler rows use **zero added shift**; actual generation adds the
position-dependent shifts. shift_x and shift_z themselves use actual offset
noise, including ShiftB's rotation of world X into noise Y. aquifer_barrier,
aquifer_fluid_level_floodedness and aquifer_fluid_level_spread have X/Z scale 1:
their single scale argument changes Y only. the separate 64-bit counterpart
below uses the same zero-shift conditions

### 32-bit / lossy

| Input / stack | X/Z scale | +X | +Z | −Z | −X |
| --- | ---: | ---: | ---: | ---: | ---: |
| temperature / second | 0.25 | 2,159,871,461,051 | 2,159,871,261,976 | -2,159,871,750,415 | -2,159,871,551,340 |
| temperature / first | 0.25 | 2,199,023,051,862 | 2,199,023,064,316 | -2,199,023,446,789 | -2,199,023,459,243 |
| vegetation / second | 0.25 | 1,079,935,627,531 | 1,079,935,630,357 | -1,079,935,875,839 | -1,079,935,878,664 |
| vegetation / first | 0.25 | 1,099,511,574,757 | 1,099,511,604,763 | -1,099,511,650,790 | -1,099,511,680,796 |
| continentalness / second | 0.25 | 16,873,994,513 | 16,873,995,059 | -16,873,997,227 | -16,873,997,772 |
| continentalness / first | 0.25 | 17,179,868,295 | 17,179,869,005 | -17,179,869,364 | -17,179,870,074 |
| erosion / second | 0.25 | 269,983,926,302 | 269,983,913,475 | -269,983,963,075 | -269,983,950,248 |
| erosion / first | 0.25 | 274,877,895,003 | 274,877,902,109 | -274,877,911,780 | -274,877,918,886 |
| ridge / second | 0.25 | 269,983,936,574 | 269,983,909,921 | -269,983,966,629 | -269,983,939,976 |
| ridge / first | 0.25 | 274,877,893,570 | 274,877,876,286 | -274,877,937,603 | -274,877,920,319 |
| temperature_large / second | 0.25 | 8,639,485,574,370 | 8,639,485,589,377 | -8,639,486,460,184 | -8,639,486,475,191 |
| temperature_large / first | 0.25 | 8,796,092,848,386 | 8,796,092,100,111 | -8,796,093,944,306 | -8,796,093,196,031 |
| vegetation_large / second | 0.25 | 4,319,742,507,050 | 4,319,742,822,341 | -4,319,743,202,440 | -4,319,743,517,731 |
| vegetation_large / first | 0.25 | 4,398,046,073,757 | 4,398,046,463,569 | -4,398,046,558,640 | -4,398,046,948,452 |
| continentalness_large / second | 0.25 | 67,495,982,115 | 67,495,978,973 | -67,495,990,165 | -67,495,987,024 |
| continentalness_large / first | 0.25 | 68,719,473,482 | 68,719,473,280 | -68,719,480,193 | -68,719,479,991 |
| erosion_large / second | 0.25 | 1,079,935,634,243 | 1,079,935,695,464 | -1,079,935,810,732 | -1,079,935,871,953 |
| erosion_large / first | 0.25 | 1,099,511,529,390 | 1,099,511,596,383 | -1,099,511,659,170 | -1,099,511,726,163 |
| offset / shift_x / second | 0.25 | 16,873,994,542 | 16,873,995,662 | -16,873,996,623 | -16,873,997,744 |
| offset / shift_x / first | 0.25 | 17,179,868,388 | 17,179,867,495 | -17,179,870,874 | -17,179,869,981 |
| shift_z / second | 0.25 | 16,873,995,483 | 16,873,994,542 | -16,873,997,744 | -16,873,996,803 |
| shift_z / first | 0.25 | 17,179,868,680 | 17,179,868,388 | -17,179,869,981 | -17,179,869,689 |
| aquifer_barrier / second | 1.0 | 16,873,996,003 | 16,873,994,840 | -16,873,997,445 | -16,873,996,282 |
| aquifer_barrier / first | 1.0 | 17,179,869,060 | 17,179,867,258 | -17,179,871,111 | -17,179,869,309 |
| aquifer_fluid_level_floodedness / second | 1.0 | 269,983,910,877 | 269,983,907,694 | -269,983,968,856 | -269,983,965,673 |
| aquifer_fluid_level_floodedness / first | 1.0 | 274,877,890,777 | 274,877,875,622 | -274,877,938,267 | -274,877,923,112 |
| aquifer_fluid_level_spread / second | 1.0 | 67,495,982,650 | 67,495,983,344 | -67,495,985,794 | -67,495,986,488 |
| aquifer_fluid_level_spread / first | 1.0 | 68,719,473,563 | 68,719,474,643 | -68,719,478,830 | -68,719,479,910 |
| aquifer_lava / second | 1.0 | 4,218,498,832 | 4,218,498,945 | -4,218,499,127 | -4,218,499,240 |
| aquifer_lava / first | 1.0 | 4,294,967,226 | 4,294,967,083 | -4,294,967,510 | -4,294,967,367 |
| cave_cheese / second | 1.0 | 4,218,498,805 | 4,218,498,760 | -4,218,499,312 | -4,218,499,267 |
| cave_cheese / first | 1.0 | 4,294,967,173 | 4,294,966,982 | -4,294,967,611 | -4,294,967,420 |
| pillar / second | 25.0 | 5,399,678,716 | 5,399,678,204 | -5,399,679,328 | -5,399,678,815 |
| pillar / first | 25.0 | 5,497,557,855 | 5,497,557,533 | -5,497,558,746 | -5,497,558,423 |
| pillar_rareness / second | 1.0 | 539,967,863,510 | 539,967,834,176 | -539,967,918,923 | -539,967,889,589 |
| pillar_rareness / first | 1.0 | 549,755,785,233 | 549,755,810,812 | -549,755,816,965 | -549,755,842,544 |
| pillar_thickness / second | 1.0 | 539,967,871,021 | 539,967,832,465 | -539,967,920,634 | -539,967,882,078 |
| pillar_thickness / first | 1.0 | 549,755,756,396 | 549,755,788,952 | -549,755,838,825 | -549,755,871,381 |
| spaghetti_2d_elevation / second | 1.0 | 539,967,857,991 | 539,967,838,185 | -539,967,914,913 | -539,967,895,108 |
| spaghetti_2d_elevation / first | 1.0 | 549,755,753,528 | 549,755,811,168 | -549,755,816,609 | -549,755,874,249 |
| spaghetti_3d_thickness / second | 1.0 | 539,967,823,017 | 539,967,863,097 | -539,967,890,001 | -539,967,930,081 |
| spaghetti_3d_thickness / first | 1.0 | 549,755,786,349 | 549,755,774,423 | -549,755,853,354 | -549,755,841,428 |
| spaghetti_roughness / second | 1.0 | 67,495,977,355 | 67,495,977,972 | -67,495,991,166 | -67,495,991,783 |
| spaghetti_roughness / first | 1.0 | 68,719,470,813 | 68,719,474,462 | -68,719,479,011 | -68,719,482,660 |
| spaghetti_roughness_modulator / second | 1.0 | 539,967,838,082 | 539,967,820,689 | -539,967,932,410 | -539,967,915,017 |
| spaghetti_roughness_modulator / first | 1.0 | 549,755,772,804 | 549,755,805,032 | -549,755,822,745 | -549,755,854,973 |
| cave_layer / second | 1.0 | 539,967,855,676 | 539,967,860,124 | -539,967,892,974 | -539,967,897,423 |
| cave_layer / first | 1.0 | 549,755,812,118 | 549,755,791,159 | -549,755,836,618 | -549,755,815,659 |
| noodle / second | 1.0 | 539,967,827,965 | 539,967,821,257 | -539,967,931,841 | -539,967,925,133 |
| noodle / first | 1.0 | 549,755,800,008 | 549,755,754,333 | -549,755,873,444 | -549,755,827,769 |
| noodle_thickness / second | 1.0 | 539,967,848,775 | 539,967,873,822 | -539,967,879,277 | -539,967,904,323 |
| noodle_thickness / first | 1.0 | 549,755,785,091 | 549,755,807,682 | -549,755,820,095 | -549,755,842,686 |
| spaghetti_2d_modulator / second | 2.0 | 2,159,871,296,996 | 2,159,871,276,961 | -2,159,871,735,430 | -2,159,871,715,395 |
| spaghetti_2d_modulator / first | 2.0 | 2,199,023,070,622 | 2,199,023,015,428 | -2,199,023,495,677 | -2,199,023,440,483 |
| spaghetti_2d_thickness / second | 2.0 | 2,159,871,287,460 | 2,159,871,416,944 | -2,159,871,595,447 | -2,159,871,724,931 |
| spaghetti_2d_thickness / first | 2.0 | 2,199,023,121,894 | 2,199,023,180,385 | -2,199,023,330,720 | -2,199,023,389,211 |
| spaghetti_3d_rarity / second | 2.0 | 2,159,871,346,163 | 2,159,871,451,089 | -2,159,871,561,302 | -2,159,871,666,228 |
| spaghetti_3d_rarity / first | 2.0 | 2,199,023,031,743 | 2,199,023,127,637 | -2,199,023,383,468 | -2,199,023,479,362 |
| cave_entrance / second | 0.75 | 89,994,638,053 | 89,994,643,692 | -89,994,648,492 | -89,994,654,131 |
| cave_entrance / first | 0.75 | 91,625,967,072 | 91,625,958,654 | -91,625,979,310 | -91,625,970,892 |
| ore_veininess / second | 1.5 | 359,978,573,239 | 359,978,570,348 | -359,978,598,384 | -359,978,595,494 |
| ore_veininess / first | 1.5 | 366,503,867,602 | 366,503,874,146 | -366,503,877,706 | -366,503,884,250 |
| ore_vein_a / second | 4.0 | 67,495,982,715 | 67,495,984,409 | -67,495,984,729 | -67,495,986,424 |
| ore_vein_a / first | 4.0 | 68,719,473,846 | 68,719,475,046 | -68,719,478,427 | -68,719,479,627 |
| ore_vein_b / second | 4.0 | 67,495,982,027 | 67,495,978,327 | -67,495,990,811 | -67,495,987,111 |
| ore_vein_b / first | 4.0 | 68,719,475,035 | 68,719,475,397 | -68,719,478,076 | -68,719,478,438 |
| ore_gap / second | 1.0 | 67,495,978,864 | 67,495,978,820 | -67,495,990,318 | -67,495,990,274 |
| ore_gap / first | 1.0 | 68,719,476,549 | 68,719,472,757 | -68,719,480,716 | -68,719,476,924 |
| noodle_ridge_a / second | 2.6666666666666665 | 101,243,965,109 | 101,243,965,490 | -101,243,988,216 | -101,243,988,598 |
| noodle_ridge_a / first | 2.6666666666666665 | 103,079,209,368 | 103,079,214,120 | -103,079,216,089 | -103,079,220,841 |
| noodle_ridge_b / second | 2.6666666666666665 | 101,243,967,202 | 101,243,971,223 | -101,243,982,484 | -101,243,986,505 |
| noodle_ridge_b / first | 2.6666666666666665 | 103,079,211,933 | 103,079,207,528 | -103,079,222,681 | -103,079,218,276 |
| jagged / second | 1500.0 | 2,812,333 | = +X | -2,812,333 | = −Z |
| jagged / first | 1500.0 | 2,863,312 | = +X | -2,863,312 | = −Z |

### 64-bit / precise

| Input / stack | X/Z scale | +X | +Z | −Z | −X |
| --- | ---: | ---: | ---: | ---: | ---: |
| temperature / second | 0.25 | 9,276,577,482,669,748,554,649 | = +X | -9,276,577,482,669,748,554,649 | = −Z |
| temperature / first | 0.25 | 9,444,732,965,739,291,484,160 | = +X | -9,444,732,965,739,291,484,160 | = −Z |
| vegetation / erosion_large / second | 0.25 | 4,638,288,741,334,874,281,348 | = +X | -4,638,288,741,334,874,281,348 | = −Z |
| vegetation / erosion_large / first | 0.25 | 4,722,366,482,869,645,746,176 | = +X | -4,722,366,482,869,645,746,176 | = −Z |
| continentalness / offset / shift_x / shift_z / second | 0.25 | 72,473,261,583,357,418,567 | = +X | -72,473,261,583,357,418,567 | = −Z |
| continentalness / offset / shift_x / shift_z / first | 0.25 | 73,786,976,294,838,222,848 | = +X | -73,786,976,294,838,222,848 | = −Z |
| erosion / ridge / second | 0.25 | 1,159,572,185,333,718,576,372 | = +X | -1,159,572,185,333,718,576,372 | = −Z |
| erosion / ridge / first | 0.25 | 1,180,591,620,717,411,442,688 | = +X | -1,180,591,620,717,411,442,688 | = −Z |
| temperature_large / second | 0.25 | 37,106,309,930,678,994,194,458 | = +X | -37,106,309,930,678,994,194,458 | = −Z |
| temperature_large / first | 0.25 | 37,778,931,862,957,165,912,064 | = +X | -37,778,931,862,957,165,912,064 | = −Z |
| vegetation_large / second | 0.25 | 18,553,154,965,339,497,101,252 | = +X | -18,553,154,965,339,497,101,252 | = −Z |
| vegetation_large / first | 0.25 | 18,889,465,931,478,582,960,128 | = +X | -18,889,465,931,478,582,960,128 | = −Z |
| continentalness_large / second | 0.25 | 289,893,046,333,429,650,128 | = +X | -289,893,046,333,429,650,128 | = −Z |
| continentalness_large / first | 0.25 | 295,147,905,179,352,866,816 | = +X | -295,147,905,179,352,866,816 | = −Z |
| aquifer_barrier / second | 1.0 | 72,473,261,583,357,418,567 | = +X | -72,473,261,583,357,418,567 | = −Z |
| aquifer_barrier / first | 1.0 | 73,786,976,294,838,222,848 | = +X | -73,786,976,294,838,222,848 | = −Z |
| aquifer_fluid_level_floodedness / second | 1.0 | 1,159,572,185,333,718,576,372 | = +X | -1,159,572,185,333,718,576,372 | = −Z |
| aquifer_fluid_level_floodedness / first | 1.0 | 1,180,591,620,717,411,442,688 | = +X | -1,180,591,620,717,411,442,688 | = −Z |
| aquifer_fluid_level_spread / spaghetti_roughness / ore_gap / second | 1.0 | 289,893,046,333,429,650,128 | = +X | -289,893,046,333,429,650,128 | = −Z |
| aquifer_fluid_level_spread / spaghetti_roughness / ore_gap / first | 1.0 | 295,147,905,179,352,866,816 | = +X | -295,147,905,179,352,866,816 | = −Z |
| aquifer_lava / cave_cheese / second | 1.0 | 18,118,315,395,839,352,631 | = +X | -18,118,315,395,839,352,631 | = −Z |
| aquifer_lava / cave_cheese / first | 1.0 | 18,446,744,073,709,553,665 | = +X | -18,446,744,073,709,553,665 | = −Z |
| pillar / second | 25.0 | 23,191,443,706,674,373,537 | = +X | -23,191,443,706,674,373,537 | = −Z |
| pillar / first | 25.0 | 23,611,832,414,348,231,312 | = +X | -23,611,832,414,348,231,312 | = −Z |
| pillar_rareness / pillar_thickness / spaghetti_2d_elevation / spaghetti_3d_thickness / spaghetti_roughness_modulator / cave_layer / noodle / noodle_thickness / second | 1.0 | 2,319,144,370,667,437,144,697 | = +X | -2,319,144,370,667,437,144,697 | = −Z |
| pillar_rareness / pillar_thickness / spaghetti_2d_elevation / spaghetti_3d_thickness / spaghetti_roughness_modulator / cave_layer / noodle / noodle_thickness / first | 1.0 | 2,361,183,241,434,822,877,184 | = +X | -2,361,183,241,434,822,877,184 | = −Z |
| spaghetti_2d_modulator / spaghetti_2d_thickness / spaghetti_3d_rarity / second | 2.0 | 9,276,577,482,669,748,554,649 | = +X | -9,276,577,482,669,748,554,649 | = −Z |
| spaghetti_2d_modulator / spaghetti_2d_thickness / spaghetti_3d_rarity / first | 2.0 | 9,444,732,965,739,291,484,160 | = +X | -9,444,732,965,739,291,484,160 | = −Z |
| cave_entrance / second | 0.75 | 386,524,061,777,906,194,806 | = +X | -386,524,061,777,906,194,806 | = −Z |
| cave_entrance / first | 0.75 | 393,530,540,239,137,150,294 | = +X | -393,530,540,239,137,150,294 | = −Z |
| ore_veininess / second | 1.5 | 1,546,096,247,111,624,763,132 | = +X | -1,546,096,247,111,624,763,132 | = −Z |
| ore_veininess / first | 1.5 | 1,574,122,160,956,548,584,790 | = +X | -1,574,122,160,956,548,584,790 | = −Z |
| ore_vein_a / ore_vein_b / second | 4.0 | 289,893,046,333,429,650,128 | = +X | -289,893,046,333,429,650,128 | = −Z |
| ore_vein_a / ore_vein_b / first | 4.0 | 295,147,905,179,352,866,816 | = +X | -295,147,905,179,352,866,816 | = −Z |
| noodle_ridge_a / noodle_ridge_b / second | 2.6666666666666665 | 434,839,569,500,144,516,574 | = +X | -434,839,569,500,144,516,574 | = −Z |
| noodle_ridge_a / noodle_ridge_b / first | 2.6666666666666665 | 442,721,857,769,029,318,657 | = +X | -442,721,857,769,029,318,657 | = −Z |
| jagged / second | 1500.0 | 12,078,876,930,559,569 | = +X | -12,078,876,930,559,569 | = −Z |
| jagged / first | 1500.0 | 12,297,829,382,473,036 | = +X | -12,297,829,382,473,036 | = −Z |

## Climate overflow: behavioral pipeline

the boundary tables describe sampler arithmetic. the downstream pipeline is:

`world position → shifts → shifted climate noises → terrain splines / biome search`

terrain then follows `offset → depth → sloped_cheese → surface/underground
selection → final density → interpolation → aquifer solid/fluid selection`.
biome search is a separate consumer of the climate values, not the cause of
the density branch switch. the source details below matter: neither every
overflow nor every nonfinite climate value has the same visible outcome.

### 1. Shift eclipse: ordinary versus stable shifts

ordinary `64bit` shifts first extrapolate at the nominal magnitude
**72,473,261,583,357,402,475**. the seed-0 precise first-extrapolation boundary
is **72,473,261,583,357,418,567**; see the signed shift tables below.
this coincides with the continentalness reference scale and precedes the
erosion, ridge, temperature and vegetation reference scales.

`DensityFunctions.ShiftedNoise` uses **`world_coord * scale + shift`**, not
`(world_coord + shift) * scale`. the horizontal climate scale is 0.25.
`ShiftA` and `ShiftB` already multiply their offset-noise outputs by 4.
those results are added as sampling coordinates, not as small density terms.
once a shift extrapolates, its enormous output can drive the receiving noise
far beyond its own lattice range even while the world position is nowhere
near that channel's zero-shift boundary. signs and transverse variation then
scramble the resulting climate field.

this is the **shift eclipse**: ordinary generation does not provide clean,
independent climate transitions at each later table entry. continentalness
fails alongside the shifts; it is not strictly a later event. the table's
zero-shift references remain useful arithmetic references, but are not a
sequence of observable ordinary-mode biome walls.

to isolate a climate sampler's own overflow during generation, use
**`64bit-stable-shifts`** (or explicitly disable shifts in a diagnostic).
stable shifts retain bounded shift sampling while leaving the receiving
climate samplers' ordinary overflow behavior intact. their still-nonzero
shifts can perturb the reference boundaries. this isolates the cause; it
does not guarantee intact terrain further out,
since base noise and cave noises fail too.

### 2. Spline extrapolation, negative infinite offset, and the cave fallback

continentalness, erosion and ridge-derived peaks/valleys feed nested
`CubicSpline` curves for terrain offset, factor and jaggedness. they are not
simply added to density as raw climate values. `TerrainProvider.overworldOffset`
places its outer continentalness knots between **-1.1 and 1.0**. outside a
spline's knot range, `CubicSpline.Multipoint.linearExtend` evaluates:

`value = endpoint_value + endpoint_derivative * (input - endpoint_location)`

**a zero derivative returns the endpoint value directly**, without multiplying
zero by an infinite input. importantly, both outer continentalness endpoint
derivatives are zero in this source. the high continental edge does **not**
have a negative slope: a huge positive C selects the nested inland spline,
whose erosion and ridge inputs still need evaluation.

splines use **float**, even though raw density functions return double.
finite double C and E values can easily be far beyond the float range,
so both become positive infinity when supplied to spline coordinates. this
can select endpoint sub-splines; nonzero derivatives in the nested ridge
splines can extrapolate to signed infinity, and invalid combinations can
produce NaN. thus the reported `offset: -Infinity`, `factor: 4.69`,
`sloped_cheese: -Infinity` is a downstream spline/density failure, **not proof
that C alone multiplied a negative continental endpoint slope**.

once offset is negative infinite, adding the finite height gradient gives
negative infinite depth. with a finite positive factor such as 4.69 and no
competing invalid/infinite terms, `noiseGradientDensity` and `sloped_cheese`
remain negative infinite. the decisive branch in `NoiseRouterData.selectCaves`
is:

```text
rangeChoice(sloped_cheese, -1,000,000.0, 1.5625,
            min(sloped_cheese, 5 * entrances), underground)
```

the interval includes its lower bound and excludes its upper bound.
**negative infinity is below -1,000,000.0, so it selects `underground`, not
the normal surface/entrance branch.** NaN also fails the interval test.
this connects broken climate/splines to the cave/pillar-dominated terrain
associated with the MC-311599-style fallback. the underground branch
still consumes sloped cheese and other cave fields: it is not a clean reset
to healthy caves. its clamps, max operations and pillar gate can retain finite
density despite a nonfinite surface input, as the measured transects below
demonstrate. later interpolation can introduce additional NaN failures.

### 3. Six-dimensional biome distance collapse: quantization and integer wrap

`MultiNoiseBiomeSource` searches climate targets in temperature, vegetation
(humidity), continentalness, erosion, depth and weirdness: `(T,V,C,E,D,W)`.
conceptually it minimizes a squared distance, but targets are **intervals**,
not necessarily individual points:

`fitness = d(T,I_T)^2 + d(V,I_V)^2 + d(C,I_C)^2 + d(E,I_E)^2 + d(D,I_D)^2 + d(W,I_W)^2 + offset^2`

here `d` is zero inside a target interval and otherwise the distance to its
nearest edge. `offset` is an additional target penalty. in this implementation
the actual arithmetic is **not a double Euclidean-distance search**:

1. `Climate.Sampler` casts each density result to float.
2. `Climate.quantizeCoord` computes `(long)(value * 10000.0F)`.
3. interval distances, squares and their sum use signed **long** arithmetic.
4. `Climate.RTree` searches those distances, retaining a thread-local last leaf
   as an initial candidate and updating only for a strictly smaller distance.

there are two useful failure regimes, but neither is floating-point distance
mantissa drowning followed by an Infinity comparison:

- **finite out-of-range climate:** a growing dimension can dominate the intended
  metric and suppress normal biome variation. however, long squares/sums can
  wrap long before float or double infinity. wrapped distances can become
  negative, invalidate nearest-neighbor pruning, and give pathological bands
  or biome choices rather than a reliable one-dimensional closest-C search.
- **saturated/nonfinite climate:** Java casts sufficiently large positive
  values and positive infinity to **9,223,372,036,854,775,807**, negative ones
  to **-9,223,372,036,854,775,808**, and NaN to **0**. once multiple positions
  quantize to the same climate vector, they lose those distinctions entirely.
  distance subtraction, squaring and summation still wrap; the search never
  compares floating-point infinite or NaN distances.

`+Infinity < +Infinity` is false, but it does **not** explain this
biome search. equal integer distances can retain an existing candidate; tree
ordering and the cached last result matter. there is no general rule that
overflow permanently chooses the first biome in the registry.

### 4. Why regular and `_large` reference scales differ by four

`NoiseData.registerBiomeNoises` preserves each channel's amplitude list and
lowers its first octave by **2** for `_large`. it does not remove two octaves:
it moves the same spectrum down two octave levels. every corresponding
frequency is multiplied by **0.25**, so the nominal world-coordinate magnitude
needed to reach the same lattice limit is multiplied by **4**.

| Channel | regular nominal 64-bit N | `_large` nominal 64-bit N |
| --- | ---: | ---: |
| temperature | 9,276,577,482,669,747,516,696 | 37,106,309,930,678,990,066,784 |
| vegetation | 4,638,288,741,334,873,758,348 | 18,553,154,965,339,495,033,392 |
| continentalness | 72,473,261,583,357,402,475 | 289,893,046,333,429,609,897 |
| erosion | 1,159,572,185,333,718,439,587 | 4,638,288,741,334,873,758,348 |

the factor of four is a **scale relationship**, not an exact identity between
rounded table integers or seeded first-failure coordinates. the nominal table
also groups some channels; use the exact zero-shift catalog for precise walls.

crucially `NoiseRouterData.overworld` **selects regular OR large-biome noises**
for these channels. it does not combine regular micro-variation with a `_large`
macro backdrop in one world. ordinary settings use the regular channels;
large-biomes settings substitute the `_large` channels, including the terrain
continentalness/erosion splines. ridge and the shared shifts are not similarly
replaced. therefore the two reference distances describe failures in different
settings, **not two successive local-then-continental climate collapses in
every world**. stable shifts make this comparison meaningful without the
shared shift blowout eclipsing the later large-biome sampler boundaries.

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

seed 0 as always.
scales include the SurfaceSystem call sites;
SurfaceRules samples its named noise at scale 1. these affect only the rules /
biome features that consume them, not necessarily terrain density

### 32-bit / lossy

| Input / stack | X/Z scale | +X | +Z | −Z | −X |
| --- | ---: | ---: | ---: | ---: | ---: |
| surface / second | 1.0 | 33,747,990,847 | 33,747,989,218 | -33,747,995,351 | -33,747,993,723 |
| surface / first | 1.0 | 34,359,735,509 | 34,359,737,773 | -34,359,738,964 | -34,359,741,228 |
| surface_secondary / second | 1.0 | 16,873,994,941 | 16,873,995,192 | -16,873,997,094 | -16,873,997,344 |
| surface_secondary / first | 1.0 | 17,179,867,586 | 17,179,868,359 | -17,179,870,010 | -17,179,870,783 |
| clay_bands_offset / second | 1.0 | 539,967,862,633 | 539,967,822,334 | -539,967,930,764 | -539,967,890,465 |
| clay_bands_offset / first | 1.0 | 549,755,793,493 | 549,755,767,101 | -549,755,860,676 | -549,755,834,284 |
| badlands_pillar / second | 0.2 | 5,273,123,686 | 5,273,123,748 | -5,273,123,842 | -5,273,123,904 |
| badlands_pillar / first | 0.2 | 5,368,709,016 | 5,368,708,734 | -5,368,709,507 | -5,368,709,225 |
| badlands_pillar_roof / second | 0.75 | 719,957,124,994 | 719,957,112,110 | -719,957,225,354 | -719,957,212,470 |
| badlands_pillar_roof / first | 0.75 | 733,007,704,062 | 733,007,725,226 | -733,007,778,476 | -733,007,799,640 |
| badlands_surface / second | 1.0 | 33,747,990,236 | 33,747,990,685 | -33,747,993,884 | -33,747,994,334 |
| badlands_surface / first | 1.0 | 34,359,734,389 | 34,359,735,943 | -34,359,740,794 | -34,359,742,348 |
| iceberg_surface / second | 1.0 | 33,747,991,943 | 33,747,991,447 | -33,747,993,123 | -33,747,992,627 |
| iceberg_surface / first | 1.0 | 34,359,738,143 | 34,359,737,108 | -34,359,739,629 | -34,359,738,594 |
| iceberg_pillar / second | 1.28 | 13,182,808,351 | 13,182,808,572 | -13,182,810,402 | -13,182,810,622 |
| iceberg_pillar / first | 1.28 | 13,421,772,474 | 13,421,772,496 | -13,421,773,105 | -13,421,773,127 |
| iceberg_pillar_roof / second | 1.17 | 14,422,217,299 | 14,422,218,024 | -14,422,219,827 | -14,422,220,552 |
| iceberg_pillar_roof / first | 1.17 | 14,683,647,999 | 14,683,648,321 | -14,683,649,431 | -14,683,649,752 |
| surface_swamp / second | 1.0 | 8,436,997,727 | 8,436,997,284 | -8,436,998,859 | -8,436,998,416 |
| surface_swamp / first | 1.0 | 8,589,933,863 | 8,589,934,084 | -8,589,935,101 | -8,589,935,322 |
| calcite / second | 1.0 | 134,991,964,658 | 134,991,965,454 | -134,991,972,822 | -134,991,973,618 |
| calcite / first | 1.0 | 137,438,940,663 | 137,438,938,518 | -137,438,968,427 | -137,438,966,282 |
| gravel / second | 1.0 | 67,495,976,555 | 67,495,980,480 | -67,495,988,658 | -67,495,992,584 |
| gravel / first | 1.0 | 68,719,471,514 | 68,719,472,894 | -68,719,480,579 | -68,719,481,959 |
| packed_ice / second | 1.0 | 33,747,989,685 | 33,747,988,878 | -33,747,995,692 | -33,747,994,884 |
| packed_ice / first | 1.0 | 34,359,734,616 | 34,359,737,986 | -34,359,738,751 | -34,359,742,121 |
| powder_snow / second | 1.0 | 16,873,995,043 | 16,873,994,584 | -16,873,997,701 | -16,873,997,242 |
| powder_snow / first | 1.0 | 17,179,868,000 | 17,179,868,907 | -17,179,869,462 | -17,179,870,369 |
| nether_state_selector / second | 1.0 | 33,747,991,164 | 33,747,990,357 | -33,747,994,212 | -33,747,993,406 |
| nether_state_selector / first | 1.0 | 34,359,735,845 | 34,359,734,817 | -34,359,741,920 | -34,359,740,892 |
| ice / second | 1.0 | 4,218,498,915 | 4,218,498,652 | -4,218,499,420 | -4,218,499,158 |
| ice / first | 1.0 | 4,294,967,266 | 4,294,967,294 | -4,294,967,299 | -4,294,967,327 |
| soul_sand_layer / second | 1.0 | 2,109,249,426 | 2,109,249,326 | -2,109,249,711 | -2,109,249,610 |
| soul_sand_layer / first | 1.0 | 2,147,483,620 | 2,147,483,461 | -2,147,483,836 | -2,147,483,677 |
| gravel_layer / second | 1.0 | 2,109,249,400 | 2,109,249,373 | -2,109,249,663 | -2,109,249,636 |
| gravel_layer / first | 1.0 | 2,147,483,553 | 2,147,483,613 | -2,147,483,684 | -2,147,483,744 |
| patch / second | 1.0 | 2,109,249,336 | 2,109,249,477 | -2,109,249,560 | -2,109,249,700 |
| patch / first | 1.0 | 2,147,483,520 | 2,147,483,514 | -2,147,483,783 | -2,147,483,777 |
| netherrack / second | 1.0 | 2,109,249,345 | 2,109,249,371 | -2,109,249,666 | -2,109,249,692 |
| netherrack / first | 1.0 | 2,147,483,642 | 2,147,483,455 | -2,147,483,842 | -2,147,483,655 |
| nether_wart / second | 1.0 | 2,109,249,398 | 2,109,249,384 | -2,109,249,653 | -2,109,249,638 |
| nether_wart / first | 1.0 | 2,147,483,483 | 2,147,483,475 | -2,147,483,822 | -2,147,483,814 |

### 64-bit / precise

| Input / stack | X/Z scale | +X | +Z | −Z | −X |
| --- | ---: | ---: | ---: | ---: | ---: |
| surface / badlands_surface / iceberg_surface / packed_ice / nether_state_selector / second | 1.0 | 144,946,523,166,714,829,087 | = +X | -144,946,523,166,714,829,087 | = −Z |
| surface / badlands_surface / iceberg_surface / packed_ice / nether_state_selector / first | 1.0 | 147,573,952,589,676,437,504 | = +X | -147,573,952,589,676,437,504 | = −Z |
| surface_secondary / powder_snow / second | 1.0 | 72,473,261,583,357,418,567 | = +X | -72,473,261,583,357,418,567 | = −Z |
| surface_secondary / powder_snow / first | 1.0 | 73,786,976,294,838,222,848 | = +X | -73,786,976,294,838,222,848 | = −Z |
| clay_bands_offset / second | 1.0 | 2,319,144,370,667,437,144,697 | = +X | -2,319,144,370,667,437,144,697 | = −Z |
| clay_bands_offset / first | 1.0 | 2,361,183,241,434,822,877,184 | = +X | -2,361,183,241,434,822,877,184 | = −Z |
| badlands_pillar / second | 0.2 | 22,647,894,244,799,192,685 | = +X | -22,647,894,244,799,192,685 | = −Z |
| badlands_pillar / first | 0.2 | 23,058,430,092,136,943,360 | = +X | -23,058,430,092,136,943,360 | = −Z |
| badlands_pillar_roof / second | 0.75 | 3,092,192,494,223,249,520,899 | = +X | -3,092,192,494,223,249,520,899 | = −Z |
| badlands_pillar_roof / first | 0.75 | 3,148,244,321,913,097,164,118 | = +X | -3,148,244,321,913,097,164,118 | = −Z |
| iceberg_pillar / second | 1.28 | 56,619,735,611,997,983,641 | = +X | -56,619,735,611,997,983,641 | = −Z |
| iceberg_pillar / first | 1.28 | 57,646,075,230,342,360,400 | = +X | -57,646,075,230,342,360,400 | = −Z |
| iceberg_pillar_roof / second | 1.17 | 61,942,958,618,254,204,596 | = +X | -61,942,958,618,254,204,596 | = −Z |
| iceberg_pillar_roof / first | 1.17 | 63,065,791,704,989,937,889 | = +X | -63,065,791,704,989,937,889 | = −Z |
| surface_swamp / second | 1.0 | 36,236,630,791,678,709,284 | = +X | -36,236,630,791,678,709,284 | = −Z |
| surface_swamp / first | 1.0 | 36,893,488,147,419,111,424 | = +X | -36,893,488,147,419,111,424 | = −Z |
| calcite / second | 1.0 | 579,786,092,666,859,292,209 | = +X | -579,786,092,666,859,292,209 | = −Z |
| calcite / first | 1.0 | 590,295,810,358,705,725,440 | = +X | -590,295,810,358,705,725,440 | = −Z |
| gravel / second | 1.0 | 289,893,046,333,429,650,128 | = +X | -289,893,046,333,429,650,128 | = −Z |
| gravel / first | 1.0 | 295,147,905,179,352,866,816 | = +X | -295,147,905,179,352,866,816 | = −Z |
| ice / second | 1.0 | 18,118,315,395,839,352,631 | = +X | -18,118,315,395,839,352,631 | = −Z |
| ice / first | 1.0 | 18,446,744,073,709,553,665 | = +X | -18,446,744,073,709,553,665 | = −Z |
| soul_sand_layer / gravel_layer / patch / netherrack / nether_wart / second | 1.0 | 9,059,157,697,919,676,316 | = +X | -9,059,157,697,919,676,316 | = −Z |
| soul_sand_layer / gravel_layer / patch / netherrack / nether_wart / first | 1.0 | 9,223,372,036,854,776,833 | = +X | -9,223,372,036,854,776,833 | = −Z |

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

See the separate **64-bit / precise** table under [input boundaries](#noise-inputs);

the single-scale cave_layer call's `8` and the three aquifer scale arguments
scale Y only. the spaghetti thickness modulator's two-scale call uses X/Z=2;
these are different overloads

### Shifted climate inputs, zero-shift references

these rows turn off the added shift for the boundary calculation, **not** for
world generation. actual climate inputs add shift_x / shift_z, which depend on
position and can already have overflowed. these are exact zero-shift reference
walls, not exact global biome walls. offset is the noise underlying shift_x/z;
ShiftB rotates world X into the noise Y axis

See the separate **64-bit / precise** table under [input boundaries](#noise-inputs); values are not repeated here.

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

See the separate **64-bit / precise** table under [input boundaries](#surfacesystem--surfacerules-noise-inputs); values are not repeated here.

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

seed 0 . these are the current implementation's End
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
in both NormalNoise stacks. Their scaled inputs retain all 1088 coordinate bits,
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

## Stable-shifts: distant pillar-infinity exposure

seed 0, default overworld settings unchanged, **64bit-stable-shifts / precise**.
each direction below fixes the other world horizontal coordinate at **0** and
checks Y=-64..319 through actual `NoiseChunk` interpolation and noise-fill
materials. these are independently measured local boundaries, **not** the
32-bit coordinates multiplied by 2^32, and not global firsts over every Y/Z.
surface rules, structures and carvers arent relevant

### Cause and progression

`NoiseRouterData.pillars()` evaluates
`(2 * pillar + mapped_rareness) * mapped_thickness^3`. The already-extrapolating
inputs eventually overflow double arithmetic. Positive infinite pillars can
win the underground max; negative infinite pillars need not. Ordinary lerp in
`NoiseChunk.NoiseInterpolator` can then evaluate `Infinity - Infinity` or
`0 * Infinity`, producing NaN. Squeeze cannot repair NaN, and aquifer solid
selection (`density > 0`) rejects it. Direct router final density may remain
finite and saturated while actual interpolated density fails.

The shifts remain stabilized: this is a separate failure. Individual height
bands change abruptly; different corners/heights fail at different distances,
so the overall exposure can progress in stages. Negative directions can jump
straight into extensive failure because their failed floor produces enormous
negative fractions. The first NaN does not necessarily remove a solid block:
it can occur in an already-empty height band.

### Exact checked local interpolated boundaries, all four directions

"Outward" = first NaN-containing column **in the checked local window**;
"inward" is its adjacent column toward zero. each boundary was verified by an
exhaustive 33-column window (16 blocks inward through 16 blocks outward).
counts cover the full 384-height column

| Direction | Inward coordinate | Outward coordinate | NaN heights inward → outward | Solid blocks inward → outward | Top solid Y inward → outward |
| --- | ---: | ---: | --- | --- | --- |
| +X, Z=0 | 2,319,144,373,482,458,742,507 | **2,319,144,373,482,458,742,508** | 0 → 16 | 209 → 193 | 255 → 255 |
| −X, Z=0 | -2,319,144,370,667,437,144,696 | **-2,319,144,370,667,437,144,697** | 0 → 112 | 136 → 34 | 255 → 255 |
| +Z, X=0 | 2,319,144,373,009,074,352,935 | **2,319,144,373,009,074,352,936** | 0 → 16 | 207 → 207 | 255 → 255 |
| −Z, X=0 | -2,319,144,370,667,437,144,696 | **-2,319,144,370,667,437,144,697** | 0 → 240 | 207 → 80 | 255 → 31 |

the +Z row is an exact **NaN onset**, not yet solid loss in that column. the
other rows already remove solid noise-fill blocks

### Raw pillar checks versus interpolated effects

the following adjacent integer-input checks use the actual seeded pillar
function. Other=4 means Z=4 for X travel, or X=4 for Z travel. negative rows
use Other=0

| Direction | Y | Other | Last finite coordinate inward | First nonfinite coordinate outward | Pillar value inward → outward |
| --- | ---: | ---: | ---: | ---: | --- |
| +X | 128 | 4 | 2,319,144,373,477,576,476,466 | 2,319,144,373,477,576,476,467 | 1.797687469893245e308 → +Infinity |
| −X | -64 | 0 | -2,319,144,370,667,437,144,696 | -2,319,144,370,667,437,144,697 | -3.247194039676121e125 → -Infinity |
| +Z | 136 | 4 | 2,319,144,373,009,074,352,937 | 2,319,144,373,009,074,352,938 | 1.7976882462173448e308 → +Infinity |
| −Z | -64 | 0 | -2,319,144,370,667,437,144,696 | -2,319,144,370,667,437,144,697 | 4.963603700275303e125 → +Infinity |

for +X the earlier raw infinity does **not** immediately produce a NaN in the
measured column; later branch/interpolation conditions matter. for +Z the
affected grid plane is ...352,940, whose cell begins at ...352,936. interpolation
therefore fails before the raw integer-input boundary. on negative travel,
the interpolation cell orientation is different; do not blindly mirror the
positive `ceil(N/4)*4 - 4` rule.

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

## IEEE-754 infinity and NaN progression

these are **floating-point overflows**, not the earlier 2^31 / 2^63 lattice
failures. both generation modes use the same IEEE-754 binary64 (`double`)
limit; that does **not** give them the same world-coordinate failure points.
stable shifts prevent one route to failure, not compound cave overflow.
an overflowing mathematical result is not stored as a number above 2^1024:
it rounds to signed Infinity, and subsequent operations can produce NaN.

### Traced arithmetic paths

| Point | How Infinity / NaN appears | What can happen next |
| --- | --- | --- |
| ImprovedNoise smoothstep | the failed floor leaves an unbounded fraction; `t*t*t*(t*(t*6-15)+10)` can overflow | lerp multiplies these weights by gradient differences; several extrapolating axes compound the growth |
| Perlin / NormalNoise accumulation | weighted octave sums and the two-stack sum can overflow, or combine opposite infinities | the returned noise can be Infinity or NaN; a lattice failure alone is not yet floating-point overflow |
| shift-driven climate | shift outputs become coordinates for another extrapolating noise | repeated growth reaches Infinity/NaN much earlier than a world coordinate reaches the double limit |
| terrain splines / depth / sloped cheese | shifted climate values feed spline interpolation and density arithmetic | infinities can saturate some branches; invalid combinations propagate NaN |
| pillars | `(2*pillar + mapped_rareness) * mapped_thickness^3` | compound multiplication overflows while individual raw noises can still be finite; positive infinity can win the underground max |
| spaghetti roughness | `mapped_roughness_modulator * (abs(roughness)-0.4)` | the product can overflow even with two finite inputs |
| 2D spaghetti | the elevation/thickness sum is cubed before the final clamp | Infinity can be clamped; NaN cannot be repaired by that clamp |
| cave layer | `4 * cave_layer^2` | the square or subsequent multiplication can overflow before cave branch selection |
| other density / surface combinations | scaled sums, products, squares and cubes of extrapolated noise | use-site arithmetic can overflow before the underlying raw sampler does; surface failures are not automatically terrain-density failures |
| NoiseChunk interpolation | lerp subtracts endpoints and multiplies by the interpolation fraction | `Infinity-Infinity` and `0*Infinity` give NaN, including at cell endpoints |
| final solid selection | `density > 0` is false for NaN | the aquifer fluid/air branch runs; this is not lava at every height |
| End rings | signed-long squared-distance wrap feeds a negative value to sqrt | NaN without any double Infinity; this is the separate ring failure cataloged above |

### Consolidated Infinity / NaN coordinate catalog

**axes cannot generally be discarded.** the IEEE conversion limit is symmetric
in X/Z and sign, but seeded noise values, nested splines, products and branch
selection are not. `±X` means the two signs matched in the specified test;
`±X, ±Z` means all four matched. coordinate sign and **result sign** are separate.
even matched survey samples do not prove identical exact onsets.

the catalog combines all observed nonfinite event types from the probes with
the tighter local tests documented above. it is **not an exhaustive list of
global first failures**: those depend on seed, Y, transverse coordinate,
settings, precision, sampling/interpolation and nonmonotonic branch selection.
there is no single coordinate at which every instance of a named noise
permanently becomes infinite. a function can encounter NaN, later infinity,
and finite values again; some clamps conceal infinity entirely.

evidence labels:

- **S**: expanded `tools/InfinityProbe.java` survey, seed 0, precise, Y=64,
  other axis=0; 16 samples per power-of-two interval through the positive
  range below `2^127` (the historical survey cap). **first observed sample of that result kind**, not an
  exact first block. raw noise, registered overworld functions, shift functions,
  router outputs, cave-branch nodes and pre-clamp `4*cave_layer²` are checked.
- **L**: locally verified adjacent-coordinate boundary; conditions specified
  in the earlier stable-shifts pillar section. not a global first.
- **G**: measured first nonfinite density-grid sample on the earlier defined
  32-bit shift transect; not a first generated block.
- **E**: exact arithmetic threshold, with the stated conversion or End-ring
  operation, not necessarily a world-generation infinity.
- **P**: observed at the first affected 64-bit shift grid plane, other axis=0,
  Y=64; this is not an independently located infinity onset.
- **S2**: extended `InfinityProbe`, precise 64-bit ordinary and stable-shifts,
  Y=64, other axis=0, all four signed axes, 16 samples per power-of-two
  interval from exponent 94 through 1086 inclusive. Only new later events
  are added below; an event already present at the survey start is not a
  newly located onset. Full per-field/sign results, including unit-scale raw
  samplers and signed infinities, are in
  [`tools/worldgen-evidence/infinity.tsv`](tools/worldgen-evidence/infinity.tsv).
- **D**: `DistantTerrainProbe`, actual NoiseChunk columns or real
  BIOMES/NOISE/SURFACE chunk stages; exact conditions and local-window limits
  are specified in the [late-stage investigation](#beyond-the-old-survey-cap-density-death-and-surviving-ice).
  Neither a sampled empty chunk nor a locally verified edge proves a global
  last block.

`ordinary` and `stable` refer to the corresponding generator modes. `both
widths / both` covers ordinary and stable-shifts 32/64-bit modes. duplicate
registered/router outputs are merged below. powers are approximate `log2(N)`
unless an exact expression is supplied; the full column is the magnitude N.

scaled-input conversion references below use **one rounding of the exact product
`N * scale` to binary64**, with `T = 2^1024 − 2^970` and first integer
`N = ceil(T / scale)`. `tools/ScaledDoubleOverflowProbe.java` verifies both
signs and their inward neighbors. these are mathematical references **inside
the 1088-bit address domain**, not measured sampler/world failures: converting N to
double before multiplying, the second NormalNoise stack, octave scaling and
added climate shifts are different operations. the scales are from
`NoiseRouterData`: jagged has Y scale 0, spaghetti modulator/thickness/rarity
has Y scale 1, and shifted climate is horizontal. ore vein a/b, noodle ridge
a/b and ore_veininess use their listed scales on all three axes;
cave_entrance uses 0.75 horizontally but 0.5 vertically. noodle's stored
binary64 scale is exactly `6004799503160661 / 2^51`, **not exact 8/3**;
its reference below uses that stored coefficient. Infinity here describes the
converted input, not the returned noise or density.


| Event / evaluated field | Mode | Directions / conditions | Result | Evidence | Approximate power of two | Full coordinate magnitude N |
| --- | --- | --- | --- | --- | ---: | ---: |
| shift-driven final density | 32-bit ordinary | −X; first affected grid plane | NaN | G | ≈2^33.9741 | 16,873,996,804 |
| shift-driven final density | 32-bit ordinary | −Z; first affected grid plane | NaN | G | ≈2^33.9741 | 16,873,996,624 |
| shift-driven final density | 32-bit ordinary | +Z | NaN | G | ≈2^33.9741 | 16,874,464,128 |
| shift-driven final density | 32-bit ordinary | +X | NaN | G | ≈2^33.9741 | 16,874,602,452 |
| climate: T, V, C, E, ridges, folded ridges | 32-bit ordinary | ±X, ±Z | NaN | S | 2^34 | 17,179,869,184 |
| terrain: offset, depth, sloped_cheese, final density | 32-bit ordinary | ±X, ±Z | NaN | S | 2^34 | 17,179,869,184 |
| caves: cheese_layer, candidate, underground, selected | 32-bit ordinary | ±X, ±Z | NaN | S | 2^34 | 17,179,869,184 |
| End-ring squared distance → sqrt / island density (not double overflow) | End rings enabled | diagonal X=Z, each axis | NaN | E | ≈2^34 | 17,179,869,184 |
| End-ring squared distance → sqrt / island density (not double overflow) | End rings enabled | axis, other=0 | NaN | E | ≈2^34.5000 | 24,296,004,000 |
| offset → depth → sloped_cheese | 32-bit stable | ±Z | −Infinity | S | 2^38 | 274,877,906,944 |
| offset → depth → sloped_cheese | 32-bit stable | −X | −Infinity | S | ≈2^39.1699 | 618,475,290,624 |
| offset → depth → sloped_cheese | 32-bit stable | +X | −Infinity | S | ≈2^40.7549 | 1,855,425,871,872 |
| pillars and gated_pillars | 32-bit ordinary / stable | ±Z | −Infinity | S | ≈2^48.7004 | 457,396,837,154,816 |
| pillars and gated_pillars | 32-bit ordinary / stable | ±X | −Infinity | S | ≈2^48.8074 | 492,581,209,243,648 |
| continentalness | 64-bit ordinary | −X, first affected shift grid plane | NaN | P | ≈2^65.9741 | 72,473,261,583,357,418,568 |
| continentalness; depth | 64-bit ordinary | −Z for C; +Z for depth, first affected shift grid plane | Infinity for C; −Infinity for depth | P | ≈2^65.9741 | 72,473,261,583,357,418,568 |
| continentalness | 64-bit ordinary | ±X, ±Z | NaN | S | 2^66 | 73,786,976,294,838,206,464 |
| climate: T, V, E, ridges, folded ridges | 64-bit ordinary | +X, +Z | NaN | S | 2^66 | 73,786,976,294,838,206,464 |
| terrain: offset, depth, sloped_cheese, final density | 64-bit ordinary | +X, +Z | NaN | S | 2^66 | 73,786,976,294,838,206,464 |
| caves: cheese_layer, candidate, underground, selected | 64-bit ordinary | +X, +Z | NaN | S | 2^66 | 73,786,976,294,838,206,464 |
| climate: T, V, E, ridges, folded ridges | 64-bit ordinary | −X, −Z | NaN | S | ≈2^66.0875 | 78,398,662,313,265,594,368 |
| terrain: offset, depth, sloped_cheese, final density | 64-bit ordinary | −X, −Z | NaN | S | ≈2^66.0875 | 78,398,662,313,265,594,368 |
| caves: cheese_layer, candidate, underground, selected | 64-bit ordinary | −X, −Z | NaN | S | ≈2^66.0875 | 78,398,662,313,265,594,368 |
| erosion (already NaN at an earlier surveyed position) | 64-bit ordinary | +Z | +Infinity | S | 2^70 | 1,180,591,620,717,411,303,424 |
| raw pillar compound | 64-bit stable | −X, Y=-64, other=0 | −Infinity | L | ≈2^70.9741 | 2,319,144,370,667,437,144,697 |
| raw pillar compound | 64-bit stable | −Z, Y=-64, other=0 | +Infinity | L | ≈2^70.9741 | 2,319,144,370,667,437,144,697 |
| interpolated final density | 64-bit stable | −X / −Z, column Y=-64..319 | NaN | L | ≈2^70.9741 | 2,319,144,370,667,437,144,697 |
| interpolated final density | 64-bit stable | +Z, column Y=-64..319 | NaN | L | ≈2^70.9741 | 2,319,144,373,009,074,352,936 |
| raw pillar compound | 64-bit stable | +Z, Y=136, other=4 | +Infinity | L | ≈2^70.9741 | 2,319,144,373,009,074,352,938 |
| raw pillar compound | 64-bit stable | +X, Y=128, other=4 | +Infinity | L | ≈2^70.9741 | 2,319,144,373,477,576,476,467 |
| interpolated final density | 64-bit stable | +X, column Y=-64..319 | NaN | L | ≈2^70.9741 | 2,319,144,373,482,458,742,508 |
| pillars and gated_pillars | 64-bit ordinary / stable | ±X, ±Z, Y=64 | −Infinity | S | 2^71 | 2,361,183,241,434,822,606,848 |
| spaghetti roughness product | both widths / both | ±X | +Infinity | S | ≈2^91.8580 | 4,487,532,642,409,503,496,509,325,312 |
| spaghetti roughness product | both widths / both | ±Z | −Infinity | S | ≈2^91.8580 | 4,487,532,642,409,503,496,509,325,312 |
| pre-clamp cave layer: `4*cave_layer²` | both widths / both | ±Z | +Infinity | S | ≈2^92.9542 | 9,594,035,304,461,697,130,468,212,736 |
| cave cheese_layer sum (layer plus clamped cheese terms) | both widths, stable only | ±Z | +Infinity | S | ≈2^92.9542 | 9,594,035,304,461,697,130,468,212,736 |
| pre-clamp cave layer: `4*cave_layer²` | both widths / both | ±X | +Infinity | S | ≈2^93.1699 | 11,141,460,353,568,422,474,092,118,016 |
| cave cheese_layer sum (layer plus clamped cheese terms) | both widths, stable only | ±X | +Infinity | S | ≈2^93.1699 | 11,141,460,353,568,422,474,092,118,016 |
| BlendedNoise / sloped_cheese | 64-bit stable (base noise also ordinary) | ±Z; Y=64, other=0 | +Infinity | S2 | ≈2^162.7549 | 9,865,136,051,983,594,697,874,872,620,834,910,382,677,544,665,088 |
| BlendedNoise; stable sloped_cheese / cave selection / direct final density | 64-bit ordinary for base; stable for all listed fields | ±X; Y=64, other=0 | NaN | S2 | ≈2^162.8580 | 10,595,886,870,649,046,156,976,715,037,193,051,892,505,510,936,576 |
| BlendedNoise; stable sloped_cheese / cave selection / direct final density | 64-bit ordinary for base; stable for all listed fields | ±Z; Y=64, other=0 | NaN | S2 | ≈2^163.5236 | 16,807,268,829,305,383,559,342,375,576,237,254,726,043,224,244,224 |
| local bottom-band solid loss, **not permanent** | 64-bit stable | +Z, X=51, full column | 8 solids → 0; solids return 16 blocks later | D | ≈2^165.3878 | 61,190,897,294,192,142,547,271,579,610,383,892,756,375,251,976,164 |
| supplied teleport: NOISE → SURFACE | 64-bit stable | chunk containing X=51 and +Z=N | 0 solids → 22,259 ice/snow solids | D | ≈2^166.2523 | 111,414,603,535,684,224,740,921,180,149,999,999,999,999,999,999,999 |
| aquifer lava | 64-bit ordinary / stable | ±X; Y=64, other=0 | NaN | S2 | ≈2^171.0875 | 3,180,227,562,832,044,750,011,218,195,990,631,850,771,309,213,515,776 |
| compound pillars / gated pillars | 64-bit ordinary / stable | ±X; Y=64, other=0 | NaN | S2 | ≈2^171.4594 | 4,115,588,610,723,822,617,661,576,488,929,052,983,351,106,041,020,416 |
| aquifer lava | 64-bit ordinary / stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^171.7004 | 4,863,877,449,037,244,911,781,863,123,279,789,889,414,943,503,024,128 |
| compound pillars / gated pillars | 64-bit ordinary / stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^171.7549 | 5,050,949,658,615,600,485,311,934,781,867,474,115,930,902,868,525,056 |
| continentalness | 64-bit stable | ±X; Y=64, other=0 | NaN | S2 | ≈2^172.9542 | 11,598,476,993,858,045,558,864,442,832,436,422,043,989,480,661,057,536 |
| aquifer barrier | 64-bit ordinary / stable | ±X; Y=64, other=0 | NaN | S2 | ≈2^173.0875 | 12,720,910,251,328,179,000,044,872,783,962,527,403,085,236,854,063,104 |
| furthest nonempty SURFACE chunk **in the specified sparse survey** | 64-bit stable | +Z, requested X=51; actual ice at X=48 | 127 packed ice blocks, top Y=74 | D | ≈2^173.5850 | 17,958,932,119,522,135,058,886,879,224,417,685,745,532,099,088,089,088 |
| continentalness | 64-bit stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^173.8074 | 20,952,087,472,775,824,235,368,025,761,820,633,369,787,448,936,103,936 |
| unit-scale surface / iceberg_surface samples (not surface feature outcome) | 64-bit ordinary / stable | ±X; Y=64, other=0 | NaN | S2 | ≈2^174.0875 | 25,441,820,502,656,358,000,089,745,567,925,054,806,170,473,708,126,208 |
| ore gap | 64-bit ordinary / stable | ±X; Y=64, other=0 | NaN | S2 | ≈2^174.9069 | 44,897,330,298,805,337,647,217,198,061,044,214,363,830,247,720,222,720 |
| spaghetti roughness product; aquifer spread | 64-bit ordinary / stable | ±X; Y=64, other=0 | NaN | S2 | ≈2^175.0875 | 50,883,641,005,312,716,000,179,491,135,850,109,612,340,947,416,252,416 |
| unit-scale surface sample | 64-bit ordinary / stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^175.1699 | 53,876,796,358,566,405,176,660,637,673,253,057,236,596,297,264,267,264 |
| spaghetti roughness product; aquifer spread | 64-bit ordinary / stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^175.5236 | 68,842,573,124,834,851,059,066,370,360,267,795,357,873,046,504,341,504 |
| noodles | 64-bit ordinary / stable | ±X; Y=64, other=0 | NaN | S2 | ≈2^175.5850 | 71,835,728,478,088,540,235,547,516,897,670,742,982,128,396,352,356,352 |
| ore gap | 64-bit ordinary / stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^175.5850 | 71,835,728,478,088,540,235,547,516,897,670,742,982,128,396,352,356,352 |
| unit-scale iceberg_surface sample | 64-bit ordinary / stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^175.7004 | 77,822,039,184,595,918,588,509,809,972,476,638,230,639,096,048,386,048 |
| locally verified all-height interpolated density loss | 64-bit stable | +Z, X=51, Y=-64..319 | 376 → 384 NaN heights | D | ≈2^175.7854 | 82,541,967,024,202,331,355,893,425,568,107,005,193,777,286,113,028,040 |
| 2D spaghetti | 64-bit ordinary / stable | ±X; Y=64, other=0 | NaN | S2 | ≈2^176.0875 | 101,767,282,010,625,432,000,358,982,271,700,219,224,681,894,832,504,832 |
| 2D spaghetti | 64-bit ordinary / stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^176.2479 | 113,739,903,423,640,188,706,283,568,421,312,009,721,703,294,224,564,224 |
| erosion / ridges / offset / depth / initial density without jaggedness | 64-bit stable | ±X; Y=64, other=0 | NaN | S2 | ≈2^177.0875 | 203,534,564,021,250,864,000,717,964,543,400,438,449,363,789,665,009,664 |
| aquifer floodedness | 64-bit ordinary / stable | ±X; Y=64, other=0 | NaN | S2 | ≈2^177.2479 | 227,479,806,847,280,377,412,567,136,842,624,019,443,406,588,449,128,448 |
| aquifer floodedness | 64-bit ordinary / stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^177.9069 | 359,178,642,390,442,701,177,737,584,488,353,714,910,641,981,761,781,760 |
| erosion | 64-bit stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^177.9542 | 371,151,263,803,457,457,883,662,170,637,965,505,407,663,381,153,841,152 |
| noodles | 64-bit ordinary / stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^178.1699 | 431,014,370,868,531,241,413,285,101,386,024,457,892,770,378,114,138,112 |
| ridges / offset / depth / initial density without jaggedness | 64-bit stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^179.0875 | 814,138,256,085,003,456,002,871,858,173,601,753,797,455,158,660,038,656 |
| vegetation / humidity | 64-bit stable | ±X; Y=64, other=0 | NaN | S2 | ≈2^179.2479 | 909,919,227,389,121,509,650,268,547,370,496,077,773,626,353,796,513,792 |
| temperature | 64-bit stable | ±X; Y=64, other=0 | NaN | S2 | ≈2^180.2479 | 1,819,838,454,778,243,019,300,537,094,740,992,155,547,252,707,593,027,584 |
| vegetation / humidity | 64-bit stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^180.6439 | 2,394,524,282,602,951,341,184,917,229,922,358,099,404,279,878,411,878,400 |
| temperature | 64-bit stable | ±Z; Y=64, other=0 | NaN | S2 | ≈2^181.3219 | 3,831,238,852,164,722,145,895,867,567,875,772,959,046,847,805,459,005,440 |
| aquifer barrier, exceptionally late first sampled NaN (earlier values include −Infinity) | 64-bit ordinary / stable | ±Z; Y=64, other=0 | NaN | S2 | exact 2^1026 | 719077253944926363091722076315609893447190791576922629093720324630930703222003852530833909289630144084480455519485573430635159075257666489971389722557896497511071573699461941105208878404984376477812331808340023075352602729369851525895652442163308948653402042738345192959788983753918865219341425318496896548864 |
| positive world-coordinate storage limit | signed Int1088, all modes | any positive axis | not a sampler Infinity onset; absolute binary64 projection overflows | E | 2^1087 − 1 | exact `(1 << 1087) - 1` |
| negative world-coordinate storage limit (magnitude) | signed Int1088, all modes | any negative axis | not a sampler Infinity onset; absolute binary64 projection overflows | E | 2^1087 | exact `1 << 1087` |
| jagged scaled-input conversion reference, **inside address domain** | IEEE-754 round-to-nearest, ties-to-even | scale 1500.0, ±X / ±Z; exact product then convert | ±Infinity | E | ≈2^1013.4493; exact ceil(T / 1500) | 119846208990821053862485980936868943386622755140025217957449185986963312195176500631099345318391471397553524277795258607297703698567960268420438325781003880454605934667138922450849236563878474354509650486846713237047577973081895276546573889957650195146049445629577007136228474373133005395361920118602782999 |
| ore_vein_a / b scaled-input conversion reference, **inside address domain** | IEEE-754 round-to-nearest, ties-to-even | scale 4.0, any axis, either sign; exact product then convert | ±Infinity | E | 2^1022 − 2^968 | 44942328371557895198432242851325853769983533177509456734043444745111242073191187736662254494396801774082571604173221977736638886962985100657664372167876455170477225500177095919068463711454427882941118932567517463892841739905710728704965208734118823179768542111091377676085677889924877023260720044476043624448 |
| noodle_ridge_a / b scaled-input conversion reference, **inside address domain** | IEEE-754 round-to-nearest, ties-to-even | scale 2.6666666666666665 (stored binary64), any axis, either sign; exact product then convert | ±Infinity | E | ≈2^1022.5850; exact ceil(T · 2^51 / 6004799503160661) | 67413492557336846539848944654588427510674136710336496477536280434149753427062861174765678995902826007920042704951772509122046163305406233434817786489802796641662960034324556978613332350467285294794906107031877163314306505878423580552717416452810213936256441506719861839980217226929893614313258623609084051456 |
| spaghetti modulator/thickness/rarity scaled-input conversion reference, **inside address domain** | IEEE-754 round-to-nearest, ties-to-even | scale 2.0, ±X / ±Z; exact product then convert | ±Infinity | E | 2^1023 − 2^969 | 89884656743115790396864485702651707539967066355018913468086889490222484146382375473324508988793603548165143208346443955473277773925970201315328744335752910340954451000354191838136927422908855765882237865135034927785683479811421457409930417468237646359537084222182755352171355779849754046521440088952087248896 |
| ore_veininess scaled-input conversion reference, **inside address domain** | IEEE-754 round-to-nearest, ties-to-even | scale 1.5 (3/2), any axis, either sign; exact product then convert | ±Infinity | E | ≈2^1023.4150; exact ceil(2T / 3) | 119846208990821053862485980936868943386622755140025217957449185986963312195176500631099345318391471397553524277795258607297703698567960268420438325781003880454605934667138922450849236563878474354509650486846713237047577973081895276546573889957650195146049445629577007136228474373133005395361920118602782998528 |
| largest finite binary64 value, **not a world boundary** | IEEE-754 double | either sign; axes irrelevant | finite ±Double.MAX_VALUE | E | ≈2^1024; exact 2^1024 − 2^971 | 179769313486231570814527423731704356798070567525844996598917476803157260780028538760589558632766878171540458953514382464234321326889464182768467546703537516986049910576551282076245490090389328944075868508455133942304583236903222948165808559332123348274797826204144723168738177180919299881250404026184124858368 |
| first exact integer magnitude converting to binary64 Infinity | IEEE-754 round-to-nearest, ties-to-even | ±, axes irrelevant; inside 1088-bit address domain | ±Infinity | E | ≈2^1024; exact **2^1024 − 2^970** | 179769313486231580793728971405303415079934132710037826936173778980444968292764750946649017977587207096330286416692887910946555547851940402630657488671505820681908902000708383676273854845817711531764475730270069855571366959622842914819860834936475292719074168444365510704342711559699508093042880177904174497792 |
| cave_entrance scaled-input conversion reference, **inside address domain** | IEEE-754 round-to-nearest, ties-to-even | scale 0.75 (3/4), ±X / ±Z; exact product then convert | ±Infinity | E | ≈2^1024.4150; exact (2^1026 − 2^972 + 1) / 3 | 239692417981642107724971961873737886773245510280050435914898371973926624390353001262198690636782942795107048555590517214595407397135920536840876651562007760909211869334277844901698473127756948709019300973693426474095155946163790553093147779915300390292098891259154014272456948746266010790723840237205565997056 |
| zero-shift climate scaled-input conversion reference, **inside address domain** | IEEE-754 round-to-nearest, ties-to-even | scale 0.25, ±X / ±Z; exact product then convert | ±Infinity | E | 2^1026 − 2^972 | 719077253944926323174915885621213660319736530840151307744695115921779873171059003786596071910348828385321145666771551643786222191407761610522629954686023282727635608002833534705095419383270846127057902921080279422285467838491371659279443339745901170876296673777462042817370846238798032372171520711616697991168 |

knot assumed; this does not prove matching behavior between samples.

last blocks:

| Transect | First sampled column with zero noise-fill solids | NaN heights there | First sampled column with all 384 interpolated heights NaN |
| --- | ---: | ---: | ---: |
| ±X, Z=0; magnitudes | 10,230,511,461,316,320,427,425,793,829,013,981,137,591,527,800,832 | 320 | 83,808,349,891,103,296,941,472,103,047,282,533,479,149,795,744,415,744 |
| ±Z, X=0; magnitudes | 35,076,039,295,941,670,036,888,435,985,190,792,471,742,381,031,424 | 320 | 119,726,214,130,147,567,059,245,861,496,117,904,970,213,993,920,593,920 |
| +Z, X=51 | 70,152,078,591,883,340,073,776,871,970,381,584,943,484,762,062,848 | 320 | 83,808,349,891,103,296,941,472,103,047,282,533,479,149,795,744,415,744 |

empty does **not** require all 384 densities to be NaN. remaining finite
heights may have nonpositive density. conversely, a direct-router positive
sample does not imply an interpolated solid block.
