package com.qouteall.imm_ptl_peripheral.altius_world;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.function.Consumer;

public class DimTermWidget extends EntryListWidget.Entry<DimTermWidget> {
    
    public final RegistryKey<World> dimension;
    public final DimListWidget parent;
    private final Consumer<DimTermWidget> selectCallback;
    private final Identifier dimIconPath;
    private final Text dimensionName;
    
    public final static int widgetHeight = 50;
    
    public DimTermWidget(
        RegistryKey<World> dimension,
        DimListWidget parent,
        Consumer<DimTermWidget> selectCallback
    ) {
        this.dimension = dimension;
        this.parent = parent;
        this.selectCallback = selectCallback;
        
        this.dimIconPath = getDimensionIconPath(this.dimension);
        
        this.dimensionName = getDimensionName(dimension);
    }
    
    @Override
    public void render(
        MatrixStack matrixStack,
        int index,
        int y,
        int width,
        int height,
        int mouseX,
        int mouseY,
        int i,
        boolean bl,
        float f
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        client.textRenderer.draw(
            matrixStack, dimensionName.getString(),
            width + widgetHeight + 3, (float) (y),
            0xFFFFFFFF
        );
        
        client.textRenderer.draw(
            matrixStack, dimension.getValue().toString(),
            width + widgetHeight + 3, (float) (y + 10),
            0xFF999999
        );
        
        client.getTextureManager().bindTexture(dimIconPath);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        
        DrawableHelper.drawTexture(
            matrixStack,
            width, y, 0, (float) 0,
            widgetHeight - 4, widgetHeight - 4,
            widgetHeight - 4, widgetHeight - 4
        );
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        selectCallback.accept(this);
        super.mouseClicked(mouseX, mouseY, button);
        return true;//allow outer dragging
        /**
         * {@link EntryListWidget#mouseClicked(double, double, int)}
         */
    }
    
    public static Identifier getDimensionIconPath(RegistryKey<World> dimension) {
        Identifier id = dimension.getValue();
        return new Identifier(
            id.getNamespace(),
            "textures/dimension/" + id.getPath() + ".png"
        );
    }
    
    private static TranslatableText getDimensionName(RegistryKey<World> dimension) {
        return new TranslatableText(
            "dimension." + dimension.getValue().getNamespace() + "."
                + dimension.getValue().getPath()
        );
    }
}
