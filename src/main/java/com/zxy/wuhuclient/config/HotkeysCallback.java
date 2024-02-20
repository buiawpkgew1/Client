package com.zxy.wuhuclient.config;

import com.zxy.wuhuclient.featuresList.SyncInventory;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import net.minecraft.client.MinecraftClient;

import static com.zxy.wuhuclient.config.Configs.*;

//监听按键
public class HotkeysCallback implements IHotkeyCallback {
    MinecraftClient client = MinecraftClient.getInstance();

    //激活的热键会被key记录
    @Override
    public boolean onKeyAction(KeyAction action, IKeybind key) {
        if(key == WUHU_CLIENT.getKeybind()){
            client.setScreen(new ConfigUi());
            return true;
        }else if(key == SYNC_INVENTORY.getKeybind()){
            SyncInventory.startOrOffSyncInventory();
        }
        return false;
    }

    //设置反馈到onKeyAction()方法的快捷键
    public static void init(){
        HotkeysCallback hotkeysCallback = new HotkeysCallback();

        for (ConfigHotkey configHotkey : Configs.KEY_LIST) {
            configHotkey.getKeybind().setCallback(hotkeysCallback);
        }
    }
}
