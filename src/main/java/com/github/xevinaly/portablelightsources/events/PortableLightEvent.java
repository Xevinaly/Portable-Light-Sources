package com.github.xevinaly.portablelightsources.events;

import com.github.xevinaly.portablelightsources.util.DelayedCall;
import com.github.xevinaly.portablelightsources.PortableLightSources;
import com.github.xevinaly.portablelightsources.blocks.AirLightSource;
import com.github.xevinaly.portablelightsources.blocks.WaterLightSource;
import com.github.xevinaly.portablelightsources.init.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = com.github.xevinaly.portablelightsources.PortableLightSources.MODID, bus = Bus.FORGE)
public class PortableLightEvent {
    private static Map<Entity, LocationData> entityLocationData = new ConcurrentHashMap<>();
    private static final Map<Item, LightSource> itemLightSources = new HashMap<>();
    private static final List<Block> replaceableBlocks = new ArrayList<>();
    private static final List<Block> oneOverBlocks = new ArrayList<>();
    private static final List<Block> lightSourceBlocks = new ArrayList<>();

    @SubscribeEvent
    public static void parseTags(WorldEvent.Load event) {
        if (itemLightSources.size() == 0) {
            replaceableBlocks.addAll(BlockTags.getCollection().get(new ResourceLocation(PortableLightSources.MODID, "portable_light_replaceable")).getAllElements());
            oneOverBlocks.addAll(BlockTags.getCollection().get(new ResourceLocation(PortableLightSources.MODID, "portable_light_one_over")).getAllElements());
            lightSourceBlocks.addAll(BlockTags.getCollection().get(new ResourceLocation(PortableLightSources.MODID, "light_sources")).getAllElements());

            List<Item> airOnly = new ArrayList<>();
            List<Item> waterOnly = new ArrayList<>();
            waterOnly.addAll(ItemTags.getCollection().get(new ResourceLocation(PortableLightSources.MODID, "portable_light_water")).getAllElements());
            airOnly.addAll(ItemTags.getCollection().get(new ResourceLocation(PortableLightSources.MODID, "portable_light_air")).getAllElements());


            for (int lightValue = 1; lightValue <= 15; lightValue++) {
                try {
                    for (Item item : ItemTags.getCollection().get(new ResourceLocation(PortableLightSources.MODID, "portable_light_" + lightValue)).getAllElements()) {
                        Medium restrictedMedium = Medium.NONE;

                        if (airOnly.contains(item)) {
                            restrictedMedium = Medium.WATER;
                        }
                        if (waterOnly.contains(item)) {
                            restrictedMedium = Medium.AIR;
                        }

                        itemLightSources.put(item, new LightSource(lightValue, restrictedMedium));
                    }
                } catch (Exception exception) {
                    PortableLightSources.LOGGER.info("There are no portable light sources with a light value of " + lightValue);
                }
            }
        }
    }

    @SubscribeEvent
    public static void resetEntityLocationData(WorldEvent.Unload event) {
        for (Map.Entry<Entity, LocationData> entry : entityLocationData.entrySet()) {
            entry.getValue().reset();
        }

        entityLocationData = new ConcurrentHashMap<>();
    }

    @SubscribeEvent
    public static void registerEntity(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();

        entityLocationData.put(entity, new LocationData());
    }

    @SubscribeEvent
    public static void checkEntityLocationAndLightStatus(TickEvent.WorldTickEvent event) {
        World world = event.world;

        Iterator<Entity> entities = entityLocationData.keySet().iterator();

        while (entities.hasNext()) {
            Entity entity = entities.next();
            LocationData data = entityLocationData.get(entity);
            data.reset();

            if (entity.removed) {
                entities.remove();
            } else {
                int lightValue = greatestLightSourceOnEntity(entity, world);

                if (lightValue != 0) {
                    BlockPos blockToLight = getClosestReplaceableBlock(entity, world);
                    BlockState originalBlockState = world.getBlockState(blockToLight);
                    BlockState newBlockState = getLitBlockState(lightValue, originalBlockState);


                    if (lightSourceBlocks.contains(originalBlockState.getBlock())) {
                        data.add(blockToLight, () -> {});
                    } else {
                        data.add(blockToLight, () -> world.setBlockState(blockToLight, originalBlockState));
                    }

                    if (lightValue > world.getLightValue(blockToLight) || entity instanceof PlayerEntity) {
                        world.setBlockState(blockToLight, newBlockState);
                    }
                }
            }
        }
    }

    private static int greatestLightSourceOnEntity(Entity entity, World world) {
        int lightValue = 0;

        if (entity.getFireTimer() > 0) {
            lightValue = 15;
        }

        if (EntityTypeTags.getCollection().get(new ResourceLocation(PortableLightSources.MODID, "bright_entities")).contains(entity.getType())) {
            lightValue = 15;
        }

        if (entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entity;
            for (ItemStack itemStack : player.getHeldEquipment()) {
                if (itemLightSources.containsKey(itemStack.getItem())) {
                    LightSource item = itemLightSources.get(itemStack.getItem());
                    if (item.lightValue > lightValue && item.restrictedMedium != getMedium(world.getBlockState(entity.getPosition()).getBlock())) {
                        lightValue = item.lightValue;
                    }
                }
            }
        } else if (entity instanceof ItemEntity) {
            ItemEntity itemEntity = (ItemEntity) entity;
            if (itemLightSources.containsKey(itemEntity.getItem().getItem())) {
                LightSource item = itemLightSources.get(itemEntity.getItem().getItem());
                if (item.lightValue > lightValue && item.restrictedMedium != getMedium(world.getBlockState(entity.getPosition()).getBlock())) {
                    lightValue = item.lightValue;
                }
            }
        }

        return lightValue;
    }

