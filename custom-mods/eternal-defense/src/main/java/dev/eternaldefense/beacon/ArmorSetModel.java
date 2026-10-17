package dev.eternaldefense.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.List;

/** One worn armor piece; the cubes under a part's "glow" child are drawn again at full brightness. */
public class ArmorSetModel extends HumanoidModel<LivingEntity> {
    private final List<ModelPart> base;
    private final List<ModelPart> glow=new ArrayList<>();
    public ArmorSetModel(ModelPart root){
        super(root);base=List.of(head,hat,body,rightArm,leftArm,rightLeg,leftLeg);
        for(var part:base)if(part.hasChild("glow"))glow.add(part.getChild("glow"));
    }
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer buffer,int light,int overlay,float r,float g,float b,float a){
        // Each model holds one piece only, so every part may draw (the duster's tails ride on the legs)
        setAllVisible(true);hat.visible=false;
        for(var part:glow)part.visible=false;
        super.renderToBuffer(pose,buffer,light,overlay,r,g,b,a);
        if(glow.isEmpty())return;
        for(var part:glow)part.visible=true;
        for(var part:base)part.skipDraw=true;
        super.renderToBuffer(pose,buffer,LightTexture.FULL_BRIGHT,overlay,r,g,b,a);
        for(var part:base)part.skipDraw=false;
    }
}
