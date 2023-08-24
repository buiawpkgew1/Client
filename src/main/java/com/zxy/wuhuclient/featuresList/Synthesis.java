package com.zxy.wuhuclient.featuresList;


import com.zxy.wuhuclient.config.Configs;
import fi.dy.masa.itemscroller.recipes.RecipePattern;
import fi.dy.masa.itemscroller.recipes.RecipeStorage;
import fi.dy.masa.malilib.util.InventoryUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.zxy.wuhuclient.Utils.ScreenManagement.closeScreen;
import static net.minecraft.block.ShulkerBoxBlock.FACING;


public class Synthesis {
    public static boolean isLoadMod = FabricLoader.getInstance().isModLoaded("itemscroller");
    public static MinecraftClient client = MinecraftClient.getInstance();
    public static RecipePattern recipe = null;
    //1 丢出容器物品 2 合成 3 装箱
    public static int step = 0;
    //记录合成点击的方块位置
    public static BlockPos pos;
    public static boolean runIng = false;

    public static void tick() {
        if(!Configs.SYNTHESIS.getBooleanValue()) return;
        if (Synthesis.pos != null && step == 2) {
            synthesis();
        }
        if (storagePos != null && Configs.AUTO_STORAGE.getBooleanValue()) {
            autoStorage();
        }
    }

    public static void onInventory() {
        if (step == 1) dropInventory();
        if (step == 3) Synthesis.storage();
    }

    public static boolean isInventory(BlockPos pos) {
//        if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
//            BlockPos pos = ((BlockHitResult) client.crosshairTarget).getBlockPos();
        if (client.world == null) return false;
        BlockState blockState = client.world.getBlockState(pos);
        BlockEntity blockEntity = client.world.getBlockEntity(pos);
        try {
            if (((BlockWithEntity) blockState.getBlock()).createScreenHandlerFactory(blockState, client.world, pos) == null ||
                    (blockEntity instanceof ShulkerBoxBlockEntity entity &&
                            !client.world.isSpaceEmpty(ShulkerEntity.calculateBoundingBox(blockState.get(FACING), 0.0f, 0.5f).offset(pos).contract(1.0E-6)) &&
                            entity.getAnimationStage() == ShulkerBoxBlockEntity.AnimationStage.CLOSED)) {
                client.inGameHud.setOverlayMessage(new TranslatableText("八嘎，目标容器无法打开"), false);
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
//        } else {
//            return false;
//        }
    }

    public static void start(BlockPos pos) {
        if (!updateRecipe()) {
            client.inGameHud.setOverlayMessage(new TranslatableText("当前快捷合成配方为空"), false);
            return;
        }
//        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
//            BlockPos pos = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
        if (client.world != null) {
            BlockState blockState = client.world.getBlockState(pos);
            if (blockState.isOf(Blocks.CRAFTING_TABLE)) {
                //工作台合成
                step = 2;
                Synthesis.pos = pos;
                closeScreen = 1;

//                    mc.interactionManager.interactBlock(mc.player, mc.world, Hand.MAIN_HAND, (BlockHitResult) mc.crosshairTarget);
                client.interactionManager.interactBlock(client.player, client.world, Hand.MAIN_HAND,
                        new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos, false));
                return;
            }
            if (!isInventory(pos)) {
                //背包合成
                if (recipe.getRecipeItems().length == 4) {
                    step = 2;
                    Synthesis.pos = pos;
                }
                return;
            }
            step = 1;
            closeScreen = 1;
//                client.interactionManager.interactBlock(client.player, client.world, Hand.MAIN_HAND, (BlockHitResult) client.crosshairTarget);
            client.interactionManager.interactBlock(client.player, client.world, Hand.MAIN_HAND,
                    new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos, false));
        }
//        }
    }

    public synchronized static void synthesis() {
        if (!updateRecipe()) return;
        ClientPlayerEntity player = client.player;
        ScreenHandler sc = player.currentScreenHandler;
        client.inGameHud.setOverlayMessage(Text.of("合成中..."), false);
        //检查是否满足合成条件
        ItemStack[] recipeItems = recipe.getRecipeItems();
        if ((recipeItems.length == 9 && !(sc instanceof CraftingScreenHandler)) || (recipeItems.length == 4 && !(sc instanceof PlayerScreenHandler))) {
            if (recipeItems.length == 9 && sc instanceof PlayerScreenHandler && closeScreen != 1) {
                closeScreen = 1;
                client.interactionManager.interactBlock(client.player, client.world, Hand.MAIN_HAND,
                        new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos, false));
            }
            return;
        }


