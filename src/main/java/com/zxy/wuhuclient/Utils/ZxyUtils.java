package com.zxy.wuhuclient.Utils;

import com.zxy.wuhuclient.features_list.SyncInventory;
import com.zxy.wuhuclient.features_list.Synthesis;
import com.zxy.wuhuclient.features_list.Test;
import com.zxy.wuhuclient.features_list.litematica_helper.LitematicaHelper;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;


import java.util.*;

import net.minecraft.text.Text;


//#if MC > 11802
import net.minecraft.text.MutableText;
//#else
//$$ import net.minecraft.text.TranslatableText;
//#endif

import static com.zxy.wuhuclient.Utils.ZxyUtils.TempData.max;
import static com.zxy.wuhuclient.Utils.ZxyUtils.TempData.min;
import static com.zxy.wuhuclient.WuHuClientMod.client;
import static com.zxy.wuhuclient.config.Configs.SEARCH_BLOCK_COLOR;
import static com.zxy.wuhuclient.config.Configs.SEARCH_BLOCK_LIST;
import static fi.dy.masa.litematica.selection.SelectionMode.NORMAL;

public class ZxyUtils {
    public static boolean isLoadQuiShulker = isLoadMod("quickshulker");
    public static boolean isLoadChestTracker = isLoadMod("chesttracker");
    public static boolean isLoadMod(String modId){
        return FabricLoader.getInstance().isModLoaded(modId);
    }
    public static void tick(){
        searchBlockThread();
        Synthesis.tick();
        Test.tick();
        if (SyncInventory.num==2) SyncInventory.syncInv();
    }
    public static class TempData {
        public static int[] min;
        public static int[] max;

        public static boolean xuanQuFanWeiNei_p(BlockPos pos) {
            AreaSelection i = DataManager.getSelectionManager().getCurrentSelection();
            if (i == null) return false;
            if (DataManager.getSelectionManager().getSelectionMode() == NORMAL) {
                boolean fw = false;
                List<Box> arr = i.getAllSubRegionBoxes();
                for (int j = 0; j < arr.size(); j++) {
                    if (comparePos(arr.get(j), pos)) {
                        return true;
                    } else {
                        fw = false;
                    }
                }
                return fw;
            } else {
                Box box = i.getSubRegionBox(DataManager.getSimpleArea().getName());
                return comparePos(box, pos);
            }
        }

        static boolean comparePos(Box box, BlockPos pos) {
            int x = 0, y = 0, z = 0;
            if (pos != null) {
                x = pos.getX();
                y = pos.getY();
                z = pos.getZ();
            }
            if (box == null) return false;
            BlockPos kpos1 = box.getPos1();
            BlockPos kpos2 = box.getPos2();
            min = new int[]{
                    kpos1.getX() < kpos2.getX() ? kpos1.getX() : kpos2.getX(),
                    kpos1.getY() < kpos2.getY() ? kpos1.getY() : kpos2.getY(),
                    kpos1.getZ() < kpos2.getZ() ? kpos1.getZ() : kpos2.getZ()
            };
            max = new int[]{
                    kpos1.getX() > kpos2.getX() ? kpos1.getX() : kpos2.getX(),
                    kpos1.getY() > kpos2.getY() ? kpos1.getY() : kpos2.getY(),
                    kpos1.getZ() > kpos2.getZ() ? kpos1.getZ() : kpos2.getZ()
            };
            if (
                    x < min[0] || x > max[0] ||
                            y < min[1] || y > max[1] ||
                            z < min[2] || z > max[2]
            ) {
                return false;
            } else {
                return true;
            }
        }

        public ClientPlayerEntity player;
        public ClientWorld world;
        public WorldSchematic worldSchematic;

        public TempData(ClientPlayerEntity player, ClientWorld world, WorldSchematic worldSchematic) {
            this.player = player;
            this.world = world;
            this.worldSchematic = worldSchematic;
        }
    }

    public static LinkedList<BlockPos> siftBlock(String blockName) {
        LinkedList<BlockPos> blocks = new LinkedList<>();
        AreaSelection i = DataManager.getSelectionManager().getCurrentSelection();
        List<Box> box;
        if (i == null) return blocks;
        box = i.getAllSubRegionBoxes();
        for (int index = 0; index < box.size(); index++) {
            TempData.comparePos(box.get(index), null);
            for (int x = min[0]; x <= max[0]; x++) {
                for (int y = min[1]; y <= max[1]; y++) {
                    for (int z = min[2]; z <= max[2]; z++) {
                        BlockPos pos = new BlockPos(new BlockPos(x, y, z));
                        BlockState state = null;
                        if (client.world != null) {
                            state = client.world.getBlockState(pos);
                        }
                        Block block = state.getBlock();
                        String string = Registries.BLOCK.getId(block).toString();

                        if (blockName.length() > 2) {
                            String fix = null;
                            String[] split = blockName.split("-");
                            fix = split[split.length-1];
                            if ("a".equals(fix)) {
                                String substring = blockName.substring(0, blockName.length() - 2);
                                if (substring.equals(string)) {
                                    blocks.add(pos);
                                }
                                continue;
                            }else if ("inventory".equals(fix) && InventoryUtils.isInventory(pos)){
                                blocks.add(pos);
                            }else if("all".equals(fix)){
                                blocks.add(pos);
                            }
                        }
                        if (string.contains(blockName)) {
                            blocks.add(pos);
                        }
                    }
                }
            }
        }
        return blocks;
    }

    public static String searchBlockId = "searchBlock";
    public static boolean searchBlockSwitch = false;
    public static void startSearchBlock(){
        searchBlockSwitch = !searchBlockSwitch;
        Set<BlockPos> highlightBlockPosList = HighlightBlockRenderer.getHighlightBlockPosList(searchBlockId);
        if(!searchBlockSwitch && highlightBlockPosList != null) {
            HighlightBlockRenderer.clear(searchBlockId);
            return;
        }
        HighlightBlockRenderer.createHighlightBlockList(searchBlockId,SEARCH_BLOCK_COLOR);
    }
    public static boolean searchBlockIng = false;
    public synchronized static void searchBlock(){
            if(!searchBlockSwitch) return;
            searchBlockIng = true;
            LinkedHashSet<BlockPos> blockPos = new LinkedHashSet<>();
            List<String> strings = SEARCH_BLOCK_LIST.getStrings();
            for (String blockName : strings) {
                LinkedList<BlockPos> blockPosLinkedList = siftBlock(blockName);
                List<BlockPos> list = blockPosLinkedList.stream().distinct().toList();
                blockPos.addAll(list);
            }
            HighlightBlockRenderer.setPos(searchBlockId,blockPos);
    }
    public static void searchBlockThread(){
        if (!searchBlockIng) {
            new Thread(() -> {
                try {
                    searchBlockIng = true;
                    searchBlock();
                    if(isLoadChestTracker) LitematicaHelper.instance.highlightInventoryBlock();
                }finally {
                    searchBlockIng = false;
                }
            }).start();
        }
    }

    public static void actionBar(String message){
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        //#if MC > 11802
        MutableText translatable = Text.translatable(message);
        //#else
        //$$ TranslatableText translatable = new TranslatableText(message);
        //#endif
        minecraftClient.inGameHud.setOverlayMessage(translatable,false);
    }
}
