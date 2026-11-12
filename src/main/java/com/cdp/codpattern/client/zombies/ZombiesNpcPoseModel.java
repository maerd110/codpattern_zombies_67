package com.cdp.codpattern.client.zombies;

import com.cdp.codpattern.app.zombies.npc.ZombiesNpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import com.mojang.blaze3d.vertex.*;

/** Two-segment legs allow bent knees rather than rotating an entire straight leg. */
public final class ZombiesNpcPoseModel extends PlayerModel<ZombiesNpcEntity> {
    private final ModelPart legs;
    private boolean bent;
    public ZombiesNpcPoseModel(ModelPart root){super(root,false);
        var mesh=new MeshDefinition();var definition=mesh.getRoot();
        for(String side:new String[]{"left","right"}){
            var thigh=definition.addOrReplaceChild(side,CubeListBuilder.create().texOffs(side.equals("left")?16:0,side.equals("left")?48:16).addBox(-2,0,-2,4,6,4),PartPose.offset(side.equals("left")?1.9F:-1.9F,12,0));
            thigh.addOrReplaceChild("shin",CubeListBuilder.create().texOffs(side.equals("left")?16:0,side.equals("left")?54:22).addBox(-2,0,-2,4,6,4),PartPose.offset(0,6,0));
        }
        legs=LayerDefinition.create(mesh,64,64).bakeRoot();
    }
    @Override public void setupAnim(ZombiesNpcEntity npc,float swing,float amount,float age,float yaw,float pitch){
        for(var part:java.util.List.of(head,hat,body,leftArm,rightArm,leftLeg,rightLeg,leftSleeve,rightSleeve,leftPants,rightPants,jacket))part.resetPose();
        riding=npc.storyAlive()&&npc.posture()==1;super.setupAnim(npc,swing,amount,age,yaw,pitch);
        applyPose(npc.posture(),npc.storyAlive());
        if(!npc.getMainHandItem().isEmpty()&&!npc.lying()&&npc.posture()==0){rightArm.xRot=-1.4F+head.xRot;leftArm.xRot=rightArm.xRot;rightArm.yRot=head.yRot-.12F;leftArm.yRot=head.yRot+.35F;}
        hat.copyFrom(head);jacket.copyFrom(body);leftSleeve.copyFrom(leftArm);rightSleeve.copyFrom(rightArm);leftPants.copyFrom(leftLeg);rightPants.copyFrom(rightLeg);
    }
    public void applyPose(int posture,boolean alive){
        if(!alive&&posture!=4)posture=2;
        bent=posture==3||posture==2;
        leftLeg.visible=rightLeg.visible=leftPants.visible=rightPants.visible=!bent;
        for(String side:new String[]{"left","right"}){var leg=legs.getChild(side);leg.xRot=posture==3?-1.6F:-.75F;leg.yRot=side.equals("left")?-.28F:.28F;leg.zRot=0;leg.getChild("shin").xRot=posture==3?2.2F:1.3F;}
        if(posture==3){body.xRot=.13F;head.xRot=.22F;rightArm.xRot=leftArm.xRot=-.75F;rightArm.zRot=.10F;leftArm.zRot=-.10F;}
        else if(posture==2){head.xRot=head.yRot=head.zRot=0;leftArm.x=2;rightArm.xRot=-1.05F;leftArm.xRot=-1.3F;rightArm.zRot=-.40F;leftArm.zRot=.30F;}
        else if(posture==4){head.xRot=head.yRot=head.zRot=0;leftArm.xRot=rightArm.xRot=0;leftArm.zRot=-.08F;rightArm.zRot=.08F;leftLeg.xRot=rightLeg.xRot=0;body.z=leftArm.z=rightArm.z=leftLeg.z=rightLeg.z=2;}
        hat.copyFrom(head);jacket.copyFrom(body);leftSleeve.copyFrom(leftArm);rightSleeve.copyFrom(rightArm);leftPants.copyFrom(leftLeg);rightPants.copyFrom(rightLeg);
    }
    @Override public void renderToBuffer(PoseStack stack,VertexConsumer vertices,int light,int overlay,float r,float g,float b,float a){
        super.renderToBuffer(stack,vertices,light,overlay,r,g,b,a);if(bent)legs.render(stack,vertices,light,overlay,r,g,b,a);
    }
}
