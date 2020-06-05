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
import net.minecraft.fluid.Fluids;
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

@EventBusSubscriber(modid = PortableLightSources.MODID, bus = Bus.FORGE)
public class PortableLightEvent {
    private static final Map<World, Map<Entity, LocationData>> worldEntityLocationData = new ConcurrentHashMap<>();
    private static final Map<Item, LightSource> itemLightSources = new HashMap<>();
    private static final List<BlockState> replaceableBlockStates = new ArrayList<>();
    private static final List<Block> lightSourceBlocks = new ArrayList<>();
    private static final List<EntityType<?>> brightEntities = new ArrayList<>();
    private static final List<EntityType<?>> untrackedEntities = new ArrayList<>();

    @SubscribeEvent
    public static void parseTags(WorldEvent.Load event) {
        if (itemLightSources.size() == 0) {
            replaceableBlockStates.add(Blocks.AIR.getDefaultState());
            replaceableBlockStates.add(Blocks.CAVE_AIR.getDefaultState());
            replaceableBlockStates.add(Blocks.VOID_AIR.getDefaultState());
            replaceableBlockStates.add(Blocks.WATER.getDefaultState());
            replaceableBlockStates.add(Fluids.WATER.getFlowingFluidState(8, false).getBlockState());
            replaceableBlockStates.add(Fluids.WATER.getFlowingFluidState(8, true).getBlockState());
            for (int i = 1; i <= 15; i++) {
                replaceableBlockStates.add(((AirLightSource) ModBlocks.AIR_LIGHT_SOURCE.get()).getStateWithLightValue(i));
                replaceableBlockStates.add(((WaterLightSource) ModBlocks.WATER_LIGHT_SOURCE.get()).getStateWithLightValue(i));
            }

            lightSourceBlocks.addAll(BlockTags.getCollection().get(new ResourceLocation(PortableLightSources.MODID, "light_sources")).getAllElements());
            brightEntities.addAll(EntityTypeTags.getCollection().get(new ResourceLocation(PortableLightSources.MODID, "bright_entities")).getAllElements());
            untrackedEntities.addAll(EntityTypeTags.getCollection().get(new ResourceLocation(PortableLightSources.MODID, "untracked_entities")).getAllElements());

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
        if (event.getWorld() instanceof  World) {
            World world = (World) event.getWorld();

            if (world != null && worldEntityLocationData.containsKey(world)) {
                for (Map.Entry<Entity, LocationData> entry : worldEntityLocationData.get(world).entrySet()) {
                    entry.getValue().reset();
                }

                worldEntityLocationData.replace(world, new ConcurrentHashMap<>());
            }
        }
    }

    @SubscribeEvent
    public static void registerEntity(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        World world = event.getWorld();

        if (!worldEntityLocationData.containsKey(world)) {
            worldEntityLocationData.put(world, new ConcurrentHashMap<>());
        }

        if (untrackedEntities.contains(entity.getType()) || (entity instanceof ItemEntity && !itemLightSources.containsKey(((ItemEntity) entity).getItem().getItem()))) {
            return;
        }

        worldEntityLocationData.get(world).put(entity, new LocationData());
    }

    @SubscribeEvent
    public static void checkEntityLocationAndLightStatus(TickEvent.WorldTickEvent event) {
        World world = event.world;
        Map<Entity, LocationData> entityLocationData = worldEntityLocationData.get(world);

        if (entityLocationData != null) {
            Iterator<Map.Entry<Entity, LocationData>> entrySet = entityLocationData.entrySet().iterator();

            while (entrySet.hasNext()) {
                Map.Entry<Entity, LocationData> entry = entrySet.next();
                Entity entity = entry.getKey();
                LocationData data = entry.getValue();

                if (entity.removed) {
                    data.reset();
                    entrySet.remove();
                } else {
                    int lightValue = greatestLightSourceOnEntity(entity, world);

                    if (lightValue != 0) {
                        BlockPos blockToLight = getClosestReplaceableBlock(entity, world);
                        BlockState originalBlockState = world.getBlockState(blockToLight);
                        BlockState newBlockState = getLitBlockState(lightValue, originalBlockState);


                        if (lightSourceBlocks.contains(originalBlockState.getBlock())) {
                            data.add(blockToLight, () -> {
                                if (newBlockState.getBlock() instanceof AirLightSource) {
                                    if (world.getBlockState(blockToLight.up()).getBlock() == Blocks.AIR) {
                                        world.setBlockState(blockToLight, Blocks.AIR.getDefaultState());
                                    } else {
                                        world.setBlockState(blockToLight, Blocks.CAVE_AIR.getDefaultState());
                                    }
                                } else {
                                    if (world.getBlockState(blockToLight.up()).isAir()) {
                                        world.setBlockState(blockToLight, Blocks.WATER.getDefaultState());
                                    } else {
                                        world.setBlockState(blockToLight, Fluids.WATER.getFlowingFluidState(8, false).getBlockState());
                                    }
                                }
                            });
                        } else {
                            data.add(blockToLight, () -> world.setBlockState(blockToLight, originalBlockState));
                        }

                        if (lightValue > world.getLightValue(blockToLight) || entity instanceof PlayerEntity) {
                            world.setBlockState(blockToLight, newBlockState);
                        }
                    } else {
                        data.reset();
                    }
                }
            }
        }
    }

    private static int greatestLightSourceOnEntity(Entity entity, World world) {
        int lightValue = 0;

        if (entity.getFireTimer() > 0) {
            return 15;
        }

        if (entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entity;
            Block blockToReplace = world.getBlockState(getClosestReplaceableBlock(entity, world)).getBlock();

            for (ItemStack itemStack : player.getHeldEquipment()) {
                if (itemLightSources.containsKey(itemStack.getItem())) {
                    LightSource item = itemLightSources.get(itemStack.getItem());
                    if (item.lightValue > lightValue && item.restrictedMedium != getMedium(blockToReplace)) {
                        lightValue = item.lightValue;
                    }
                }
            }
        } else if (entity instanceof ItemEntity) {
            ItemEntity itemEntity = (ItemEntity) entity;
            Block blockToReplace = world.getBlockState(getClosestReplaceableBlock(entity, world)).getBlock();

            if (itemLightSources.containsKey(itemEntity.getItem().getItem())) {
                LightSource item = itemLightSources.get(itemEntity.getItem().getItem());
                if (item.lightValue > lightValue && item.restrictedMedium != getMedium(blockToReplace)) {
                    lightValue = item.lightValue;
                }
            }
        } else if (brightEntities.contains(entity.getType())) {
            return 15;
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

        if (replaceableBlockStates.contains(world.getBlockState(position))) {
            return position;
        } else if (replaceableBlockStates.contains(world.getBlockState(position.up()))) {
            return position.up();
        } else if (replaceableBlockStates.contains(world.getBlockState(position.down()))) {
            return position.down();
        } else if (replaceableBlockStates.contains(world.getBlockState(position.east()))) {
            return position.east();
        } else if (replaceableBlockStates.contains(world.getBlockState(position.west()))) {
            return position.west();
        } else if (replaceableBlockStates.contains(world.getBlockState(position.north()))) {
            return position.north();
        } else if (replaceableBlockStates.contains(world.getBlockState(position.south()))) {
            return position.south();
        } else if (replaceableBlockStates.contains(world.getBlockState(position.up().east()))) {
            return position.up().east();
        } else if (replaceableBlockStates.contains(world.getBlockState(position.up().west()))) {
            return position.up().west();
        } else if (replaceableBlockStates.contains(world.getBlockState(position.up().north()))) {
            return position.up().north();
        } else if (replaceableBlockStates.contains(world.getBlockState(position.up().south()))) {
            return position.up().south();
        } else if (replaceableBlockStates.contains(world.getBlockState(position.down().north()))) {
            return position.down().north();
        } else if (replaceableBlockStates.contains(world.getBlockState(position.down().east()))) {
            return position.down().east();
        } else if (replaceableBlockStates.contains(world.getBlockState(position.down().south()))) {
            return position.down().south();
        } else if (replaceableBlockStates.contains(world.getBlockState(position.down().west()))) {
            return position.down().west();
        } else {
            return position;
        }
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
        private final List<BlockData> litBlocks;

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

        private static class BlockData {
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