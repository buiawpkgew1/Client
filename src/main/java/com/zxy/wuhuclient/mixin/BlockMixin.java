package com.zxy.wuhuclient.mixin;

import com.zxy.wuhuclient.featuresList.Synthesis;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.zxy.wuhuclient.Utils.ScreenManagement.closeScreen;
import static com.zxy.wuhuclient.featuresList.Synthesis.*;

@Mixin(Block.class)
public class BlockMixin {
    @Inject(at = @At("TAIL"),method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;)V")
    private static void dropStacks(BlockState state, WorldAccess world, BlockPos pos, BlockEntity blockEntity, CallbackInfo ci){
        if(step == 1 || step == 3 && (pos.equals(storagePos) || pos.equals(dropPos))){
            closeScreen = 0;
            if (client.player != null) client.player.closeHandledScreen();
            step = 0;
        }
    }
}
