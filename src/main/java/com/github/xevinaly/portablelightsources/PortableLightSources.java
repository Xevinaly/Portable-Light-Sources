package com.github.xevinaly.portablelightsources;

import com.github.xevinaly.portablelightsources.init.ModBlocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PortableLightSources.MODID)
@Mod.EventBusSubscriber(modid = PortableLightSources.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PortableLightSources {
    public static final Logger LOGGER = LogManager.getLogger();
    public static final String MODID = "portablelightsources";


    public PortableLightSources() {
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));

        IEventBus modEventBus =  FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
    }
}
