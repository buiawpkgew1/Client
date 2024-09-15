package com.zxy.wuhuclient.features_list;

import com.zxy.wuhuclient.Utils.HighlightBlockRenderer;
import com.zxy.wuhuclient.Utils.InventoryUtils;
import com.zxy.wuhuclient.Utils.ScreenManagement;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.*;

import static com.zxy.wuhuclient.Utils.InventoryUtils.equalsItem;
import static com.zxy.wuhuclient.Utils.ZxyUtils.siftBlock;
import static com.zxy.wuhuclient.WuHuClientMod.client;
import static com.zxy.wuhuclient.config.Configs.*;
import static net.minecraft.block.ShulkerBoxBlock.FACING;

public class SyncInventory {
    public static LinkedList<BlockPos> syncPosList = new LinkedList<>();
    public static ArrayList<ItemStack> targetBlockInv;
    public static int num = 0;
    static BlockPos blockPos = null;
    static String syncInventoryId = "syncInventory";
    static Set<BlockPos> highlightPosList = new LinkedHashSet<>();
    static Map<ItemStack, Integer> targetItemsCount = new HashMap<>();
    static Map<ItemStack, Integer> playerItemsCount = new HashMap<>();

    private static void getReadyColor() {
        HighlightBlockRenderer.createHighlightBlockList(syncInventoryId,SYNC_INVENTORY_COLOR);
        highlightPosList = HighlightBlockRenderer.getHighlightBlockPosList(syncInventoryId);
    }

