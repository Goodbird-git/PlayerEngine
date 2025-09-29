package com.player2.playerengine.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;

import java.util.Optional;

public class BlockPosUtils {
    public static Optional<BlockPos> readBlockPos(ValueInput tag, String key) {
        int[] aint = tag.getIntArray(key).get();
        return aint.length == 3 ? Optional.of(new BlockPos(aint[0], aint[1], aint[2])) : Optional.empty();
    }

    public static int[] writeBlockPos(BlockPos pos) {
        return new int[]{pos.getX(), pos.getY(), pos.getZ()};
    }
}
