package com.zxy.wuhuclient.features_list;


import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;


import static com.zxy.wuhuclient.Utils.ZxyUtils.getPlayer;
import static com.zxy.wuhuclient.WuHuClientMod.*;

public class QuickClickSlot {

    public static void clickLastSlot() {
        getPlayer().ifPresent(clientPlayer -> {
            ScreenHandler sc = clientPlayer.currentScreenHandler;
            if (clientPlayer.currentScreenHandler.equals(clientPlayer.playerScreenHandler)) return;
            for (int i = 0; i < sc.slots.size(); i++) {
                if (sc.slots.get(i).inventory instanceof PlayerInventory && i > 0) {
                    if (sc instanceof CraftingScreenHandler) i = 1;
                    client.interactionManager.clickSlot(sc.syncId, i-1, 0, SlotActionType.QUICK_MOVE, client.player);
                    return;
                }
            }

            //下面的代码在砂轮下不行
//            int size = sc.slots.get(0).inventory.size();
//            client.interactionManager.clickSlot(sc.syncId, size-1, 0, SlotActionType.QUICK_MOVE, client.player);
        });
    }
}
