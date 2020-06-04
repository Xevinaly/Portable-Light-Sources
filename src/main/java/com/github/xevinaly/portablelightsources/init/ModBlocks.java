package com.github.xevinaly.portablelightsources.init;

import com.github.xevinaly.portablelightsources.PortableLightSources;
import com.github.xevinaly.portablelightsources.blocks.AirLightSource;
import com.github.xevinaly.portablelightsources.blocks.WaterLightSource;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = new DeferredRegister<>(ForgeRegistries.BLOCKS, PortableLightSources.MODID);

    public static final RegistryObject<Block> AIR_LIGHT_SOURCE = BLOCKS.register("air_light_source",
            () -> new AirLightSource(Block.Properties.create(Material.AIR).doesNotBlockMovement().noDrops()));
    public static final RegistryObject<Block> WATER_LIGHT_SOURCE = BLOCKS.register("water_light_source",
            () -> new WaterLightSource(Block.Properties.create(Material.OCEAN_PLANT).doesNotBlockMovement().noDrops()));
}
