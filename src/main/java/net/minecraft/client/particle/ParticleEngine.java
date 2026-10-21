package net.minecraft.client.particle;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ParticleEngine implements PreparableReloadListener {
    private static final int MAX_PARTICLES_PER_LAYER = 16384;
    private static final List<ParticleRenderType> RENDER_ORDER = ImmutableList.of(ParticleRenderType.TERRAIN_SHEET, ParticleRenderType.PARTICLE_SHEET_OPAQUE, ParticleRenderType.PARTICLE_SHEET_LIT, ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT, ParticleRenderType.CUSTOM);
    protected ClientLevel level;
    private final Map<ParticleRenderType, Queue<Particle>> particles = Maps.newIdentityHashMap();
    private final Queue<TrackingEmitter> trackingEmitters = Queues.newArrayDeque();
    private final TextureManager textureManager;
    private final RandomSource random = RandomSource.create();
    private final Int2ObjectMap<ParticleProvider<?>> providers = new Int2ObjectOpenHashMap<>();
    private final Queue<Particle> particlesToAdd = Queues.newArrayDeque();
    private final Map<ResourceLocation, ParticleEngine.MutableSpriteSet> spriteSets = Maps.newHashMap();
    private final TextureAtlas textureAtlas;
    private final Object2IntOpenHashMap<ParticleGroup> trackedParticleCounts = new Object2IntOpenHashMap<>();

    public ParticleEngine(ClientLevel pLevel, TextureManager pTextureManager) {
        this.textureAtlas = new TextureAtlas(TextureAtlas.LOCATION_PARTICLES);
        pTextureManager.register(this.textureAtlas.location(), this.textureAtlas);
        this.level = pLevel;
        this.textureManager = pTextureManager;
        this.registerProviders();
    }

    private void registerProviders() {
        this.register(ParticleTypes.AMBIENT_ENTITY_EFFECT, SpellParticle.AmbientMobProvider::new);
        this.register(ParticleTypes.ANGRY_VILLAGER, HeartParticle.AngryVillagerProvider::new);
        this.register(ParticleTypes.BLOCK_MARKER, new BlockMarker.Provider());
        this.register(ParticleTypes.BLOCK, new TerrainParticle.Provider());
        this.register(ParticleTypes.BUBBLE, BubbleParticle.Provider::new);
        this.register(ParticleTypes.BUBBLE_COLUMN_UP, BubbleColumnUpParticle.Provider::new);
        this.register(ParticleTypes.BUBBLE_POP, BubblePopParticle.Provider::new);
        this.register(ParticleTypes.CAMPFIRE_COSY_SMOKE, CampfireSmokeParticle.CosyProvider::new);
        this.register(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, CampfireSmokeParticle.SignalProvider::new);
        this.register(ParticleTypes.CLOUD, PlayerCloudParticle.Provider::new);
        this.register(ParticleTypes.COMPOSTER, SuspendedTownParticle.ComposterFillProvider::new);
        this.register(ParticleTypes.CRIT, CritParticle.Provider::new);
        this.register(ParticleTypes.CURRENT_DOWN, WaterCurrentDownParticle.Provider::new);
        this.register(ParticleTypes.DAMAGE_INDICATOR, CritParticle.DamageIndicatorProvider::new);
        this.register(ParticleTypes.DRAGON_BREATH, DragonBreathParticle.Provider::new);
        this.register(ParticleTypes.DOLPHIN, SuspendedTownParticle.DolphinSpeedProvider::new);
        this.register(ParticleTypes.DRIPPING_LAVA, DripParticle.LavaHangProvider::new);
        this.register(ParticleTypes.FALLING_LAVA, DripParticle.LavaFallProvider::new);
        this.register(ParticleTypes.LANDING_LAVA, DripParticle.LavaLandProvider::new);
        this.register(ParticleTypes.DRIPPING_WATER, DripParticle.WaterHangProvider::new);
        this.register(ParticleTypes.FALLING_WATER, DripParticle.WaterFallProvider::new);
        this.register(ParticleTypes.DUST, DustParticle.Provider::new);
        this.register(ParticleTypes.DUST_COLOR_TRANSITION, DustColorTransitionParticle.Provider::new);
        this.register(ParticleTypes.EFFECT, SpellParticle.Provider::new);
        this.register(ParticleTypes.ELDER_GUARDIAN, new MobAppearanceParticle.Provider());
        this.register(ParticleTypes.ENCHANTED_HIT, CritParticle.MagicProvider::new);
        this.register(ParticleTypes.ENCHANT, EnchantmentTableParticle.Provider::new);
        this.register(ParticleTypes.END_ROD, EndRodParticle.Provider::new);
        this.register(ParticleTypes.ENTITY_EFFECT, SpellParticle.MobProvider::new);
        this.register(ParticleTypes.EXPLOSION_EMITTER, new HugeExplosionSeedParticle.Provider());
        this.register(ParticleTypes.EXPLOSION, HugeExplosionParticle.Provider::new);
        this.register(ParticleTypes.SONIC_BOOM, SonicBoomParticle.Provider::new);
        this.register(ParticleTypes.FALLING_DUST, FallingDustParticle.Provider::new);
        this.register(ParticleTypes.FIREWORK, FireworkParticles.SparkProvider::new);
        this.register(ParticleTypes.FISHING, WakeParticle.Provider::new);
        this.register(ParticleTypes.FLAME, FlameParticle.Provider::new);
        this.register(ParticleTypes.SCULK_SOUL, SoulParticle.EmissiveProvider::new);
        this.register(ParticleTypes.SCULK_CHARGE, SculkChargeParticle.Provider::new);
        this.register(ParticleTypes.SCULK_CHARGE_POP, SculkChargePopParticle.Provider::new);
        this.register(ParticleTypes.SOUL, SoulParticle.Provider::new);
        this.register(ParticleTypes.SOUL_FIRE_FLAME, FlameParticle.Provider::new);
        this.register(ParticleTypes.FLASH, FireworkParticles.FlashProvider::new);
        this.register(ParticleTypes.HAPPY_VILLAGER, SuspendedTownParticle.HappyVillagerProvider::new);
        this.register(ParticleTypes.HEART, HeartParticle.Provider::new);
        this.register(ParticleTypes.INSTANT_EFFECT, SpellParticle.InstantProvider::new);
        this.register(ParticleTypes.ITEM, new BreakingItemParticle.Provider());
        this.register(ParticleTypes.ITEM_SLIME, new BreakingItemParticle.SlimeProvider());
        this.register(ParticleTypes.ITEM_SNOWBALL, new BreakingItemParticle.SnowballProvider());
        this.register(ParticleTypes.LARGE_SMOKE, LargeSmokeParticle.Provider::new);
        this.register(ParticleTypes.LAVA, LavaParticle.Provider::new);
        this.register(ParticleTypes.MYCELIUM, SuspendedTownParticle.Provider::new);
        this.register(ParticleTypes.NAUTILUS, EnchantmentTableParticle.NautilusProvider::new);
        this.register(ParticleTypes.NOTE, NoteParticle.Provider::new);
        this.register(ParticleTypes.POOF, ExplodeParticle.Provider::new);
        this.register(ParticleTypes.PORTAL, PortalParticle.Provider::new);
        this.register(ParticleTypes.RAIN, WaterDropParticle.Provider::new);
        this.register(ParticleTypes.SMOKE, SmokeParticle.Provider::new);
        this.register(ParticleTypes.SNEEZE, PlayerCloudParticle.SneezeProvider::new);
        this.register(ParticleTypes.SNOWFLAKE, SnowflakeParticle.Provider::new);
        this.register(ParticleTypes.SPIT, SpitParticle.Provider::new);
        this.register(ParticleTypes.SWEEP_ATTACK, AttackSweepParticle.Provider::new);
        this.register(ParticleTypes.TOTEM_OF_UNDYING, TotemParticle.Provider::new);
        this.register(ParticleTypes.SQUID_INK, SquidInkParticle.Provider::new);
        this.register(ParticleTypes.UNDERWATER, SuspendedParticle.UnderwaterProvider::new);
        this.register(ParticleTypes.SPLASH, SplashParticle.Provider::new);
        this.register(ParticleTypes.WITCH, SpellParticle.WitchProvider::new);
        this.register(ParticleTypes.DRIPPING_HONEY, DripParticle.HoneyHangProvider::new);
        this.register(ParticleTypes.FALLING_HONEY, DripParticle.HoneyFallProvider::new);
        this.register(ParticleTypes.LANDING_HONEY, DripParticle.HoneyLandProvider::new);
        this.register(ParticleTypes.FALLING_NECTAR, DripParticle.NectarFallProvider::new);
        this.register(ParticleTypes.FALLING_SPORE_BLOSSOM, DripParticle.SporeBlossomFallProvider::new);
        this.register(ParticleTypes.SPORE_BLOSSOM_AIR, SuspendedParticle.SporeBlossomAirProvider::new);
        this.register(ParticleTypes.ASH, AshParticle.Provider::new);
        this.register(ParticleTypes.CRIMSON_SPORE, SuspendedParticle.CrimsonSporeProvider::new);
        this.register(ParticleTypes.WARPED_SPORE, SuspendedParticle.WarpedSporeProvider::new);
        this.register(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, DripParticle.ObsidianTearHangProvider::new);
        this.register(ParticleTypes.FALLING_OBSIDIAN_TEAR, DripParticle.ObsidianTearFallProvider::new);
        this.register(ParticleTypes.LANDING_OBSIDIAN_TEAR, DripParticle.ObsidianTearLandProvider::new);
        this.register(ParticleTypes.REVERSE_PORTAL, ReversePortalParticle.ReversePortalProvider::new);
        this.register(ParticleTypes.WHITE_ASH, WhiteAshParticle.Provider::new);
        this.register(ParticleTypes.SMALL_FLAME, FlameParticle.SmallFlameProvider::new);
        this.register(ParticleTypes.DRIPPING_DRIPSTONE_WATER, DripParticle.DripstoneWaterHangProvider::new);
        this.register(ParticleTypes.FALLING_DRIPSTONE_WATER, DripParticle.DripstoneWaterFallProvider::new);
        this.register(ParticleTypes.DRIPPING_DRIPSTONE_LAVA, DripParticle.DripstoneLavaHangProvider::new);
        this.register(ParticleTypes.FALLING_DRIPSTONE_LAVA, DripParticle.DripstoneLavaFallProvider::new);
        this.register(ParticleTypes.VIBRATION, VibrationSignalParticle.Provider::new);
        this.register(ParticleTypes.GLOW_SQUID_INK, SquidInkParticle.GlowInkProvider::new);
        this.register(ParticleTypes.GLOW, GlowParticle.GlowSquidProvider::new);
        this.register(ParticleTypes.WAX_ON, GlowParticle.WaxOnProvider::new);
        this.register(ParticleTypes.WAX_OFF, GlowParticle.WaxOffProvider::new);
        this.register(ParticleTypes.ELECTRIC_SPARK, GlowParticle.ElectricSparkProvider::new);
        this.register(ParticleTypes.SCRAPE, GlowParticle.ScrapeProvider::new);
        this.register(ParticleTypes.SHRIEK, ShriekParticle.Provider::new);
    }

    private <T extends ParticleOptions> void register(ParticleType<T> pParticleType, ParticleProvider<T> pParticleFactory) {
        this.providers.put(Registry.PARTICLE_TYPE.getId(pParticleType), pParticleFactory);
    }

    private <T extends ParticleOptions> void register(ParticleType<T> pParticleType, ParticleEngine.SpriteParticleRegistration<T> pParticleMetaFactory) {
        ParticleEngine.MutableSpriteSet particleengine$mutablespriteset = new ParticleEngine.MutableSpriteSet();
        this.spriteSets.put(Registry.PARTICLE_TYPE.getKey(pParticleType), particleengine$mutablespriteset);
        this.providers.put(Registry.PARTICLE_TYPE.getId(pParticleType), pParticleMetaFactory.create(particleengine$mutablespriteset));
    }

    public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier pStage, ResourceManager pResourceManager, ProfilerFiller pPreparationsProfiler, ProfilerFiller pReloadProfiler, Executor pBackgroundExecutor, Executor pGameExecutor) {
        Map<ResourceLocation, List<ResourceLocation>> map = Maps.newConcurrentMap();
        CompletableFuture<?>[] completablefuture = Registry.PARTICLE_TYPE.keySet().stream().map((p_107315_) -> {
                                                                                               return CompletableFuture.runAsync(() -> {
                                                                                                   this.loadParticleDescription(pResourceManager, p_107315_, map);
                                                                                               }, pBackgroundExecutor);
                                                                                           })
                                                           .toArray((p_107303_) -> {
                                                               return new CompletableFuture[p_107303_];
                                                           });
        return CompletableFuture.allOf(completablefuture).thenApplyAsync((p_107324_) -> {
                                                             pPreparationsProfiler.startTick();
                                                             pPreparationsProfiler.push("stitching");
                                                             TextureAtlas.Preparations textureatlas$preparations = this.textureAtlas.prepareToStitch(pResourceManager, map.values().stream().flatMap(Collection::stream), pPreparationsProfiler, 0);
                                                             pPreparationsProfiler.pop();
                                                             pPreparationsProfiler.endTick();
                                                             return textureatlas$preparations;
                                                         }, pBackgroundExecutor).thenCompose(pStage::wait).thenAcceptAsync((p_107328_) -> {
            this.particles.clear();
            pReloadProfiler.startTick();
            pReloadProfiler.push("upload");
            this.textureAtlas.reload(p_107328_);
            pReloadProfiler.popPush("bindSpriteSets");
            TextureAtlasSprite textureatlassprite = this.textureAtlas.getSprite(MissingTextureAtlasSprite.getLocation());
            map.forEach((p_172268_, p_172269_) -> {
                ImmutableList<TextureAtlasSprite> immutablelist = p_172269_.isEmpty() ? ImmutableList.of(textureatlassprite) : p_172269_.stream().map(this.textureAtlas::getSprite).collect(ImmutableList.toImmutableList());
                this.spriteSets.get(p_172268_).rebind(immutablelist);
            });
            pReloadProfiler.pop();
            pReloadProfiler.endTick();
        }, pGameExecutor);
    }

    public void close() {
        this.textureAtlas.clearTextureData();
    }

    private void loadParticleDescription(ResourceManager pManager, ResourceLocation pRegistryName, Map<ResourceLocation, List<ResourceLocation>> pTextures) {
        ResourceLocation resourcelocation = new ResourceLocation(pRegistryName.getNamespace(), "particles/" + pRegistryName.getPath() + ".json");

        try {
            Reader reader = pManager.openAsReader(resourcelocation);

            try {
                ParticleDescription particledescription = ParticleDescription.fromJson(GsonHelper.parse(reader));
                List<ResourceLocation> list = particledescription.getTextures();
                boolean flag = this.spriteSets.containsKey(pRegistryName);
                if (list == null) {
                    if (flag) {
                        throw new IllegalStateException("Missing texture list for particle " + pRegistryName);
                    }
                } else {
                    if (!flag) {
                        throw new IllegalStateException("Redundant texture list for particle " + pRegistryName);
                    }

                    pTextures.put(pRegistryName, list.stream().map((p_107387_) -> {
                                                              return new ResourceLocation(p_107387_.getNamespace(), "particle/" + p_107387_.getPath());
                                                          })
                                                     .collect(Collectors.toList()));
                }
            } catch (Throwable throwable1) {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Throwable throwable) {
                        throwable1.addSuppressed(throwable);
                    }
                }

                throw throwable1;
            }

            if (reader != null) {
                reader.close();
            }

        } catch (IOException ioexception) {
            throw new IllegalStateException("Failed to load description for particle " + pRegistryName, ioexception);
        }
    }

    public void createTrackingEmitter(Entity pEntity, ParticleOptions pParticleData) {
        SectorVec3 position = pEntity.exactPosition();
        if (position == null)
            position = SectorVec3.fromApproximate(pEntity.getX(), pEntity.getY(), pEntity.getZ());
        SectorVec3 exactPosition = position;
        this.trackingEmitters.add(Particle.createAt(exactPosition, () -> new TrackingEmitter(this.level, pEntity, pParticleData)));
    }

    public void createTrackingEmitter(Entity pEntity, ParticleOptions pData, int pLifetime) {
        SectorVec3 position = pEntity.exactPosition();
        if (position == null)
            position = SectorVec3.fromApproximate(pEntity.getX(), pEntity.getY(), pEntity.getZ());
        SectorVec3 exactPosition = position;
        this.trackingEmitters.add(Particle.createAt(exactPosition, () -> new TrackingEmitter(this.level, pEntity, pData, pLifetime)));
    }

    /**
    * Legacy double entry point retained for third-party callers.  New particle
    * paths must supply the exact split coordinate overload below.
    */
    @Nullable
    public Particle createParticle(ParticleOptions pParticleData, double pX, double pY, double pZ, double pXSpeed, double pYSpeed, double pZSpeed) {
        return this.createParticle(pParticleData, SectorVec3.fromApproximate(pX, pY, pZ),
                pXSpeed, pYSpeed, pZSpeed);
    }

    /** Creates a particle whose world origin remains split-coordinate exact. */
    @Nullable
    public Particle createParticle(ParticleOptions options, SectorVec3 position, double xd, double yd, double zd) {
        Particle particle = this.makeParticle(options, position, xd, yd, zd);
        if (particle != null) {
            this.add(particle);
        }
        return particle;
    }

    @Nullable
    private <T extends ParticleOptions> Particle makeParticle(T options, SectorVec3 position, double xd, double yd, double zd) {
        ParticleProvider<T> provider = (ParticleProvider<T>) this.providers.get(Registry.PARTICLE_TYPE.getId(options.getType()));
        if (provider == null)
            return null;
        // Providers have vanilla's immutable double signature.  Give them a
        // small local X/Z frame and retain the exact world origin in Particle.
        // This also covers providers which save constructor coordinates in their
        // own animation fields (portal/enchantment/firework particles).
        return Particle.createAt(position, () -> provider.createParticle(options, this.level, 0.0D, position.y(), 0.0D, xd, yd, zd));
    }

    public void add(Particle pEffect) {
        Optional<ParticleGroup> optional = pEffect.getParticleGroup();
        if (optional.isPresent()) {
            if (this.hasSpaceInParticleLimit(optional.get())) {
                this.particlesToAdd.add(pEffect);
                this.updateCount(optional.get(), 1);
            }
        } else {
            this.particlesToAdd.add(pEffect);
        }
    }

    public void tick() {
        this.particles.forEach((p_107349_, p_107350_) -> {
            this.level.getProfiler().push(p_107349_.toString());
            this.tickParticleList(p_107350_);
            this.level.getProfiler().pop();
        });
        if (!this.trackingEmitters.isEmpty()) {
            List<TrackingEmitter> list = Lists.newArrayList();

            for (TrackingEmitter trackingemitter : this.trackingEmitters) {
                trackingemitter.tick();
                if (!trackingemitter.isAlive()) {
                    list.add(trackingemitter);
                }
            }

            this.trackingEmitters.removeAll(list);
        }

        Particle particle;
        if (!this.particlesToAdd.isEmpty()) {
            while ((particle = this.particlesToAdd.poll()) != null) {
                this.particles.computeIfAbsent(particle.getRenderType(), (p_107347_) -> {
                                  return EvictingQueue.create(16384);
                              }).add(particle);
            }
        }
    }

    private void tickParticleList(Collection<Particle> pParticles) {
        if (!pParticles.isEmpty()) {
            Iterator<Particle> iterator = pParticles.iterator();

            while (iterator.hasNext()) {
                Particle particle = iterator.next();
                this.tickParticle(particle);
                if (!particle.isAlive()) {
                    particle.getParticleGroup().ifPresent((p_172289_) -> {
                        this.updateCount(p_172289_, -1);
                    });
                    iterator.remove();
                }
            }
        }
    }

    private void updateCount(ParticleGroup pGroup, int pCount) {
        this.trackedParticleCounts.addTo(pGroup, pCount);
    }

    private void tickParticle(Particle pParticle) {
        try {
            pParticle.tick();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking Particle");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Particle being ticked");
            crashreportcategory.setDetail("Particle", pParticle::toString);
            crashreportcategory.setDetail("Particle Type", pParticle.getRenderType()::toString);
            throw new ReportedException(crashreport);
        }
    }

    public void render(PoseStack pMatrixStack, MultiBufferSource.BufferSource pBuffer, LightTexture pLightTexture, Camera pActiveRenderInfo, float pPartialTicks) {
        pLightTexture.turnOnLightLayer();
        RenderSystem.enableDepthTest();
        PoseStack posestack = RenderSystem.getModelViewStack();
        posestack.pushPose();
        posestack.mulPoseMatrix(pMatrixStack.last().pose());
        RenderSystem.applyModelViewMatrix();

        for (ParticleRenderType particlerendertype : RENDER_ORDER) {
            Iterable<Particle> iterable = this.particles.get(particlerendertype);
            if (iterable != null) {
                RenderSystem.setShader(GameRenderer::getParticleShader);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder bufferbuilder = tesselator.getBuilder();
                particlerendertype.begin(bufferbuilder, this.textureManager);

                for (Particle particle : iterable) {
                    try {
                        particle.render(bufferbuilder, pActiveRenderInfo, pPartialTicks);
                    } catch (Throwable throwable) {
                        CrashReport crashreport = CrashReport.forThrowable(throwable, "Rendering Particle");
                        CrashReportCategory crashreportcategory = crashreport.addCategory("Particle being rendered");
                        crashreportcategory.setDetail("Particle", particle::toString);
                        crashreportcategory.setDetail("Particle Type", particlerendertype::toString);
                        throw new ReportedException(crashreport);
                    }
                }

                particlerendertype.end(tesselator);
            }
        }

        posestack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        pLightTexture.turnOffLightLayer();
    }

    public void setLevel(@Nullable ClientLevel pLevel) {
        this.level = pLevel;
        this.particles.clear();
        this.trackingEmitters.clear();
        this.trackedParticleCounts.clear();
    }

    public void destroy(BlockPos pPos, BlockState pState) {
        if (!pState.isAir()) {
            VoxelShape voxelshape = pState.getShape(this.level, pPos);
            double d0 = 0.25D;
            voxelshape.forAllBoxes((p_172273_, p_172274_, p_172275_, p_172276_, p_172277_, p_172278_) -> {
                double d1 = Math.min(1.0D, p_172276_ - p_172273_);
                double d2 = Math.min(1.0D, p_172277_ - p_172274_);
                double d3 = Math.min(1.0D, p_172278_ - p_172275_);
                int i = Math.max(2, Mth.ceil(d1 / 0.25D));
                int j = Math.max(2, Mth.ceil(d2 / 0.25D));
                int k = Math.max(2, Mth.ceil(d3 / 0.25D));

                for (int l = 0; l < i; ++l) {
                    for (int i1 = 0; i1 < j; ++i1) {
                        for (int j1 = 0; j1 < k; ++j1) {
                            double d4 = ((double) l + 0.5D) / (double) i;
                            double d5 = ((double) i1 + 0.5D) / (double) j;
                            double d6 = ((double) j1 + 0.5D) / (double) k;
                            double d7 = d4 * d1 + p_172273_;
                            double d8 = d5 * d2 + p_172274_;
                            double d9 = d6 * d3 + p_172275_;
                            SectorVec3 position = SectorVec3.fromBlockAndFraction(pPos.getX(), d7, (double) pPos.getY() + d8, pPos.getZ(), d9);
                            this.add(Particle.createAt(position, () -> new TerrainParticle(this.level, 0.0D, position.y(), 0.0D, d4 - 0.5D, d5 - 0.5D, d6 - 0.5D, pState, pPos)));
                        }
                    }
                }
            });
        }
    }

    public void crack(BlockPos pPos, Direction pSide) {
        BlockState blockstate = this.level.getBlockState(pPos);
        if (blockstate.getRenderShape() != RenderShape.INVISIBLE) {
            int j = pPos.getY();
            float f = 0.1F;
            AABB aabb = blockstate.getShape(this.level, pPos).bounds();
            double localX = this.random.nextDouble() * (aabb.maxX - aabb.minX - (double) 0.2F) + (double) 0.1F + aabb.minX;
            double particleY = (double) j + this.random.nextDouble() * (aabb.maxY - aabb.minY - (double) 0.2F) + (double) 0.1F + aabb.minY;
            double localZ = this.random.nextDouble() * (aabb.maxZ - aabb.minZ - (double) 0.2F) + (double) 0.1F + aabb.minZ;
            if (pSide == Direction.DOWN) {
                particleY = (double) j + aabb.minY - (double) 0.1F;
            }

            if (pSide == Direction.UP) {
                particleY = (double) j + aabb.maxY + (double) 0.1F;
            }

            if (pSide == Direction.NORTH) {
                localZ = aabb.minZ - (double) 0.1F;
            }

            if (pSide == Direction.SOUTH) {
                localZ = aabb.maxZ + (double) 0.1F;
            }

            if (pSide == Direction.WEST) {
                localX = aabb.minX - (double) 0.1F;
            }

            if (pSide == Direction.EAST) {
                localX = aabb.maxX + (double) 0.1F;
            }

            SectorVec3 position = SectorVec3.fromBlockAndFraction(pPos.getX(), localX, particleY, pPos.getZ(), localZ);
            this.add(Particle.createAt(position, () -> (new TerrainParticle(this.level, 0.0D, position.y(), 0.0D, 0.0D, 0.0D, 0.0D, blockstate, pPos)).setPower(0.2F).scale(0.6F)));
        }
    }

    public String countParticles() {
        return String.valueOf(this.particles.values().stream().mapToInt(Collection::size).sum());
    }

    private boolean hasSpaceInParticleLimit(ParticleGroup pGroup) {
        return this.trackedParticleCounts.getInt(pGroup) < pGroup.getLimit();
    }

    @OnlyIn(Dist.CLIENT)
    static class MutableSpriteSet implements SpriteSet {
        private List<TextureAtlasSprite> sprites;

        public TextureAtlasSprite get(int pParticleAge, int pParticleMaxAge) {
            return this.sprites.get(pParticleAge * (this.sprites.size() - 1) / pParticleMaxAge);
        }

        public TextureAtlasSprite get(RandomSource pRandom) {
            return this.sprites.get(pRandom.nextInt(this.sprites.size()));
        }

        public void rebind(List<TextureAtlasSprite> pSprites) {
            this.sprites = ImmutableList.copyOf(pSprites);
        }
    }

    @FunctionalInterface
    @OnlyIn(Dist.CLIENT)
    interface SpriteParticleRegistration<T extends ParticleOptions> {
        ParticleProvider<T> create(SpriteSet pSprites);
    }
}