    public static void startOrOffSyncInventory() {
        getReadyColor();
        if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK && syncPosList.isEmpty()) {
            BlockPos pos = ((BlockHitResult) client.crosshairTarget).getBlockPos();
            BlockState blockState = client.world.getBlockState(pos);
            Block block = null;
            if (client.world != null) {
                block = client.world.getBlockState(pos).getBlock();
                BlockEntity blockEntity = client.world.getBlockEntity(pos);
                boolean inventory = InventoryUtils.isInventory(pos);
                try {
                    if ((inventory && blockState.createScreenHandlerFactory(client.world,pos) == null)  ||
                            (blockEntity instanceof ShulkerBoxBlockEntity entity &&
                                    //#if MC > 12004
                                    !client.world.isSpaceEmpty(ShulkerEntity.calculateBoundingBox(1.0F, blockState.get(FACING), 0.0F, 0.5F).offset(pos).contract(1.0E-6)) &&
                                    //#else
                                    //$$ !client.world.isSpaceEmpty(ShulkerEntity.calculateBoundingBox(blockState.get(FACING), 0.0f, 0.5f).offset(pos).contract(1.0E-6)) &&
                                    //#endif
                                    entity.getAnimationStage() == ShulkerBoxBlockEntity.AnimationStage.CLOSED)) {
                        client.inGameHud.setOverlayMessage(Text.of("容器无法打开"), false);
                        return;
                    }else if(!inventory) {
                        client.inGameHud.setOverlayMessage(Text.of("这不是容器 无法同步"), false);
                        return;
                    }
                } catch (Exception e) {
                    client.inGameHud.setOverlayMessage(Text.of("这不是容器 无法同步"), false);
                    return;
                }
            }
            String blockName = Registries.BLOCK.getId(block).toString();
            syncPosList.addAll(siftBlock(blockName));
            if (!syncPosList.isEmpty()) {
                if (client.player == null) return;
                client.player.closeHandledScreen();
                if (!openInv(pos, false)) {
                    syncPosList = new LinkedList<>();
                    return;
                }
                highlightPosList.addAll(syncPosList);
                ScreenManagement.closeScreen++;
                num = 1;
            }
        } else if(!syncPosList.isEmpty()){
            highlightPosList.removeAll(syncPosList);
            syncPosList = new LinkedList<>();
            if (client.player != null) client.player.closeScreen();
            num = 0;
            client.inGameHud.setOverlayMessage(Text.of("已取消同步"), false);
        }
    }

    public static boolean openInv(BlockPos pos, boolean ignoreThePrompt) {
        if (client.player != null && client.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > 25D) {
            if (!ignoreThePrompt) client.inGameHud.setOverlayMessage(Text.of("距离过远无法打开容器"), false);
            return false;
        }
        if (client.interactionManager != null) {
            //#if MC > 11802
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), Direction.DOWN, pos, false));
            //#else
            //$$ client.interactionManager.interactBlock(client.player,client.world, Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(pos), Direction.DOWN,pos,false));
            //#endif
            return true;
        } else return false;
    }

    public static void itemsCount(Map<ItemStack, Integer> itemsCount, ItemStack itemStack) {
        // 判断是否存在可合并的键
        Optional<Map.Entry<ItemStack, Integer>> entry = itemsCount.entrySet().stream()
                //#if MC > 12004
                .filter(e -> ItemStack.areItemsAndComponentsEqual(e.getKey(), itemStack))
                //#else
                //$$ .filter(e -> ItemStack.canCombine(e.getKey(), itemStack))
                //#endif
                .findFirst();

        if (entry.isPresent()) {
            // 更新已有键对应的值
            Integer count = entry.get().getValue();
            count += itemStack.getCount();
            itemsCount.put(entry.get().getKey(), count);
        } else {
            // 添加新键值对
            itemsCount.put(itemStack, itemStack.getCount());
        }
    }

    public static void syncInv() {
        switch (num) {
            case 1 -> {
                //按下热键后记录看向的容器 开始同步容器 只会触发一次
                targetBlockInv = new ArrayList<>();
                targetItemsCount = new HashMap<>();
                if (client.player != null && !client.player.currentScreenHandler.equals(client.player.playerScreenHandler)) {
                    for (int i = 0; i < client.player.currentScreenHandler.slots.get(0).inventory.size(); i++) {
                        ItemStack copy = client.player.currentScreenHandler.slots.get(i).getStack().copy();
                        itemsCount(targetItemsCount, copy);
                        targetBlockInv.add(copy);
                    }
                    //上面如果不使用copy()在关闭容器后会使第一个元素号变该物品成总数 非常有趣...
//                    System.out.println("???1 "+targetBlockInv.get(0).getCount());
                    client.player.closeHandledScreen();
//                    System.out.println("!!!1 "+targetBlockInv.get(0).getCount());
                    num = 2;
                }
            }
            case 2 -> {
                //打开列表中的容器 只要容器同步列表不为空 就会一直执行此处
                if (client.player == null) return;
                playerItemsCount = new HashMap<>();
                client.inGameHud.setOverlayMessage(Text.of("剩余 " + syncPosList.size() + " 个容器. 再次按下快捷键取消同步"), false);
                if (!client.player.currentScreenHandler.equals(client.player.playerScreenHandler)) return;
                DefaultedList<Slot> slots = client.player.playerScreenHandler.slots;
                slots.forEach(slot -> itemsCount(playerItemsCount, slot.getStack()));
//                if(targetItemsCount.keySet().stream()
//                        .noneMatch(itemStack -> playerItemsCount.keySet().stream()
//                                .anyMatch(itemStack1 -> ItemStack.canCombine(itemStack,itemStack1)))) return;
                if (SYNC_INVENTORY_CHECK.getBooleanValue() && !targetItemsCount.entrySet().stream()
                        .allMatch(target -> playerItemsCount.entrySet().stream()
                                .anyMatch(player ->
                                        equalsItem(player.getKey(), target.getKey()) && target.getValue() <= player.getValue())))

                    return;


                for (BlockPos pos : syncPosList) {
                    if (!openInv(pos, true)) continue;
                    ScreenManagement.closeScreen++;
                    blockPos = pos;
                    num = 3;
                    break;
                }
                if (syncPosList.isEmpty()) {
                    num = 0;
                    client.inGameHud.setOverlayMessage(Text.of("同步完成"), false);
                }
            }
            case 3 -> {
                //开始同步 在打开容器后触发
                ScreenHandler sc = client.player.currentScreenHandler;
                if (sc.equals(client.player.playerScreenHandler)) return;
                int size = Math.min(targetBlockInv.size(), sc.slots.get(0).inventory.size());

                int times = 0;
                for (int i = 0; i < size; i++) {
                    ItemStack item1 = sc.slots.get(i).getStack();
                    ItemStack item2 = targetBlockInv.get(i).copy();
                    int currNum = item1.getCount();
                    int tarNum = item2.getCount();
                    boolean same = equalsItem(item1, item2.copy()) && !item1.isEmpty();
                    if (equalsItem(item1, item2) && currNum == tarNum) continue;
                    //不和背包交互
                    if (same) {
                        //有多
                        while (currNum > tarNum) {
                            client.interactionManager.clickSlot(sc.syncId, i, 0, SlotActionType.THROW, client.player);
                            currNum--;
                        }
                    } else {
                        //不同直接扔出
                        client.interactionManager.clickSlot(sc.syncId, i, 1, SlotActionType.THROW, client.player);
                        times++;
                    }
                    boolean thereAreItems = false;
                    //背包交互
                    for (int i1 = size; i1 < sc.slots.size(); i1++) {
                        ItemStack stack = sc.slots.get(i1).getStack();
                        ItemStack currStack = sc.slots.get(i).getStack();
                        currNum = currStack.getCount();
                        boolean same2 = thereAreItems = equalsItem(item2, stack);
                        if (same2 && !stack.isEmpty()) {
                            int i2 = stack.getCount();
                            client.interactionManager.clickSlot(sc.syncId, i1, 0, SlotActionType.PICKUP, client.player);
                            for (; currNum < tarNum && i2 > 0; i2--) {
                                client.interactionManager.clickSlot(sc.syncId, i, 1, SlotActionType.PICKUP, client.player);
                                currNum++;
                            }
                            client.interactionManager.clickSlot(sc.syncId, i1, 0, SlotActionType.PICKUP, client.player);
                        }
                        //这里判断没啥用，因为一个游戏刻操作背包太多次.getStack().getCount()获取的数量不准确 下次一定优化，
                        if (currNum != tarNum) times++;
                    }
                    if (!thereAreItems) times++;
                }
                if (times == 0) {
                    syncPosList.remove(blockPos);
                    highlightPosList.remove(blockPos);
                    blockPos = null;
                }
                client.player.closeHandledScreen();
                num = 2;
            }
        }
    }
}
