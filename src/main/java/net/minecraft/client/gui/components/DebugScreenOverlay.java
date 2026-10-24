package net.minecraft.client.gui.components;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.math.Matrix4f;
import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Int128;
import net.minecraft.core.Int128Math;
import net.minecraft.core.Registry;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FrameTimer;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.RegionFileNames;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.SectorVec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DebugScreenOverlay extends GuiComponent {
    private static final int COLOR_GREY = 14737632;
    private static final int MARGIN_RIGHT = 2;
    private static final int MARGIN_LEFT = 2;
    private static final int MARGIN_TOP = 2;
    private final Minecraft minecraft;
    private final DebugScreenOverlay.AllocationRateCalculator allocationRateCalculator;
    private final Font font;
    private HitResult block;
    private HitResult liquid;
    @Nullable
    private ChunkPos lastPos;
    private final RegionDisplayCache regionDisplay = new RegionDisplayCache();
    private final PositionDisplayCache positionDisplay = new PositionDisplayCache();
    private final MemoryDisplayCache memoryDisplay = new MemoryDisplayCache();
    private int precisionBits = Integer.MIN_VALUE;
    private String precisionLine;
    private String chunkXLine;
    private String chunkZLine;
    private List<String> hardwareInformation;
    private int displayWidth = -1;
    private int displayHeight = -1;
    private String displayLine;
    // DecimalFormat is mutable; keep separate instances for callers on other threads.
    private static final ThreadLocal<NumberFormats> NUMBER_FORMATS = ThreadLocal.withInitial(NumberFormats::new);
    @Nullable
    private LevelChunk clientChunk;
    @Nullable
    private CompletableFuture<LevelChunk> serverChunk;
    private static final int RED = -65536;
    private static final int YELLOW = -256;
    private static final int GREEN = -16711936;

    public DebugScreenOverlay(Minecraft pMinecraft) {
        this.minecraft = pMinecraft;
        this.allocationRateCalculator = new DebugScreenOverlay.AllocationRateCalculator();
        this.font = pMinecraft.font;
    }

    public void clearChunkCache() {
        this.serverChunk = null;
        this.clientChunk = null;
    }

    public void render(PoseStack pPoseStack) {
        this.minecraft.getProfiler().push("debug");
        Entity entity = this.minecraft.getCameraEntity();
        this.block = entity.pick(20.0D, 0.0F, false);
        this.liquid = entity.pick(20.0D, 0.0F, true);
        this.drawGameInformation(pPoseStack);
        this.drawSystemInformation(pPoseStack);
        if (this.minecraft.options.renderFpsChart) {
            int i = this.minecraft.getWindow().getGuiScaledWidth();
            this.drawChart(pPoseStack, this.minecraft.getFrameTimer(), 0, i / 2, true);
            IntegratedServer integratedserver = this.minecraft.getSingleplayerServer();
            if (integratedserver != null) {
                this.drawChart(pPoseStack, integratedserver.getFrameTimer(), i - Math.min(i / 2, 240), i / 2, false);
            }
        }

        this.minecraft.getProfiler().pop();
    }

    protected void drawGameInformation(PoseStack pPoseStack) {
        List<String> list = this.getGameInformation();
        list.add("");
        boolean flag = this.minecraft.getSingleplayerServer() != null;
        list.add("Debug: Pie [shift]: " + (this.minecraft.options.renderDebugCharts ? "visible" : "hidden") + (flag ? " FPS + TPS" : " FPS") + " [alt]: " + (this.minecraft.options.renderFpsChart ? "visible" : "hidden"));

        for (int i = 0; i < list.size(); ++i) {
            String s = list.get(i);
            if (!Strings.isNullOrEmpty(s)) {
                int j = 9;
                int k = this.font.width(s);
                int l = 2;
                int i1 = 2 + j * i;
                fill(pPoseStack, 1, i1 - 1, 2 + k + 1, i1 + j - 1, -1873784752);
                this.font.draw(pPoseStack, s, 2.0F, (float) i1, 14737632);
            }
        }
    }

    protected void drawSystemInformation(PoseStack pPoseStack) {
        List<String> list = this.getSystemInformation();

        for (int i = 0; i < list.size(); ++i) {
            String s = list.get(i);
            if (!Strings.isNullOrEmpty(s)) {
                int j = 9;
                int k = this.font.width(s);
                int l = this.minecraft.getWindow().getGuiScaledWidth() - 2 - k;
                int i1 = 2 + j * i;
                fill(pPoseStack, l - 1, i1 - 1, l + k + 1, i1 + j - 1, -1873784752);
                this.font.draw(pPoseStack, s, (float) l, (float) i1, 14737632);
            }
        }
    }

    protected List<String> getGameInformation() {
        IntegratedServer integratedserver = this.minecraft.getSingleplayerServer();
        Connection connection = this.minecraft.getConnection().getConnection();
        float f = connection.getAverageSentPackets();
        float f1 = connection.getAverageReceivedPackets();
        String s;
        if (integratedserver != null) {
            s = "Integrated server @ " + fixed(integratedserver.getAverageTickTime(), 0) + " ms ticks, " + fixed(f, 0) + " tx, " + fixed(f1, 0) + " rx";
        } else {
            s = "\"" + this.minecraft.player.getServerBrand() + "\" server, " + fixed(f, 0) + " tx, " + fixed(f1, 0) + " rx";
        }

        BlockPos blockpos = this.minecraft.getCameraEntity().blockPosition();
        if (this.minecraft.showOnlyReducedInfo()) {
            return Lists.newArrayList("Minecraft " + SharedConstants.getCurrentVersion().getName() + " (" + this.minecraft.getLaunchedVersion() + "/" + ClientBrandRetriever.getClientModName() + ")", this.minecraft.fpsString, s, this.minecraft.levelRenderer.getChunkStatistics(), this.minecraft.levelRenderer.getEntityStatistics(), "P: " + this.minecraft.particleEngine.countParticles() + ". T: " + this.minecraft.level.getEntityCount(), "", "Chunk-relative: " + (blockpos.getXLo() & 15) + " " + (blockpos.getY() & 15) + " " + (blockpos.getZLo() & 15));
        } else {
            Entity entity = this.minecraft.getCameraEntity();
            Direction direction = entity.getDirection();
            String s1;
            switch (direction) {
                case NORTH:
                    s1 = "Towards negative Z";
                    break;
                case SOUTH:
                    s1 = "Towards positive Z";
                    break;
                case WEST:
                    s1 = "Towards negative X";
                    break;
                case EAST:
                    s1 = "Towards positive X";
                    break;
                default:
                    s1 = "Invalid";
            }

            if (!containsBlock(this.lastPos, blockpos)) {
                this.lastPos = new ChunkPos(blockpos);
                this.chunkXLine = "Chunk X: " + this.lastPos.x();
                this.chunkZLine = "Chunk Z: " + this.lastPos.z();
                this.clearChunkCache();
            }

            Level level = this.getLevel();
            ObjectSet<ChunkPos> longset = (level instanceof ServerLevel ? ((ServerLevel) level).getForcedChunks() : ObjectSets.EMPTY_SET);
            List<String> list = Lists.newArrayList("Minecraft " + SharedConstants.getCurrentVersion().getName() + " (" + this.minecraft.getLaunchedVersion() + "/" + ClientBrandRetriever.getClientModName() + ("release".equalsIgnoreCase(this.minecraft.getVersionType()) ? "" : "/" + this.minecraft.getVersionType()) + ")", this.minecraft.fpsString, s, this.minecraft.levelRenderer.getChunkStatistics(), this.minecraft.levelRenderer.getEntityStatistics(), "P: " + this.minecraft.particleEngine.countParticles() + ". T: " + this.minecraft.level.getEntityCount());
            list.add(this.minecraft.level.dimension().location() + " FC: " + longset.size());

            list.add("");
            SectorVec3 exact = entity.exactPosition();
            this.positionDisplay.update(exact != null ? exact : entity.sectorPosition());
            list.add(this.positionDisplay.xLine);
            list.add(this.positionDisplay.yLine);
            list.add(this.positionDisplay.zLine);
            list.add(this.positionDisplay.halvesLine);
            list.add("Block: [" + (blockpos.getXLo() & 15) + " " + (blockpos.getY() & 15) + " " + (blockpos.getZLo() & 15) + "]");
            this.regionDisplay.update(this.minecraft.gameRenderer.getMainCamera().getBlockPosition());
            list.add(this.chunkXLine);
            list.add(this.chunkZLine);
            list.add(this.regionDisplay.firstLine);
            list.add(this.regionDisplay.secondLine);
            list.add(this.regionDisplay.thirdLine);

            if (exact != null) {
                list.add(this.positionDisplay.subLine);
            }
            list.add("Facing: " + direction + " (" + s1 + ") (" + fixed(Mth.wrapDegrees(entity.getYRot()), 1) + " / " + fixed(Mth.wrapDegrees(entity.getXRot()), 1) + ")");
            int maxBit = Math.max(magnitudeBits(blockpos.getX()), magnitudeBits(blockpos.getZ()));
            maxBit = Math.max(maxBit, Math.getExponent(Math.abs(entity.getY())) + 1);

            if (this.precisionBits != maxBit) {
                this.precisionBits = maxBit;
                this.precisionLine = formatPrecision(Math.scalb(1.0, maxBit - 53), Math.scalb(1.0, maxBit - 24));
            }
            list.add(this.precisionLine);

            LevelChunk levelchunk = this.getClientChunk();
            if (levelchunk.isEmpty()) {
                list.add("Waiting for chunk...");
            } else {
                int i = this.minecraft.level.getChunkSource().getLightEngine().getRawBrightness(blockpos, 0);
                int j = this.minecraft.level.getBrightness(LightLayer.SKY, blockpos);
                int k = this.minecraft.level.getBrightness(LightLayer.BLOCK, blockpos);
                list.add("Client Light: " + i + " (" + j + " sky, " + k + " block)");
                LevelChunk levelchunk1 = this.getServerChunk();
                if (blockpos.getY() >= this.minecraft.level.getMinBuildHeight() && blockpos.getY() < this.minecraft.level.getMaxBuildHeight()) {
                    list.add("Biome: " + printBiome(this.minecraft.level.getBiome(blockpos)));
                    long l = 0L;
                    float f2 = 0.0F;
                    if (levelchunk1 != null) {
                        f2 = level.getMoonBrightness();
                        l = levelchunk1.getInhabitedTime();
                    }

                    DifficultyInstance difficultyinstance = new DifficultyInstance(level.getDifficulty(), level.getDayTime(), l, f2);
                    list.add("Local Difficulty: " + fixed(difficultyinstance.getEffectiveDifficulty(), 2) + " // " + fixed(difficultyinstance.getSpecialMultiplier(), 2) + " (Day " + this.minecraft.level.getDayTime() / 24000L + ")");
                }

                if (levelchunk1 != null && levelchunk1.isOldNoiseGeneration()) {
                    list.add("Blending: Old");
                }
            }

            PostChain postchain = this.minecraft.gameRenderer.currentEffect();
            if (postchain != null) {
                list.add("Shader: " + postchain.getName());
            }

            list.add("");
            List<String> diagnostics = this.minecraft.getConnection().getWorldgenDebugLines();
            if (diagnostics.isEmpty()) list.add("Worldgen debug: awaiting server data");
            else list.addAll(diagnostics);
            return list;
        }
    }

    private static String printBiome(Holder<Biome> pBiomeHolder) {
        return pBiomeHolder.unwrap().map((p_205377_) -> { return p_205377_.location().toString(); }, (p_205367_) -> { return "[unregistered " + p_205367_ + "]"; });
    }

    static String formatPrecision(double doublePrecision, double floatPrecision) {
        NumberFormats formats = NUMBER_FORMATS.get();
        return "Precision double: " + formats.doublePrecision.format(doublePrecision).replace('E', 'e')
                + " float: " + formats.floatPrecision.format(floatPrecision).replace('E', 'e');
    }

    static void addExactPositionLines(List<String> list, SectorVec3 exact) {
        list.add("X: " + formatCoordinate(exact.blockX(), exact.subX()));
        list.add("Y: " + fixed(exact.y(), 5));
        list.add("Z: " + formatCoordinate(exact.blockZ(), exact.subZ()));
    }

    static String formatCoordinateHalves(String axis, Int128 coordinate) {
        return axis + "Lo/Hi: " + Long.toHexString(coordinate.lo()) + " / " + Long.toHexString(coordinate.hi());
    }

    static String formatSubPosition(SectorVec3 exact) {
        return "SubPos: [X: " + signedFraction(exact.subX()) + ", Y: " + signedFraction(exact.subY()) + ", Z: " + signedFraction(exact.subZ()) + "]";
    }

    private static String fixed(double value, int digits) {
        return NUMBER_FORMATS.get().fixed[digits].format(value);
    }

    private static String signedFraction(double value) {
        return (Double.doubleToRawLongBits(value) < 0 ? "" : "+") + fixed(value, 7);
    }

    static String formatCoordinate(Int128 block, double fraction) {
        // SectorVec3 stores a floor block and a nonnegative fraction. Round the
        // decimal fraction separately, never converting the full coordinate to double.
        boolean negative = block.hi() < 0;
        int rounded = roundedThousandths(fraction, !negative);
        String integer;
        if (negative) {
            // Complementing the limbs gives abs(block) - 1, including Int128.MIN.
            Int128 magnitude = Int128.of(~block.hi(), ~block.lo());
            if (rounded == 0) {
                integer = block.toDecimalString();
            } else {
                integer = "-" + magnitude.toDecimalString();
                rounded = 1000 - rounded;
            }
        } else if (rounded == 1000) {
            // MAX + 1 is a valid display result even though it is not a signed Int128.
            integer = block.equals(Int128.MAX) ? "170141183460469231731687303715884105728" : block.add(Int128.ONE).toDecimalString();
            rounded = 0;
        } else {
            integer = block.toDecimalString();
        }
        // BigDecimal's historical display did not retain a negative zero.
        if (integer.equals("-0") && rounded == 0) integer = "0";
        return integer + "." + padded(rounded, 3);
    }

    private static int roundedThousandths(double fraction, boolean roundTieUp) {
        if (fraction == 0.0) return 0;
        String decimal = Double.toString(fraction);
        int exponentAt = decimal.indexOf('E');
        int exponent = exponentAt < 0 ? 0 : Integer.parseInt(decimal.substring(exponentAt + 1));
        int end = exponentAt < 0 ? decimal.length() : exponentAt;
        int point = decimal.indexOf('.');
        int position = point + exponent;
        int scaled = 0;
        int next = 0;
        boolean remainder = false;
        int digitIndex = 0;
        for (int i = 0; i < end; i++) {
            char c = decimal.charAt(i);
            if (c == '.') continue;
            int place = position - digitIndex++;
            int digit = c - '0';
            if (place >= -2 && place <= 1) {
                int multiplier = place == 1 ? 1000 : place == 0 ? 100 : place == -1 ? 10 : 1;
                scaled += digit * multiplier;
            } else if (place == -3) {
                next = digit;
            } else if (place < -3 && digit != 0) {
                remainder = true;
            }
        }
        return scaled + (next > 5 || next == 5 && (roundTieUp || remainder) ? 1 : 0);
    }

    static String padded(long value, int width) {
        String text = Long.toString(value);
        return "0".repeat(Math.max(0, width - text.length())) + text;
    }

    private static boolean containsBlock(@Nullable ChunkPos chunk, BlockPos block) {
        return chunk != null && chunk.equals(block.xHi() >> 4, Int128Math.rightShiftLo(block.xHi(), block.xLo(), 4),
                block.zHi() >> 4, Int128Math.rightShiftLo(block.zHi(), block.zLo(), 4));
    }

    private static final class NumberFormats {
        final DecimalFormat[] fixed = new DecimalFormat[8];
        final DecimalFormat doublePrecision = create("0.0000000000E0");
        final DecimalFormat floatPrecision = create("0.000000E0");

        NumberFormats() {
            for (int i = 0; i < this.fixed.length; i++) {
                this.fixed[i] = create(i == 0 ? "0" : "0." + "0".repeat(i));
            }
        }

        private static DecimalFormat create(String pattern) {
            DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT));
            format.setRoundingMode(RoundingMode.HALF_UP);
            return format;
        }
    }

    static final class PositionDisplayCache {
        private Int128 x;
        private Int128 z;
        private double subX = Double.NaN;
        private double subZ = Double.NaN;
        private double y = Double.NaN;
        String xLine, yLine, zLine, xHalves, zHalves, subLine;
        String halvesLine;

        void update(SectorVec3 position) {
            boolean xChanged = !position.blockX().equals(this.x);
            boolean zChanged = !position.blockZ().equals(this.z);
            boolean xFractionChanged = Double.doubleToLongBits(position.subX()) != Double.doubleToLongBits(this.subX);
            boolean zFractionChanged = Double.doubleToLongBits(position.subZ()) != Double.doubleToLongBits(this.subZ);
            boolean yChanged = Double.doubleToLongBits(position.y()) != Double.doubleToLongBits(this.y);
            boolean fractionsChanged = xFractionChanged || zFractionChanged || yChanged;
            if (xChanged) {
                this.x = position.blockX();
                this.xHalves = formatCoordinateHalves("x", this.x);
            }
            if (zChanged) {
                this.z = position.blockZ();
                this.zHalves = formatCoordinateHalves("z", this.z);
            }
            if (xChanged || zChanged) {
                this.halvesLine = this.xHalves + " | " + this.zHalves;
            }
            if (xChanged || xFractionChanged) this.xLine = "X: " + formatCoordinate(this.x, position.subX());
            if (zChanged || zFractionChanged) this.zLine = "Z: " + formatCoordinate(this.z, position.subZ());
            if (yChanged) this.yLine = "Y: " + fixed(position.y(), 5);
            if (fractionsChanged) this.subLine = formatSubPosition(position);
            this.subX = position.subX();
            this.subZ = position.subZ();
            this.y = position.y();
        }
    }

    private static int magnitudeBits(Int128 value) {
        // Unsigned magnitude also preserves the extra bit of Int128.MIN.
        long lo = value.hi() < 0 ? -value.lo() : value.lo();
        long hi = value.hi() < 0 ? ~value.hi() + (value.lo() == 0 ? 1 : 0) : value.hi();
        return hi != 0 ? 128 - Long.numberOfLeadingZeros(hi) : 64 - Long.numberOfLeadingZeros(lo);
    }

    static final class RegionDisplayCache {
        private ChunkPos chunk;
        private Int128 regionX;
        private Int128 regionZ;
        String firstLine;
        String secondLine;
        String thirdLine;

        void update(BlockPos block) {
            if (containsBlock(this.chunk, block)) return;
            this.chunk = new ChunkPos(block);
            this.update(this.chunk);
        }

        void update(ChunkPos chunk) {
            Int128 x = chunk.getRegionX();
            Int128 z = chunk.getRegionZ();
            if (!x.equals(this.regionX) || !z.equals(this.regionZ)) {
                this.regionX = x;
                this.regionZ = z;
                String decimalX = x.toDecimalString();
                String decimalZ = z.toDecimalString();
                this.firstLine = "Region X: " + decimalX;
                this.secondLine = "Region Z: " + decimalZ;
                this.thirdLine = " in " + RegionFileNames.regionFileName(x, z);
            }
        }
    }

    @Nullable
    private ServerLevel getServerLevel() {
        IntegratedServer integratedserver = this.minecraft.getSingleplayerServer();
        return integratedserver != null ? integratedserver.getLevel(this.minecraft.level.dimension()) : null;
    }

    private Level getLevel() {
        return DataFixUtils.orElse(Optional.ofNullable(this.minecraft.getSingleplayerServer()).flatMap((p_205373_) -> {
            return Optional.ofNullable(p_205373_.getLevel(this.minecraft.level.dimension()));
        }),
                this.minecraft.level);
    }

    @Nullable
    private LevelChunk getServerChunk() {
        if (this.serverChunk == null) {
            ServerLevel serverlevel = this.getServerLevel();
            if (serverlevel != null) {
                this.serverChunk = serverlevel.getChunkSource().getChunkFuture(this.lastPos.x(), this.lastPos.z(), ChunkStatus.FULL, false).thenApply((p_205369_) -> {
                    return p_205369_.map((p_205371_) -> { return (LevelChunk) p_205371_; }, (p_205363_) -> { return null; });
                });
            }

            if (this.serverChunk == null) {
                this.serverChunk = CompletableFuture.completedFuture(this.getClientChunk());
            }
        }

        return this.serverChunk.getNow((LevelChunk) null);
    }

    private LevelChunk getClientChunk() {
        if (this.clientChunk == null) {
            this.clientChunk = this.minecraft.level.getChunk(this.lastPos.x(), this.lastPos.z());
        }

        return this.clientChunk;
    }

    protected List<String> getSystemInformation() {
        long i = Runtime.getRuntime().maxMemory();
        long j = Runtime.getRuntime().totalMemory();
        long k = Runtime.getRuntime().freeMemory();
        long l = j - k;
        this.memoryDisplay.update(l, j, i, this.allocationRateCalculator.bytesAllocatedPerSecond(l));
        if (this.hardwareInformation == null) {
            this.hardwareInformation = List.of("Java: " + System.getProperty("java.version") + " " + (this.minecraft.is64Bit() ? 64 : 32) + "bit",
                    "CPU: " + GlUtil.getCpuInfo(), GlUtil.getRenderer(), GlUtil.getOpenGLVersion());
        }
        int width = this.minecraft.getWindow().getWidth();
        int height = this.minecraft.getWindow().getHeight();
        if (width != this.displayWidth || height != this.displayHeight) {
            this.displayWidth = width;
            this.displayHeight = height;
            this.displayLine = "Display: " + width + "x" + height + " (" + GlUtil.getVendor() + ")";
        }
        List<String> list = Lists.newArrayList(this.hardwareInformation.get(0),
                this.memoryDisplay.memoryLine, this.memoryDisplay.rateLine, this.memoryDisplay.allocatedLine, "",
                this.hardwareInformation.get(1), "", this.displayLine, this.hardwareInformation.get(2), this.hardwareInformation.get(3));
        if (this.minecraft.showOnlyReducedInfo()) {
            return list;
        } else {
            if (this.block.getType() == HitResult.Type.BLOCK) {
                BlockPos blockpos = ((BlockHitResult) this.block).getBlockPos();
                BlockState blockstate = this.minecraft.level.getBlockState(blockpos);
                list.add("");
                list.add(ChatFormatting.UNDERLINE + "Targeted Block");
                list.add("X: " + blockpos.getX());
                list.add("Y: " + blockpos.getY());
                list.add("Z: " + blockpos.getZ());
                list.add(String.valueOf((Object) Registry.BLOCK.getKey(blockstate.getBlock())));

                for (Map.Entry<Property<?>, Comparable<?>> entry : blockstate.getValues().entrySet()) {
                    list.add(this.getPropertyValueString(entry));
                }

                blockstate.getTags().map((p_205379_) -> {
                                        return "#" + p_205379_.location();
                                    })
                        .forEach(list::add);
            }

            if (this.liquid.getType() == HitResult.Type.BLOCK) {
                BlockPos blockpos1 = ((BlockHitResult) this.liquid).getBlockPos();
                FluidState fluidstate = this.minecraft.level.getFluidState(blockpos1);
                if (!fluidstate.isEmpty()) {
                    list.add("");
                    list.add(ChatFormatting.UNDERLINE + "Targeted Fluid");
                    list.add("X: " + blockpos1.getX());
                    list.add("Y: " + blockpos1.getY());
                    list.add("Z: " + blockpos1.getZ());
                    list.add(String.valueOf((Object) Registry.FLUID.getKey(fluidstate.getType())));

                    for (Map.Entry<Property<?>, Comparable<?>> entry1 : fluidstate.getValues().entrySet()) {
                        list.add(this.getPropertyValueString(entry1));
                    }

                    fluidstate.getTags().map((p_205365_) -> {
                                            return "#" + p_205365_.location();
                                        })
                            .forEach(list::add);
                }
            }

            Entity entity = this.minecraft.crosshairPickEntity;
            if (entity != null) {
                list.add("");
                list.add(ChatFormatting.UNDERLINE + "Targeted Entity");
                list.add(String.valueOf((Object) Registry.ENTITY_TYPE.getKey(entity.getType())));
            }

            return list;
        }
    }

    private String getPropertyValueString(Map.Entry<Property<?>, Comparable<?>> pEntry) {
        Property<?> property = pEntry.getKey();
        Comparable<?> comparable = pEntry.getValue();
        String s = Util.getPropertyName(property, comparable);
        if (Boolean.TRUE.equals(comparable)) {
            s = ChatFormatting.GREEN + s;
        } else if (Boolean.FALSE.equals(comparable)) {
            s = ChatFormatting.RED + s;
        }

        return property.getName() + ": " + s;
    }

    private void drawChart(PoseStack p_94059_, FrameTimer p_94060_, int p_94061_, int p_94062_, boolean p_94063_) {
        RenderSystem.disableDepthTest();
        int i = p_94060_.getLogStart();
        int j = p_94060_.getLogEnd();
        long[] along = p_94060_.getLog();
        int l = p_94061_;
        int i1 = Math.max(0, along.length - p_94062_);
        int j1 = along.length - i1;
        int $$8 = p_94060_.wrapIndex(i + i1);
        long k1 = 0L;
        int l1 = Integer.MAX_VALUE;
        int i2 = Integer.MIN_VALUE;

        for (int j2 = 0; j2 < j1; ++j2) {
            int k2 = (int) (along[p_94060_.wrapIndex($$8 + j2)] / 1000000L);
            l1 = Math.min(l1, k2);
            i2 = Math.max(i2, k2);
            k1 += (long) k2;
        }

        int k4 = this.minecraft.getWindow().getGuiScaledHeight();
        fill(p_94059_, p_94061_, k4 - 60, p_94061_ + j1, k4, -1873784752);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (Matrix4f matrix4f = Transformation.identity().getMatrix(); $$8 != j; $$8 = p_94060_.wrapIndex($$8 + 1)) {
            int l2 = p_94060_.scaleSampleTo(along[$$8], p_94063_ ? 30 : 60, p_94063_ ? 60 : 20);
            int i3 = p_94063_ ? 100 : 60;
            int j3 = this.getSampleColor(Mth.clamp(l2, 0, i3), 0, i3 / 2, i3);
            int k3 = j3 >> 24 & 255;
            int l3 = j3 >> 16 & 255;
            int i4 = j3 >> 8 & 255;
            int j4 = j3 & 255;
            bufferbuilder.vertex(matrix4f, (float) (l + 1), (float) k4, 0.0F).color(l3, i4, j4, k3).endVertex();
            bufferbuilder.vertex(matrix4f, (float) (l + 1), (float) (k4 - l2 + 1), 0.0F).color(l3, i4, j4, k3).endVertex();
            bufferbuilder.vertex(matrix4f, (float) l, (float) (k4 - l2 + 1), 0.0F).color(l3, i4, j4, k3).endVertex();
            bufferbuilder.vertex(matrix4f, (float) l, (float) k4, 0.0F).color(l3, i4, j4, k3).endVertex();
            ++l;
        }

        BufferUploader.drawWithShader(bufferbuilder.end());
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
        if (p_94063_) {
            fill(p_94059_, p_94061_ + 1, k4 - 30 + 1, p_94061_ + 14, k4 - 30 + 10, -1873784752);
            this.font.draw(p_94059_, "60 FPS", (float) (p_94061_ + 2), (float) (k4 - 30 + 2), 14737632);
            this.hLine(p_94059_, p_94061_, p_94061_ + j1 - 1, k4 - 30, -1);
            fill(p_94059_, p_94061_ + 1, k4 - 60 + 1, p_94061_ + 14, k4 - 60 + 10, -1873784752);
            this.font.draw(p_94059_, "30 FPS", (float) (p_94061_ + 2), (float) (k4 - 60 + 2), 14737632);
            this.hLine(p_94059_, p_94061_, p_94061_ + j1 - 1, k4 - 60, -1);
        } else {
            fill(p_94059_, p_94061_ + 1, k4 - 60 + 1, p_94061_ + 14, k4 - 60 + 10, -1873784752);
            this.font.draw(p_94059_, "20 TPS", (float) (p_94061_ + 2), (float) (k4 - 60 + 2), 14737632);
            this.hLine(p_94059_, p_94061_, p_94061_ + j1 - 1, k4 - 60, -1);
        }

        this.hLine(p_94059_, p_94061_, p_94061_ + j1 - 1, k4 - 1, -1);
        this.vLine(p_94059_, p_94061_, k4 - 60, k4, -1);
        this.vLine(p_94059_, p_94061_ + j1 - 1, k4 - 60, k4, -1);
        int l4 = this.minecraft.options.framerateLimit().get();
        if (p_94063_ && l4 > 0 && l4 <= 250) {
            this.hLine(p_94059_, p_94061_, p_94061_ + j1 - 1, k4 - 1 - (int) (1800.0D / (double) l4), -16711681);
        }

        String s = l1 + " ms min";
        String s1 = k1 / (long) j1 + " ms avg";
        String s2 = i2 + " ms max";
        this.font.drawShadow(p_94059_, s, (float) (p_94061_ + 2), (float) (k4 - 60 - 9), 14737632);
        this.font.drawShadow(p_94059_, s1, (float) (p_94061_ + j1 / 2 - this.font.width(s1) / 2), (float) (k4 - 60 - 9), 14737632);
        this.font.drawShadow(p_94059_, s2, (float) (p_94061_ + j1 - this.font.width(s2)), (float) (k4 - 60 - 9), 14737632);
        RenderSystem.enableDepthTest();
    }

    private int getSampleColor(int pHeight, int pHeightMin, int pHeightMid, int pHeightMax) {
        return pHeight < pHeightMid ? this.colorLerp(-16711936, -256, (float) pHeight / (float) pHeightMid) : this.colorLerp(-256, -65536, (float) (pHeight - pHeightMid) / (float) (pHeightMax - pHeightMid));
    }

    private int colorLerp(int pCol1, int pCol2, float pFactor) {
        int i = pCol1 >> 24 & 255;
        int j = pCol1 >> 16 & 255;
        int k = pCol1 >> 8 & 255;
        int l = pCol1 & 255;
        int i1 = pCol2 >> 24 & 255;
        int j1 = pCol2 >> 16 & 255;
        int k1 = pCol2 >> 8 & 255;
        int l1 = pCol2 & 255;
        int i2 = Mth.clamp((int) Mth.lerp(pFactor, (float) i, (float) i1), 0, 255);
        int j2 = Mth.clamp((int) Mth.lerp(pFactor, (float) j, (float) j1), 0, 255);
        int k2 = Mth.clamp((int) Mth.lerp(pFactor, (float) k, (float) k1), 0, 255);
        int l2 = Mth.clamp((int) Mth.lerp(pFactor, (float) l, (float) l1), 0, 255);
        return i2 << 24 | j2 << 16 | k2 << 8 | l2;
    }

    private static long bytesToMegabytes(long pBytes) {
        return pBytes / 1024L / 1024L;
    }

    private static String memoryPercent(long percent) {
        return (percent < 10 ? "  " : " ") + percent;
    }

    static final class MemoryDisplayCache {
        private long usedMb = -1, totalMb = -1, maxMb = -1, rateMb = -1;
        private long usedPercent = -1, totalPercent = -1;
        String memoryLine, rateLine, allocatedLine;

        void update(long used, long total, long max, long rate) {
            long usedMb = bytesToMegabytes(used);
            long totalMb = bytesToMegabytes(total);
            long maxMb = bytesToMegabytes(max);
            long rateMb = bytesToMegabytes(rate);
            long usedPercent = used * 100L / max;
            long totalPercent = total * 100L / max;
            if (usedMb != this.usedMb || maxMb != this.maxMb || usedPercent != this.usedPercent) {
                this.memoryLine = "Mem: " + memoryPercent(usedPercent) + "% " + padded(usedMb, 3) + "/" + padded(maxMb, 3) + "MB";
            }
            if (totalMb != this.totalMb || totalPercent != this.totalPercent) {
                this.allocatedLine = "Allocated: " + memoryPercent(totalPercent) + "% " + padded(totalMb, 3) + "MB";
            }
            if (rateMb != this.rateMb) this.rateLine = "Allocation rate: " + padded(rateMb, 3) + "MB /s";
            this.usedMb = usedMb;
            this.totalMb = totalMb;
            this.maxMb = maxMb;
            this.rateMb = rateMb;
            this.usedPercent = usedPercent;
            this.totalPercent = totalPercent;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class AllocationRateCalculator {
        private static final int UPDATE_INTERVAL_MS = 500;
        private static final List<GarbageCollectorMXBean> GC_MBEANS = ManagementFactory.getGarbageCollectorMXBeans();
        private long lastTime = 0L;
        private long lastHeapUsage = -1L;
        private long lastGcCounts = -1L;
        private long lastRate = 0L;

        long bytesAllocatedPerSecond(long p_232517_) {
            long i = System.currentTimeMillis();
            if (i - this.lastTime < 500L) {
                return this.lastRate;
            } else {
                long j = gcCounts();
                if (this.lastTime != 0L && j == this.lastGcCounts) {
                    double d0 = (double) TimeUnit.SECONDS.toMillis(1L) / (double) (i - this.lastTime);
                    long k = p_232517_ - this.lastHeapUsage;
                    this.lastRate = Math.round((double) k * d0);
                }

                this.lastTime = i;
                this.lastHeapUsage = p_232517_;
                this.lastGcCounts = j;
                return this.lastRate;
            }
        }

        private static long gcCounts() {
            long i = 0L;

            for (GarbageCollectorMXBean garbagecollectormxbean : GC_MBEANS) {
                i += garbagecollectormxbean.getCollectionCount();
            }

            return i;
        }
    }
}
