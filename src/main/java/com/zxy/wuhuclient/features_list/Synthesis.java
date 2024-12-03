package com.zxy.wuhuclient.features_list;


import com.zxy.wuhuclient.config.Configs;
import com.zxy.wuhuclient.mixin.CraftingScreenHandlerMixin;
import fi.dy.masa.itemscroller.recipes.RecipePattern;
import fi.dy.masa.itemscroller.recipes.RecipeStorage;
import fi.dy.masa.malilib.util.InventoryUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.recipe.CraftingRecipe;


import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;

import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

//#if MC < 12001
//$$ import net.minecraft.inventory.CraftingInventory;
//#elseif MC == 12001
//$$ import net.minecraft.inventory.RecipeInputInventory;
//#else
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.recipe.RecipeEntry;
//#endif

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.zxy.wuhuclient.Utils.InventoryUtils.isInventory;
import static com.zxy.wuhuclient.Utils.ScreenManagement.closeScreen;


public class Synthesis {
    public static boolean isLoadMod = FabricLoader.getInstance().isModLoaded("itemscroller");
    public static MinecraftClient client = MinecraftClient.getInstance();
    public static RecipePattern recipe = null;
    //1 丢出容器物品 2 合成 3 装箱
    public static int step = 0;
    //记录合成点击的方块位置
    public static BlockPos pos;
    //    public static Set<Integer> updatedSlot = new HashSet<>();
    public static boolean invUpdated = false;
    public static int tick = 0;
    public static void tick() {
//        tick++;
//        tick %= Integer.MAX_VALUE;
//        if(step != 0) System.out.println(step);
        if(!Configs.SYNTHESIS.getBooleanValue()) return;
        if(invUpdated){
            switch (step) {
                case 1 -> {
                    dropInventory();
                    step = 0;
                }
                case 3 -> {
                    storage();
                    step = 0;
                }
            }
        }
        if (storagePos != null && autoStorage && step != 1) {
            autoStorage();
            if(step == 3) return;
        }
        if(step == 0 || step == 2) continueSynthesis();
    }

    public static void onInventory() {
        if(!Configs.SYNTHESIS.getBooleanValue()) return;
        invUpdated = true;
    }

    public static void start(BlockPos pos) {
        tick = 0;
        if (!updateRecipe()) {
            client.inGameHud.setOverlayMessage(Text.of("当前快捷合成配方为空"), false);
            return;
        }
//        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
//            BlockPos pos = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
        if (client.world != null) {
            BlockState blockState = client.world.getBlockState(pos);
            if (blockState.isOf(Blocks.CRAFTING_TABLE)) {
                //工作台合成
//                step = 2;
                Synthesis.pos = pos;
                return;
            }
            if (!isInventory(pos)) {
                //背包合成
                if (recipe.getRecipeItems().length == 4) {
//                    step = 2;
                    Synthesis.pos = pos;
                }
                return;
            }
            if (client.player.isSneaking()) {
                client.inGameHud.setOverlayMessage(Text.of("合成取物已标记"), false);
                autoDrop = true;
            }else{
                autoDrop = false;
            }
            if(closeScreen != 0) return;
            dropPos = pos;
            dropStart();
        }
//        }
    }

    public static BlockPos dropPos;
    static boolean autoDrop;
    static void dropStart(){
        if(closeScreen != 0 || !isInventory(dropPos) || !updateRecipe()) return;
        step = 1;
        closeScreen = 1;
        invUpdated = false;
        if(!dropPos.isWithinDistance(client.player.getPos(),5)){
            dropPos = null;
            return;
        }
        client.player.networkHandler.sendPacket(new ClientCommandC2SPacket(client.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        useBlock(dropPos);
    }
    public static void useBlock(BlockPos pos){
        //#if MC > 11802
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos, false));
        //#else
        //$$ client.interactionManager.interactBlock(client.player,client.world, Hand.MAIN_HAND, new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos, false));
        //#endif
    }
    private static Map<Item, Integer> must = new HashMap<>();
    public static boolean isSynthesis(){
        if (!updateRecipe()) return false;
        ItemStack[] recipeItems = recipe.getRecipeItems();

        Map<Item, Integer> recipeMap = new HashMap<>();
        must = new HashMap<>();
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
            //#if MC > 12004

            if (stack.isEmpty() || stack.getComponents().isEmpty()) continue;
            //#else
            //$$ if (stack.isEmpty() || stack.hasNbt()) continue;
            //#endif
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
//                System.out.println("000");
                return;
            }
        });
