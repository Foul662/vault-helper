package me.alpha432.oyvey.features.modules.kek;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VaultHelper extends Module {
    public Setting<Boolean> autoClick = register(new Setting<>("AutoClick", true));
    public Setting<Boolean> highlight = register(new Setting<>("Highlight", true));
    public Setting<Boolean> debug = register(new Setting<>("Debug", true));
    public Setting<Double> maxDistance = register(new Setting<>("MaxDistance", 4.5, 1.0, 10.0));
    public Setting<Integer> clickInterval = register(new Setting<>("ClickInterval", 20, 0, 100));

    private final Set<BlockPos> cachedVaults = new HashSet<>();
    private final Map<BlockPos, Long> clickedVaults = new HashMap<>();
    private final Map<BlockPos, Long> lastClickTimes = new HashMap<>();
    private Item ominousKeyItem = null;
    private int ticks = 0;
    private int currentKeySlot = -1;

    public VaultHelper() {
        super("VaultHelper", "kek", Category.KEK, true, false, false);
    }

    @Override
    public void onUpdate() {
        if (mc.world == null || mc.player == null) return;

        ticks++;
        if (ticks >= 10) {
            ticks = 0;
            updateVaultCache();
        }

        if (autoClick.getValue()) {
            processAutoClick();
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (!highlight.getValue() || mc.world == null || mc.player == null) return;

        for (BlockPos pos : cachedVaults) {
            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
            if (distance <= 32) {
                Box box = new Box(pos);
                Color color = getVaultColor(pos);
                RenderUtil.drawBox(event.getMatrix(), box, color, 1.5f);
            }
        }
    }

    private void updateVaultCache() {
        cachedVaults.clear();
        if (mc.world == null || mc.player == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        World world = mc.world;

        int range = 12;

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);

                    if (!world.isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) {
                        continue;
                    }

                    BlockState state = world.getBlockState(checkPos);
                    if (isOminousVault(state)) {
                        cachedVaults.add(checkPos.toImmutable());
                    }
                }
            }
        }

        if (debug.getValue() && !cachedVaults.isEmpty()) {
            sendMessage("Найдено тайников: " + cachedVaults.size());
        }
    }

    private void processAutoClick() {
        if (mc.player == null || mc.interactionManager == null) return;

        long currentTime = System.currentTimeMillis();

        BlockPos targetVault = null;
        double minDistance = Double.MAX_VALUE;

        for (BlockPos pos : cachedVaults) {
            if (clickedVaults.containsKey(pos)) {
                long timeSinceClick = currentTime - clickedVaults.get(pos);
                if (timeSinceClick < 5000) {
                    continue;
                } else {
                    clickedVaults.remove(pos);
                }
            }

            Long lastClick = lastClickTimes.get(pos);
            if (lastClick != null) {
                long timeSinceLastClick = currentTime - lastClick;
                if (timeSinceLastClick < clickInterval.getValue() * 50L) {
                    continue;
                }
            }

            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
            if (distance <= maxDistance.getValue() && distance < minDistance) {
                minDistance = distance;
                targetVault = pos;
            }
        }

        if (targetVault != null) {
            if (ensureKeyInHand()) {
                if (clickVault(targetVault)) {
                    lastClickTimes.put(targetVault, currentTime);
                    clickedVaults.put(targetVault, currentTime);

                    if (debug.getValue()) {
                        sendMessage("Кликнул по тайнику на расстоянии: " + String.format("%.1f", minDistance));
                    }
                }
            }
        }
    }

    private boolean ensureKeyInHand() {
        if (mc.player == null) return false;

        if (ominousKeyItem == null) {
            findAndInitializeKey();
        }

        if (ominousKeyItem == null || ominousKeyItem == Items.AIR) {
            ominousKeyItem = findAnySuitableItem();
            if (ominousKeyItem == null) {
                if (debug.getValue()) {
                    sendMessage("Не найден подходящий предмет для использования");
                }
                return false;
            }
        }

        ItemStack mainHand = mc.player.getMainHandStack();
        if (!mainHand.isEmpty() && mainHand.getItem() == ominousKeyItem) {
            return true;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == ominousKeyItem) {
                mc.player.getInventory().setSelectedSlot(i);
                currentKeySlot = i;

                if (debug.getValue()) {
                    net.minecraft.util.Identifier itemId = net.minecraft.registry.Registries.ITEM.getId(ominousKeyItem);
                    sendMessage("Взял ключ в руку (слот " + i + "): " +
                            (itemId != null ? itemId.toString() : "unknown item"));
                }
                return true;
            }
        }

        if (debug.getValue()) {
            sendMessage("Ключ не найден в горячей панели");
        }

        return false;
    }

    private void findAndInitializeKey() {
        try {
            ominousKeyItem = net.minecraft.registry.Registries.ITEM.get(
                    net.minecraft.util.Identifier.of("minecraft", "ominous_key")
            );

            if (ominousKeyItem != null && ominousKeyItem != Items.AIR) {
                if (debug.getValue()) {
                    sendMessage("Найден зловещий ключ");
                }
                return;
            }

            for (Item item : net.minecraft.registry.Registries.ITEM) {
                net.minecraft.util.Identifier itemId = net.minecraft.registry.Registries.ITEM.getId(item);
                if (itemId != null) {
                    String path = itemId.getPath().toLowerCase();
                    if ((path.contains("ominous") && path.contains("key")) ||
                            path.contains("ominous_key") ||
                            path.contains("key_ominous")) {
                        ominousKeyItem = item;
                        if (debug.getValue()) {
                            sendMessage("Найден ключ по имени: " + itemId);
                        }
                        return;
                    }
                }
            }
        } catch (Exception e) {
            if (debug.getValue()) {
                sendMessage("Ошибка поиска ключа: " + e.getMessage());
            }
        }
    }

    private Item findAnySuitableItem() {
        Item[] backupItems = {
                Items.TRIPWIRE_HOOK,
                Items.GOLD_NUGGET,
                Items.IRON_NUGGET,
                Items.PRISMARINE_SHARD
        };

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                for (Item backupItem : backupItems) {
                    if (stack.getItem() == backupItem) {
                        if (debug.getValue()) {
                            sendMessage("Использую " + backupItem + " как ключ");
                        }
                        return backupItem;
                    }
                }
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                if (debug.getValue()) {
                    sendMessage("Использую первый предмет в слоте " + i + " как ключ");
                }
                return stack.getItem();
            }
        }

        return null;
    }

    private boolean clickVault(BlockPos pos) {
        if (mc.interactionManager == null || mc.player == null) return false;

        ItemStack mainHand = mc.player.getMainHandStack();
        if (mainHand.isEmpty()) {
            if (debug.getValue()) {
                sendMessage("Рука пустая, не могу кликнуть");
            }
            return false;
        }

        BlockHitResult hitResult = new BlockHitResult(
                Vec3d.ofCenter(pos),
                Direction.UP,
                pos,
                false
        );

        try {
            boolean success = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult).isAccepted();

            if (success) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }

            return success;
        } catch (Exception e) {
            if (debug.getValue()) {
                sendMessage("Ошибка при клике: " + e.getMessage());
            }
            return false;
        }
    }

    private boolean isOminousVault(BlockState state) {
        if (state.getBlock() != Blocks.VAULT) {
            return false;
        }

        try {
            return state.get(net.minecraft.state.property.Properties.OMINOUS);
        } catch (Exception e) {
            String stateString = state.toString().toLowerCase();
            return stateString.contains("ominous=true");
        }
    }

    private Color getVaultColor(BlockPos pos) {
        long currentTime = System.currentTimeMillis();

        if (clickedVaults.containsKey(pos)) {
            long timeSinceClick = currentTime - clickedVaults.get(pos);
            if (timeSinceClick < 5000) {
                return Color.YELLOW;
            }
        }

        double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
        if (distance <= maxDistance.getValue()) {
            return Color.GREEN;
        }

        return Color.RED;
    }

    private void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("§6[VaultMace] §f" + message), false);
        }
    }

    @Override
    public void onEnable() {
        cachedVaults.clear();
        clickedVaults.clear();
        lastClickTimes.clear();
        ticks = 0;
        currentKeySlot = -1;
        ominousKeyItem = null;
        updateVaultCache();

        sendMessage("Модуль включен. Автоматически ищет и кликает по тайникам.");
    }

    @Override
    public void onDisable() {
        cachedVaults.clear();
        clickedVaults.clear();
        lastClickTimes.clear();
        sendMessage("Модуль выключен");
    }
}