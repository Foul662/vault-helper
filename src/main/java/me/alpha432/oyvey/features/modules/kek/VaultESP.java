package me.alpha432.oyvey.features.modules.kek;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class VaultESP extends Module {
    public Setting<Integer> horizontalRange = register(new Setting<>("HorizontalRange", 64, 16, 128));
    public Setting<Integer> verticalRange = register(new Setting<>("VerticalRange", 32, 8, 128));
    public Setting<Float> lineWidth = register(new Setting<>("LineWidth", 1.5f, 0.1f, 5.0f));
    public Setting<Integer> updateDelay = register(new Setting<>("UpdateDelay", 20, 1, 100));

    private final Color RED_COLOR = new Color(255, 0, 0, 255);
    private final Set<BlockPos> cachedVaults = new HashSet<>();
    private int ticks = 0;

    public VaultESP() {
        super("VaultESP", "kek", Category.KEK, true, false, false);
    }

    @Override
    public void onUpdate() {
        if (mc.world == null || mc.player == null) return;

        ticks++;
        if (ticks >= updateDelay.getValue()) {
            ticks = 0;
            updateVaultCache();
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int hRange = horizontalRange.getValue();
        int vRange = verticalRange.getValue();

        for (BlockPos pos : cachedVaults) {
            int dx = Math.abs(pos.getX() - playerPos.getX());
            int dz = Math.abs(pos.getZ() - playerPos.getZ());
            int dy = Math.abs(pos.getY() - playerPos.getY());

            if (dx <= hRange && dz <= hRange && dy <= vRange) {
                Box box = new Box(pos);
                RenderUtil.drawBox(event.getMatrix(), box, RED_COLOR, lineWidth.getValue());


            }
        }
    }

    private void updateVaultCache() {
        cachedVaults.clear();
        if (mc.world == null || mc.player == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        World world = mc.world;

        int hRange = horizontalRange.getValue();
        int vRange = verticalRange.getValue();

        int minX = playerPos.getX() - hRange;
        int maxX = playerPos.getX() + hRange;
        int minZ = playerPos.getZ() - hRange;
        int maxZ = playerPos.getZ() + hRange;

        int minY = playerPos.getY() - vRange;
        int maxY = playerPos.getY() + vRange;

        minY = Math.max(minY, world.getBottomY());


        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
                if (!world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                    continue;
                }

                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    if (isOminousVault(state)) {
                        cachedVaults.add(pos.toImmutable());
                    }
                }
            }
        }
    }

    private boolean isOminousVault(BlockState state) {
        if (state.getBlock() != Blocks.VAULT) {
            return false;
        }

        try {
            if (state.contains(Properties.OMINOUS)) {
                return state.get(Properties.OMINOUS);
            }

            return state.toString().toLowerCase().contains("ominous=true");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onEnable() {
        cachedVaults.clear();
        ticks = 0;
        updateVaultCache();
    }

    @Override
    public void onDisable() {
        cachedVaults.clear();
    }
}