//        if(!result.get() && dropPos != null && Configs.AUTO_DROP.getBooleanValue()) {
        if(!result.get() && dropPos != null && autoDrop && closeScreen <= 0) {
//            client.player.closeHandledScreen();
            dropStart();
        }
        return result.get();
    }
    public static void synthesis() {
//        step = 0;
        if (!updateRecipe()) return;
        ClientPlayerEntity player = client.player;
        ScreenHandler sc = player.currentScreenHandler;
        client.inGameHud.setOverlayMessage(Text.of("合成中..."), false);
        //检查是否满足合成条件
        if (!isSynthesis()) return;

        ItemStack[] recipeItems = recipe.getRecipeItems();
//        System.out.println("222");
//        if(runIng)return;
//        new Thread(() ->{
//            runIng = true;
        int recipeLength = recipeItems.length;
        for (int i = recipeLength + 1; i < sc.slots.size(); i++) {
            client.interactionManager.clickSlot(sc.syncId, -999, 0, SlotActionType.QUICK_CRAFT, client.player);

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

            int invCount = stack.getCount() - 1;
            int synCount = sc.slots.get(index).getStack().getCount();
            if(synCount>stack.getMaxCount()) {
                System.out.println("+++++++++++++++++++");
                System.out.println(synCount);
            }
            client.interactionManager.clickSlot(sc.syncId, i, 0, SlotActionType.PICKUP, player);
            client.interactionManager.clickSlot(sc.syncId, i, 1, SlotActionType.PICKUP, player);
            //改为拖动

            client.interactionManager.clickSlot(sc.syncId, index, 0, SlotActionType.PICKUP, player);

            //sc.getCursorStack()在一个游戏刻多次点击后获取的数量不靠谱，

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

            HashMap<String, String> stringStringHashMap = new HashMap<>();
            count1 = sc.slots.stream()
                    .skip(recipeLength + 1)
                    .filter(
                            slot -> InventoryUtils.areStacksEqual(slot.getStack(), stack)
                                    && slot.getStack().getCount() - 1 > 1)

                    .count();
            if (count1 == 0
            ) {
                System.out.println("average");
//                for (int i2 = 0; i2 < recipeLength; i2++) {
//                    System.out.println(sc.slots.get(i2 + 1).getStack());
//                }
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
                            for (int i4 = 0 ; sc.slots.get(i3 + 1).getStack().getCount() < average && i4 < 64 ;i4++) {
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
                }
                return;
            }
            int rec = 0;
            for (int i2 = 0; InventoryUtils.areStacksEqual(sc.slots.get(0).getStack(), recipe.getResult()) && i2 < 64; i2++) {
                client.interactionManager.clickSlot(sc.syncId, 0, 1, SlotActionType.THROW, player);
                rec++;
            }
            if (rec > 0) return;
        }
    }

    public static boolean satisfyCraft(){
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        ScreenHandler sc = player.currentScreenHandler;

        if(sc instanceof CraftingScreenHandler sc1
        ){
            ItemStack stack = ItemStack.EMPTY;
            //#if MC <= 12001
                //#if MC == 12001
                //$$ RecipeInputInventory rec = ((CraftingScreenHandlerMixin)sc1).getInput();
                //#else
                //$$ CraftingInventory rec = ((CraftingScreenHandlerMixin)sc1).getInput();
                //#endif
            //$$ Optional<CraftingRecipe> optional = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, rec, world);
            //$$ CraftingRecipe recipe = optional.isPresent() ? optional.get() : null;
            //$$ CraftingRecipe recipeEntry = optional.isPresent() ? optional.get() : null;
            //#else
            RecipeInputInventory rec = ((CraftingScreenHandlerMixin)sc1).getInput();
            //#if MC < 12100
            //$$ Optional<RecipeEntry<CraftingRecipe>> optional = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, rec, world);
            //#else
            Optional<RecipeEntry<CraftingRecipe>> optional = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, rec.createRecipeInput(), world);
            //#endif
            CraftingRecipe recipe = optional.map(RecipeEntry::value).orElse(null);
            RecipeEntry<?> recipeEntry = optional.orElse(null);
            //#endif

            if (recipe != null)
            {
                if ((recipe.isIgnoredInRecipeBook() ||
                        world.getGameRules().getBoolean(GameRules.DO_LIMITED_CRAFTING) == false ||
                        ((ClientPlayerEntity) player).getRecipeBook().contains(recipeEntry)))
                {
                    //#if MC > 11802
                        //#if MC >= 12100
                        stack = recipe.craft(rec.createRecipeInput(), MinecraftClient.getInstance().getNetworkHandler().getRegistryManager());
                        //#else
                        //$$ stack = recipe.craft(rec, MinecraftClient.getInstance().getNetworkHandler().getRegistryManager());
                        //#endif
                    //#else
                    //$$ stack = recipe.craft(rec);
                    //#endif
                }
                return !stack.isEmpty() && stack.getItem().equals(Synthesis.recipe.getResult().getItem());

            }
        }
        return false;
    }

    public static void synthesis2(){
        client.inGameHud.setOverlayMessage(Text.of("合成中..."), false);
        if(!pos.isWithinDistance(client.player.getPos(),5)){
            client.inGameHud.setOverlayMessage(Text.of("工作台或标记的方块超出范围，已重置。请再次点击开始合成"), false);
            pos = null;
            return;
        }
        if(!isSynthesis()) return;

        ClientPlayerEntity player = client.player;
        ScreenHandler sc = player.currentScreenHandler;
        if (sc.equals(player.playerScreenHandler)) return;

        ItemStack[] recipeItems = recipe.getRecipeItems();
//        int[] playerInv = new int[sc.slots.size()];

        int[] recInv = new int[recipeItems.length];
//        for (int i = 0; i < recInv.length; i++) {
//            recInv[i] = sc.slots.get(i+1).getStack().getCount();
//        }

        for (int i = recipeItems.length+1; i < sc.slots.size(); i++) {
            ItemStack stack = sc.slots.get(i).getStack().copy();
            if (stack.isEmpty() || (stack.getMaxCount() != 1 && stack.getCount() == 1)) continue;
            if (Arrays.stream(recipeItems).noneMatch(rec -> InventoryUtils.areStacksEqual(rec,stack))) continue;

            int stackCount = stack.getCount()-1;
            int cursorStackCount = stack.getCount()-1;
            client.interactionManager.clickSlot(sc.syncId, -999, 0, SlotActionType.PICKUP, player);
            client.interactionManager.clickSlot(sc.syncId, i, 0, SlotActionType.PICKUP, player);
            client.interactionManager.clickSlot(sc.syncId, i, 1, SlotActionType.PICKUP, player);
            client.interactionManager.clickSlot(sc.syncId, -999, 0, SlotActionType.QUICK_CRAFT, client.player);

            int skip = 0;
            int craft = 0;
            ArrayList<Integer> numArr = new ArrayList<>();
            for (int i1 = 1; i1 <= recipeItems.length; i1++) {
                ItemStack stack1 = sc.slots.get(i1).getStack();
                if (craft >= stackCount) break;
                if(!InventoryUtils.areStacksEqual(stack,recipeItems[i1-1])) continue;

                if (recInv[i1-1] >= stack.getMaxCount()){
                    skip++;
                    continue;
                }
                numArr.add(i1-1);
                craft++;
                client.interactionManager.clickSlot(sc.syncId, i1, 1, SlotActionType.QUICK_CRAFT, client.player);
            }
            client.interactionManager.clickSlot(sc.syncId, -999, 2, SlotActionType.QUICK_CRAFT, client.player);

            if(craft== 0){
                client.interactionManager.clickSlot(sc.syncId, i, 0, SlotActionType.PICKUP, player);
                continue;
            }
            if(craft > cursorStackCount){
                int craftCopy = craft;
                for (Integer integer : numArr) {
                    recInv[integer] += 1;
                    if(--craftCopy <= 0) break;
                }
            }else {
                for (Integer integer : numArr) {

                    if(recInv[integer] + stackCount/craft >= stack.getMaxCount()){
                        int num = stack.getMaxCount() - recInv[integer];
                        cursorStackCount -= num;
                        recInv[integer] += num;
                    }else {
                        cursorStackCount -= stackCount/craft;
                        recInv[integer] += stackCount/craft;
                    }
                }
            }
            if(cursorStackCount > 0){
                client.interactionManager.clickSlot(sc.syncId, i, 0, SlotActionType.PICKUP, player);
            }

            if(skip == recipeItems.length) break;
        }
//        for (int i2 = 0; InventoryUtils.areStacksEqual(sc.slots.get(0).getStack(), recipe.getResult()) && i2 < 64; i2++) {
        for (int i2 = 1; satisfyCraft() && i2 < 64; i2++) {
            client.interactionManager.clickSlot(sc.syncId, 0, 1, SlotActionType.THROW, player);
        }
        client.interactionManager.clickSlot(sc.syncId, -999, 2, SlotActionType.QUICK_CRAFT, client.player);
        client.interactionManager.clickSlot(sc.syncId, -999, 2, SlotActionType.QUICK_CRAFT, client.player);
        player.closeHandledScreen();
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
        if (!updateRecipe()) return;
        client = MinecraftClient.getInstance();
        ClientPlayerEntity player;
        if (client.player == null) return;
        player = client.player;
        if (player.currentScreenHandler.equals(player.playerScreenHandler)) return;
        for (ItemStack recipeItem : recipe.getRecipeItems()) {
            dropItem(recipeItem, false);
        }
        player.closeHandledScreen();
    }
    public static BlockPos storagePos = null;
    public static boolean autoStorage = false;

    public static void autoStorage() {
        if (client.player == null || !updateRecipe() || !isInventory(storagePos)) return;
        ClientPlayerEntity player = client.player;
        DefaultedList<Slot> slots = player.currentScreenHandler.slots;
        if (storagePos != null && storagePos.isWithinDistance(player.getPos(), 5) && step != 3 && closeScreen <= 0) {
            if (slots.stream()
                    .anyMatch(slot -> InventoryUtils.areStacksEqual(slot.getStack(), recipe.getResult()) && slot.getStack().getCount() > 1))
            {
//                System.out.println("autoStorage");
                closeScreen = 1;
                step = 3;
                invUpdated = false;
                client.player.networkHandler.sendPacket(new ClientCommandC2SPacket(client.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
                useBlock(storagePos);
            }
        }
    }
    public static void storage() {
        if (!updateRecipe()) return;
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        ScreenHandler sc = player.currentScreenHandler;
        if(sc.equals(player.playerScreenHandler)) return;
        DefaultedList<Slot> slots = sc.slots;
        if (slots.stream()
                .limit(slots.get(0).inventory.size())
                .allMatch(slot -> InventoryUtils.areStacksEqual(slot.getStack(), recipe.getResult())
                        && slot.getStack().getCount() >= slot.getStack().getMaxCount())) {
            client.inGameHud.setOverlayMessage(Text.of("合成助手: 该容器已满"), false);
            player.closeHandledScreen();
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
    }

    private static boolean updateRecipe() {
        recipe = RecipeStorage.getInstance().getSelectedRecipe();
        return !recipe.getResult().isEmpty();
    }

    public static void continueSynthesis() {
        if (pos != null ) {
            ScreenHandler sc = client.player.currentScreenHandler;
            ItemStack[] recipeItems = recipe.getRecipeItems();
            if (((recipeItems.length == 9 && !(sc instanceof CraftingScreenHandler)) || (recipeItems.length == 4 && !(sc instanceof PlayerScreenHandler)))) {
                if (recipeItems.length == 9 && sc instanceof PlayerScreenHandler && closeScreen <= 0) {
                    if (client.world.getBlockState(pos).isAir()) return;
//                    System.out.println(".............");
                    closeScreen = 1;
//                    tick = 0;
                    step = 2;
                    invUpdated = false;
                    client.player.networkHandler.sendPacket(new ClientCommandC2SPacket(client.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
                    useBlock(pos);
                } else if (recipeItems.length == 4) {
                    client.player.closeHandledScreen();
                }
            }else if(invUpdated && step == 2) {
                synthesis2();
            }else{
                client.player.closeScreen();
            }
        }
    }
}