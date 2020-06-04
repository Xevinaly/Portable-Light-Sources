package com.github.xevinaly.portablelightsources.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;

public class AirLightSource extends Block {
    public static final IntegerProperty LIGHTVALUE = IntegerProperty.create("light_value", 0, 15);

    public AirLightSource(Block.Properties properties) {
        super(properties);
        this.setDefaultState(this.stateContainer.getBaseState().with(LIGHTVALUE, Integer.valueOf(0)));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        return VoxelShapes.empty();
    }

    public int getLightValue(BlockState state) {
        return state.get(LIGHTVALUE);
    }

    public BlockState getStateWithLightValue(int lightValue) {
        return this.getDefaultState().with(LIGHTVALUE, Integer.valueOf(lightValue));
    }

    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(LIGHTVALUE);
    }

    @Override
    public boolean isAir(BlockState state) {
        return true;
    }
}