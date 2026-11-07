package com.awesomehippo.chainedupdatesfix.mixin;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Unique
    private static final int MAX_DEPTH = 128;
    // 1.19 default of max-chained-neighbor-updates but might make it configurable
    @Unique
    private static final int MAX_UPDATES = 1000000;
    @Unique
    private final ArrayDeque<Runnable> chainedupdatesfix$stack = new ArrayDeque<>();
    @Unique
    private final List<Runnable> chainedupdatesfix$added = new ArrayList<>();
    @Unique
    private int chainedupdatesfix$depth;
    @Unique
    private int chainedupdatesfix$count;
    @Unique
    private boolean chainedupdatesfix$running;

    @Shadow
    public abstract void neighborChanged(BlockPos pos, Block block, BlockPos fromPos);

    @Redirect(method = "neighborChanged(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;neighborChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V"))
    private void chainedupdatesfix$neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        if (chainedupdatesfix$depth >= MAX_DEPTH) {
            if (chainedupdatesfix$count < MAX_UPDATES) {
                BlockPos at = pos.immutable();
                BlockPos from = fromPos.immutable();
                chainedupdatesfix$added.add(() -> neighborChanged(at, block, from));
            } else if (chainedupdatesfix$count == MAX_UPDATES) {
                // took the 1.19 message:p
                LogUtils.getLogger().error("Too many chained neighbor updates. Skipping the rest. First skipped position: {}", pos.toShortString());
            }
            chainedupdatesfix$count++;
            return;
        }
        chainedupdatesfix$depth++;
        try {
            state.neighborChanged(level, pos, block, fromPos, moving);
        } finally {
            chainedupdatesfix$depth--;
        }
        if (chainedupdatesfix$depth > 0 || chainedupdatesfix$running || chainedupdatesfix$added.isEmpty()) {
            return;
        }
        chainedupdatesfix$running = true;
        try {
            while (!chainedupdatesfix$added.isEmpty() || !chainedupdatesfix$stack.isEmpty()) {
                for (int i = chainedupdatesfix$added.size() - 1; i >= 0; i--) {
                    chainedupdatesfix$stack.push(chainedupdatesfix$added.get(i));
                }
                chainedupdatesfix$added.clear();
                chainedupdatesfix$stack.pop().run();
            }
        } finally {
            chainedupdatesfix$stack.clear();
            chainedupdatesfix$added.clear();
            chainedupdatesfix$count = 0;
            chainedupdatesfix$running = false;
        }
    }
}
