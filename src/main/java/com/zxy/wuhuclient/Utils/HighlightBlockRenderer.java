package com.zxy.wuhuclient.Utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zxy.wuhuclient.features_list.litematica_helper.LitematicaHelper;
import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.Color4f;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.*;

import static com.zxy.wuhuclient.Utils.ZxyUtils.searchBlockId;
import static com.zxy.wuhuclient.WuHuClientMod.client;
import static com.zxy.wuhuclient.config.Configs.LITEMATICA_HELPER;
import static com.zxy.wuhuclient.config.Configs.SEARCH_BLOCK_LIMIT;

public class HighlightBlockRenderer implements IRenderer {
    public record HighlightTheProject(ConfigColor color4f, Set<BlockPos> pos){ }
    public static HighlightBlockRenderer instance = new HighlightBlockRenderer();
    public static Map<String,HighlightTheProject> highlightTheProjectMap = new HashMap<>();
    public static boolean shaderIng = false;
    public static void createHighlightBlockList(String id,ConfigColor color4f){
        if (highlightTheProjectMap.get(id) == null) {
            highlightTheProjectMap.put(id,new HighlightTheProject(color4f,new LinkedHashSet<>()));
        }
    }
    public static Set<BlockPos> getHighlightBlockPosList(String id){
        if(highlightTheProjectMap.get(id) != null){
            return highlightTheProjectMap.get(id).pos();
        }
        return null;
    }
    public static List<String> clearList = new LinkedList<>();
    public static void clear(String id){
        if (!clearList.contains(id)) clearList.add(id);
    }
    public static Map<String,Set<BlockPos>> setMap = new HashMap<>();
    public static void setPos(String id,Set<BlockPos> posSet){
        HighlightTheProject highlightTheProject = highlightTheProjectMap.get(id);
        if (highlightTheProject != null && posSet != null) {
            setMap.put(id,posSet);
        }
    }
    public void highlightBlock(Color4f color4f, BlockPos pos) {
        BlockState blockState = client.world.getBlockState(pos);
        Entity cameraEntity = client.cameraEntity;
        if(cameraEntity == null) return;
        VoxelShape voxelShape = blockState.getCollisionShape(client.world, pos,ShapeContext.of(cameraEntity));
        voxelShape = voxelShape.getBoundingBoxes().stream()
                .map(VoxelShapes::cuboid)
                .reduce(VoxelShapes::union)
                .orElse(VoxelShapes.empty()).simplify();
        Vec3d pos1 = client.gameRenderer.getCamera().getPos();
        double x = pos.getX() - pos1.x;
        double y = pos.getY() - pos1.y;
        double z = pos.getZ() - pos1.z;

        RenderSystem.disableDepthTest();

        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-1.0F, -1.0F);
        //#if MC > 12006
        BuiltBuffer meshData;
        //#endif
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
//        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tessellator instance = Tessellator.getInstance();
        //#if MC > 12006
        BufferBuilder buffer = instance.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        //#else
        //$$ BufferBuilder buffer = instance.getBuffer();
        //#endif

        //#if MC > 12006
        voxelShape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                RenderUtils.drawBoxAllSidesBatchedQuads(
                        (float)(minX + x),
                        (float)(minY + y),
                        (float)(minZ + z),
                        (float)(maxX + x),
                        (float)(maxY + y),
                        (float)(maxZ + z),
                        color4f, buffer));
        //#else
        //$$ if (!buffer.isBuilding()) buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        //$$ voxelShape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
        //$$         RenderUtils.drawBoxAllSidesBatchedQuads(
        //$$                 minX + x,
        //$$                 minY + y,
        //$$                 minZ + z,
        //$$                 maxX + x,
        //$$                 maxY + y,
        //$$                 maxZ + z,
        //$$                 color4f, buffer));
        //#endif




        //#if MC > 12006
        try
        {
            meshData = buffer.end();
            BufferRenderer.drawWithGlobalProgram(meshData);
            meshData.close();
        }
        catch (Exception e)
        {
            Litematica.logger.error("renderSchematicMismatches: Failed to draw Schematic Mismatches (Step 2) (Error: {})", e.getLocalizedMessage());
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        //#else
        //$$ instance.draw();
        //$$ RenderSystem.enableCull();
        //$$ RenderSystem.disableBlend();
        //$$ RenderSystem.enableDepthTest();
        //#endif

        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }
//    public void test3(MatrixStack matrices){
//
//        BlockPos pos1 = client.player.getBlockPos().up(-1);
//        BlockPos pos2 = client.player.getBlockPos().up(-2);
//        fi.dy.masa.litematica.render.RenderUtils.renderAreaSides(pos1, pos1, new Color4f(1,1,0,0.5F), matrices, client);
//    }

    //如果不注册无法渲染，
    public static void init(){
        RenderEventHandler.getInstance().registerWorldLastRenderer(instance);
    }

    @Override
    //#if MC > 12004
    public void onRenderWorldLast(Matrix4f matrices, Matrix4f projMatrix){
    //#else
    //$$ public void onRenderWorldLast(MatrixStack matrices, Matrix4f projMatrix){
    //#endif
        shaderIng = true;
        for (Map.Entry<String, HighlightTheProject> stringHighlightTheProjectEntry : highlightTheProjectMap.entrySet()) {
            String key = stringHighlightTheProjectEntry.getKey();
            HighlightTheProject value = stringHighlightTheProjectEntry.getValue();
            if(!LITEMATICA_HELPER.getBooleanValue() && LitematicaHelper.instance.litematicaHelper.equals(key)) continue;

            setMap.forEach((k,v) -> {
                if (key.equals(k)) {
                    value.pos.clear();
                    value.pos.addAll(v);
                }
            });
            setMap.clear();

            Set<BlockPos> pos = value.pos;
            Color4f color = value.color4f.getColor();

            for (BlockPos p : pos) {
                if (searchBlockId.equals(key) && SEARCH_BLOCK_LIMIT.getBooleanValue() && !DataManager.getRenderLayerRange().isPositionWithinRange(p)) continue;
                instance.highlightBlock(color,p);
            }
            for (String s : clearList) {
                if (key.equals(s)) {
                    value.pos.clear();
                    clearList.remove(s);
                }
            }
            clearList.clear();
        }
        shaderIng = false;
    }
}