        Map<Item, Integer> recipeMap = new HashMap<>();
        Map<Item, Integer> must = new HashMap<>();
        for (ItemStack recipeItem : recipeItems) {
            if (recipeItem.isEmpty()) continue;
            Item item = recipeItem.getItem();
            if (must.containsKey(item)) {
                must.put(item, must.get(item) + recipeItem.getCount());
            } else {
                must.put(item, recipeItem.getCount());
            }
            recipeMap.put(item, 0);
        }
        for (Slot slot : client.player.currentScreenHandler.slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || stack.hasNbt()) continue;
            recipeMap.forEach((k, v) -> {
                int num = stack.getMaxCount() == 1 ? 0 : 1;
                if (stack.getItem().equals(k)) recipeMap.put(k, v + (stack.getCount() - num));
            });
        }
        AtomicReference<Boolean> result = new AtomicReference<>(true);
        recipeMap.forEach((k, v) -> {
//            System.out.println(k + "\t" + v);
//            System.out.println(must.get(k));
            if (v < must.get(k)) {
                result.set(false);
                return;
            }

        });

        if (!result.get()) {
            return;
        }

//        System.out.println("合成");
//        if(runIng)return;
//        new Thread(() ->{
//            runIng = true;
        int recipeLength = recipeItems.length;
        for (int i = recipeLength + 1; i < sc.slots.size(); i++) {
            ItemStack stack = sc.slots.get(i).getStack().copy();
            if (stack.isEmpty() || (stack.getMaxCount() != 1 && stack.getCount() == 1)) continue;

            int index = -999;
            //查当前槽的物品是否为合成所需,且缺少的该物品
            for (int i1 = 1; i1 <= recipeLength; i1++) {
                ItemStack stack1 = sc.slots.get(i1).getStack();
                if (!InventoryUtils.areStacksEqual(recipeItems[i1 - 1], stack)) continue;
                if (stack1.getCount() == stack1.getMaxCount()) continue;
                index = i1;
                break;
            }
            if (index == -999) continue;
            if (!sc.getCursorStack().isEmpty()) {
                client.interactionManager.clickSlot(sc.syncId, -999, 0, SlotActionType.PICKUP, player);
            }
            client.interactionManager.clickSlot(sc.syncId, i, 0, SlotActionType.PICKUP, player);
            client.interactionManager.clickSlot(sc.syncId, i, 1, SlotActionType.PICKUP, player);
            client.interactionManager.clickSlot(sc.syncId, index, 0, SlotActionType.PICKUP, player);


            //处理跟随鼠标物品
            if (!sc.getCursorStack().isEmpty()) {
                for (int i2 = 0; i2 < recipeLength; i2++) {
                    if (InventoryUtils.areStacksEqual(recipeItems[i2], sc.getCursorStack())
                            && sc.slots.get(i2 + 1).getStack().getCount() < recipeItems[i2].getMaxCount()) {
                        client.interactionManager.clickSlot(sc.syncId, i2 + 1, 0, SlotActionType.PICKUP, player);
                    }
                    if (sc.getCursorStack().isEmpty()) break;
                }
                for (int i2 = recipeLength + 1; i2 < sc.slots.size() && !sc.getCursorStack().isEmpty(); i2++) {
                    if (InventoryUtils.areStacksEqual(sc.slots.get(i2).getStack(), sc.getCursorStack())
                            && sc.slots.get(i2).getStack().getCount() < sc.slots.get(i2).getStack().getMaxCount()) {
                        client.interactionManager.clickSlot(sc.syncId, i2, 0, SlotActionType.PICKUP, player);
                    }
                    if (sc.getCursorStack().isEmpty()) break;
                }
                if (!sc.getCursorStack().isEmpty()) {
                    client.interactionManager.clickSlot(sc.syncId, -999, 0, SlotActionType.PICKUP, player);
                }
            }
            long count1 = 0;
            //均分
//            for (int i1 = 9; i1 < sc.slots.size(); i1++) {
//                ItemStack stack1 = sc.slots.get(i).getStack().copy();
//                if(InventoryUtils.areStacksEqual(stack1, stack)
//                        || (stack1.getCount() > 1 || stack1.getMaxCount() == 1)){
//                    count1++;
//                }
//            }

            count1 = sc.slots.stream()
                    .skip(recipeLength + 1)
                    .filter(slot -> InventoryUtils.areStacksEqual(slot.getStack(), stack)
                            && slot.getStack().getCount() - 1 > 1)
                    .count();
            if (count1 == 0
            ) {
//                for (int i2 = 0; i2 < recipeLength; i2++) {
//                    System.out.println(sc.slots.get(i2 + 1).getStack());
//                }
//                System.out.println("==================1");
//                检测背包是否有满足的物品 如果没有则将物品均分
                int itemCount = 0;
                for (int i2 = 0; i2 < recipeLength; i2++) {
                    if (InventoryUtils.areStacksEqual(sc.slots.get(i2 + 1).getStack(), stack) && stack.getMaxCount() > 1)
                        itemCount += sc.slots.get(i2 + 1).getStack().getCount();
                }
                int average = itemCount / must.get(stack.getItem());
                for (int i2 = 0; i2 < recipeLength; i2++) {
                    if (!InventoryUtils.areStacksEqual(sc.slots.get(i2 + 1).getStack(), stack)) continue;
                    if (sc.getCursorStack().isEmpty() && sc.slots.get(i2 + 1).getStack().getCount() > average) {
                        client.interactionManager.clickSlot(sc.syncId, i2 + 1, 0, SlotActionType.PICKUP, player);
                        b1:
                        for (int i3 = 0; i3 < recipeLength; i3++) {
                            if (!InventoryUtils.areStacksEqual(recipeItems[i3], stack) || stack.getMaxCount() == 1)
                                continue;
                            while (sc.slots.get(i3 + 1).getStack().getCount() < average) {
                                client.interactionManager.clickSlot(sc.syncId, i3 + 1, 1, SlotActionType.PICKUP, player);
                                if (sc.getCursorStack().isEmpty()) break b1;
                            }
                        }
                    }
                }
                if (!sc.getCursorStack().isEmpty()) {
                    client.interactionManager.clickSlot(sc.syncId, i, 0, SlotActionType.PICKUP, player);
                    client.interactionManager.clickSlot(sc.syncId, -999, 0, SlotActionType.PICKUP, player);
                }
                for (int j = 0; j < average; j++) {
                    client.interactionManager.clickSlot(sc.syncId, 0, 1, SlotActionType.THROW, player);
//                    client.interactionManager.clickSlot(sc.syncId, 0, 0, SlotActionType.PICKUP, player);
//                    client.interactionManager.clickSlot(sc.syncId, -999, 0, SlotActionType.PICKUP, player);
                }
                return;
            }
            int rec = 0;
            for (int i2 = 0; InventoryUtils.areStacksEqual(sc.slots.get(0).getStack(), recipe.getResult()) && i2 < 64; i2++) {
                client.interactionManager.clickSlot(sc.syncId, 0, 1, SlotActionType.THROW, player);
                rec++;
//                client.interactionManager.clickSlot(sc.syncId, 0, 0, SlotActionType.PICKUP, player);
//                client.interactionManager.clickSlot(sc.syncId, -999, 0, SlotActionType.PICKUP, player);
            }
            if (rec > 0) return;
//            client.interactionManager.clickSlot(sc.syncId, -999, 0, SlotActionType.PICKUP, player);
        }
    }

    public static boolean isRequired() {
        ItemStack[] recipeItems = recipe.getRecipeItems();
        Map<Item, Integer> recipeMap = new HashMap<>();
        Map<Item, Integer> must = new HashMap<>();
        for (ItemStack recipeItem : recipeItems) {
            if (recipeItem.isEmpty()) continue;
            Item item = recipeItem.getItem();
            if (must.containsKey(item)) {
                must.put(item, must.get(item) + recipeItem.getCount());
            } else {
                must.put(item, recipeItem.getCount());
            }
            recipeMap.put(item, 0);
        }
        for (Slot slot : client.player.currentScreenHandler.slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || stack.hasNbt()) continue;
            recipeMap.forEach((k, v) -> {
                int num = stack.getMaxCount() == 1 ? 0 : 1;
                if (stack.getItem().equals(k)) recipeMap.put(k, v + (stack.getCount() - num));
            });
        }
        AtomicReference<Boolean> result = new AtomicReference<>(true);
        recipeMap.forEach((k, v) -> {
//            System.out.println(k + "\t" + v);
//            System.out.println(must.get(k));
            if (v < must.get(k)) {
                result.set(false);
                return;
            }

        });
        return result.get();
    }

    public static void dropItem(ItemStack itemStack, boolean isPlayerInventory) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        ScreenHandler sc = player.currentScreenHandler;
        int size = 0;
        if (isPlayerInventory && sc.equals(client.player.playerScreenHandler)) {
            size = sc.slots.size();
        } else if (!isPlayerInventory && !sc.equals(player.playerScreenHandler)) {
            size = sc.slots.get(0).inventory.size();
        }
        if (size == 0) return;
        for (int i = 0; i < size; i++) {
            if (InventoryUtils.areStacksEqual(sc.slots.get(i).getStack(), itemStack)) {
                client.interactionManager.clickSlot(sc.syncId, i, 1, SlotActionType.THROW, player);
            }
        }
    }

    public static void dropInventory() {
        step = 0;
        if (!updateRecipe()) return;
        client = MinecraftClient.getInstance();
        ClientPlayerEntity player;
        if (client.player == null) return;
        player = client.player;
        if (player.currentScreenHandler.equals(player.playerScreenHandler)) return;
        for (ItemStack recipeItem : recipe.getRecipeItems()) {
            dropItem(recipeItem, false);
        }
        client.player.closeHandledScreen();
        continueSynthesis();
    }

    public static BlockPos storagePos = null;

    public static void autoStorage() {
        if (client.player == null) return;
        if (!updateRecipe()) return;
        ClientPlayerEntity player = client.player;
        DefaultedList<Slot> slots = player.currentScreenHandler.slots;
        if (storagePos != null && storagePos.isWithinDistance(player.getPos(), 5) && step != 3) {
            if (slots.stream()
                    .anyMatch(slot -> InventoryUtils.areStacksEqual(slot.getStack(), recipe.getResult()) && slot.getStack().getCount() > 1))
            {
                if(step == 2)closeScreen = 3;
                else closeScreen = 1;
                step = 3;
                client.interactionManager.interactBlock(client.player, client.world, Hand.MAIN_HAND,
                        new BlockHitResult(new Vec3d(storagePos.getX() + 0.5, storagePos.getY() + 0.5, storagePos.getZ() + 0.5), Direction.UP, storagePos, false));
            }
        }
    }
    public static void storage() {
        step = 0;
        if (!updateRecipe()) return;
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        ScreenHandler sc = player.currentScreenHandler;
        DefaultedList<Slot> slots = sc.slots;
        if (slots.stream()
                .limit(slots.get(0).inventory.size())
                .allMatch(slot -> InventoryUtils.areStacksEqual(slot.getStack(), recipe.getResult())
                && slot.getStack().getCount() >= slot.getStack().getMaxCount())) {
            client.inGameHud.setOverlayMessage(Text.of("合成助手: 该容器已满"), false);
            player.closeHandledScreen();
            continueSynthesis();
            return;
        }
        //从玩家背包寻找合成物
        for (int i = slots.get(0).inventory.size(); i < slots.size(); i++) {
            ItemStack stack = slots.get(i).getStack();
            if (InventoryUtils.areStacksEqual(stack, recipe.getResult()) && stack.getCount() > 1) {
                client.interactionManager.clickSlot(sc.syncId, i, 0, SlotActionType.PICKUP, player);
                client.interactionManager.clickSlot(sc.syncId, i, 1, SlotActionType.PICKUP, player);
                //检测容器中能放下的空位
                for (int i2 = 0; i2 < slots.get(0).inventory.size(); i2++) {
                    if ((InventoryUtils.areStacksEqual(slots.get(i2).getStack(), recipe.getResult())
                            && slots.get(i2).getStack().getCount() <= slots.get(i2).getStack().getMaxCount())
                            || slots.get(i2).getStack().isEmpty()
                    ) client.interactionManager.clickSlot(sc.syncId, i2, 0, SlotActionType.PICKUP, player);
                }
                if(!sc.getCursorStack().isEmpty()){
                    client.interactionManager.clickSlot(sc.syncId, i, 0, SlotActionType.PICKUP, player);
                    client.interactionManager.clickSlot(sc.syncId, -999, 0, SlotActionType.PICKUP, player);
                }
            }
        }
        player.closeHandledScreen();
        continueSynthesis();
    }

    private static boolean updateRecipe() {
        recipe = RecipeStorage.getInstance().getSelectedRecipe();
        return !recipe.getResult().isEmpty();
    }

    private static boolean continueSynthesis() {
        if (pos != null) {
            step = 2;
            return true;
        } else return false;
    }
}
