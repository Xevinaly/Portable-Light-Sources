package com.github.xevinaly.portablelightsources.blocks;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;

public class AirLightSource extends LightSource {
    public AirLightSource(Properties properties) {
        super(properties);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public boolean isAir(BlockState state) {
        return true;
    }
}