    private static Medium getMedium(Block blockToReplace) {
        if (blockToReplace == Blocks.CAVE_AIR || blockToReplace == Blocks.AIR || blockToReplace == ModBlocks.AIR_LIGHT_SOURCE.get()) {
            return Medium.AIR;
        } else if (blockToReplace == Blocks.WATER) {
            return Medium.WATER;
        } else {
            return Medium.VOID;
        }
    }

    private static BlockPos getClosestReplaceableBlock(Entity entity, World world) {
        BlockPos position = entity.getPosition();

        if (entity.getHeight() > 1.2) {
            position = position.add(0, 1, 0);
        }

        Block blockToReplace = world.getBlockState(position).getBlock();

        if (oneOverBlocks.contains(blockToReplace)) {
            if (replaceableBlocks.contains(world.getBlockState(position.up()).getBlock())) {
                return position.up();
            } else if (replaceableBlocks.contains(world.getBlockState(position.down()).getBlock())) {
                return position.down();
            } else if (replaceableBlocks.contains(world.getBlockState(position.east()).getBlock())) {
                return position.east();
            } else if (replaceableBlocks.contains(world.getBlockState(position.west()).getBlock())) {
                return position.west();
            } else if (replaceableBlocks.contains(world.getBlockState(position.north()).getBlock())) {
                return position.north();
            } else if (replaceableBlocks.contains(world.getBlockState(position.south()).getBlock())) {
                return position.south();
            } else if (replaceableBlocks.contains(world.getBlockState(position.up().east()).getBlock())) {
                return position.up().east();
            } else if (replaceableBlocks.contains(world.getBlockState(position.up().west()).getBlock())) {
                return position.up().west();
            } else if (replaceableBlocks.contains(world.getBlockState(position.up().north()).getBlock())) {
                return position.up().north();
            } else if (replaceableBlocks.contains(world.getBlockState(position.up().south()).getBlock())) {
                return position.up().south();
            } else if (replaceableBlocks.contains(world.getBlockState(position.down().north()).getBlock())) {
                return position.down().north();
            } else if (replaceableBlocks.contains(world.getBlockState(position.down().east()).getBlock())) {
                return position.down().east();
            } else if (replaceableBlocks.contains(world.getBlockState(position.down().south()).getBlock())) {
                return position.down().south();
            } else if (replaceableBlocks.contains(world.getBlockState(position.down().west()).getBlock())) {
                return position.down().west();
            }
        }

        return position;
    }

    private static BlockState getLitBlockState(int lightValue, BlockState originalBlockState) {
        Block blockToReplace = originalBlockState.getBlock();

        if (blockToReplace == Blocks.CAVE_AIR || blockToReplace == Blocks.AIR || blockToReplace == ModBlocks.AIR_LIGHT_SOURCE.get()) {
            return ((AirLightSource) ModBlocks.AIR_LIGHT_SOURCE.get()).getStateWithLightValue(lightValue);
        } else if (blockToReplace == Blocks.WATER) {
            return ((WaterLightSource) ModBlocks.WATER_LIGHT_SOURCE.get()).getStateWithLightValue(lightValue);
        } else {
            return originalBlockState;
        }
    }

    private static class LightSource {
        int lightValue;
        Medium restrictedMedium;

        public LightSource(int lightValue, Medium restrictedMedium) {
            this.lightValue = lightValue;
            this.restrictedMedium = restrictedMedium;
        }
    }

    private enum Medium {
        AIR,
        WATER,
        VOID,
        NONE
    }

    private static class LocationData {
        private List<BlockData> litBlocks;

        public LocationData() {
            litBlocks = new ArrayList<>();
        }

        public void add(BlockPos position, DelayedCall resetMethod) {
            switch (getDuplicateLocation(position)) {
                case FIRST: swapElements(); break;
                case SECOND: break;
                default: add(new BlockData(position, resetMethod));
            }
        }

        private void swapElements() {
            if (litBlocks.size() > 1) {
                BlockData data = litBlocks.get(0);
                litBlocks.remove(0);
                litBlocks.add(data);
            }
        }

        private void add(BlockData data) {
            if (litBlocks.size() == 2) {
                removeFirstElement();
            }
            litBlocks.add(data);
        }

        private void removeFirstElement() {
            litBlocks.get(0).resetMethod.delayedCall();
            litBlocks.remove(0);
        }

        private DuplicateLocation getDuplicateLocation(BlockPos position) {
            if (litBlocks.size() == 2 && isDuplicate(position, litBlocks.get(0).position)) {
                return DuplicateLocation.FIRST;
            } else if (litBlocks.size() > 1 && isDuplicate(position, litBlocks.get(1).position)) {
                return DuplicateLocation.SECOND;
            } else {
                return DuplicateLocation.NEITHER;
            }
        }

        private boolean isDuplicate(BlockPos A, BlockPos B) {
            return A.getX() == B.getX() && A.getY() == B.getY() && A.getZ() == B.getZ();
        }

        public void reset() {
            for (BlockData data : litBlocks) {
                data.resetMethod.delayedCall();
            }
        }

        private class BlockData {
            BlockPos position;
            DelayedCall resetMethod;

            public BlockData(BlockPos position, DelayedCall resetMethod) {
                this.position = position;
                this.resetMethod = resetMethod;
            }
        }

        private enum DuplicateLocation {
            FIRST,
            SECOND,
            NEITHER
        }
    }
}