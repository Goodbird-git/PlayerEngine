/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.player2.playerengine.automaton.client;

import com.player2.playerengine.automaton.entity.CustomFishingBobberEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class CustomFishingBobberRenderer extends EntityRenderer<CustomFishingBobberEntity, FishingHookRenderState> {
   private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
   private static final RenderType RENDER_TYPE;
   private static final double VIEW_BOBBING_SCALE = (double)960.0F;

   public CustomFishingBobberRenderer(EntityRendererProvider.Context context) {
      super(context);
   }

   public boolean shouldRender(CustomFishingBobberEntity livingEntity, Frustum camera, double camX, double camY, double camZ) {
      return super.shouldRender(livingEntity, camera, camX, camY, camZ) && livingEntity.getPlayerOwner() != null;
   }

   public void render(FishingHookRenderState renderState, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      poseStack.pushPose();
      poseStack.pushPose();
      poseStack.scale(0.5F, 0.5F, 0.5F);
      poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
      PoseStack.Pose pose = poseStack.last();
      VertexConsumer vertexConsumer = bufferSource.getBuffer(RENDER_TYPE);
      vertex(vertexConsumer, pose, packedLight, 0.0F, 0, 0, 1);
      vertex(vertexConsumer, pose, packedLight, 1.0F, 0, 1, 1);
      vertex(vertexConsumer, pose, packedLight, 1.0F, 1, 1, 0);
      vertex(vertexConsumer, pose, packedLight, 0.0F, 1, 0, 0);
      poseStack.popPose();
      float f = (float)renderState.lineOriginOffset.x;
      float g = (float)renderState.lineOriginOffset.y;
      float h = (float)renderState.lineOriginOffset.z;
      VertexConsumer vertexConsumer2 = bufferSource.getBuffer(RenderType.lineStrip());
      PoseStack.Pose pose2 = poseStack.last();
      int i = 16;

      for(int j = 0; j <= 16; ++j) {
         stringVertex(f, g, h, vertexConsumer2, pose2, fraction(j, 16), fraction(j + 1, 16));
      }

      poseStack.popPose();
      super.render(renderState, poseStack, bufferSource, packedLight);
   }

   public static HumanoidArm getHoldingArm(LivingEntity player) {
      return player.getMainHandItem().getItem() instanceof FishingRodItem ? player.getMainArm() : player.getMainArm().getOpposite();
   }

   private Vec3 getPlayerHandPos(LivingEntity player, float handAngle, float partialTick) {
      int i = getHoldingArm(player) == HumanoidArm.RIGHT ? 1 : -1;
      if (this.entityRenderDispatcher.options.getCameraType().isFirstPerson() && player == Minecraft.getInstance().player) {
         double l = (double)960.0F / (double)(Integer)this.entityRenderDispatcher.options.fov().get();
         Vec3 vec3 = this.entityRenderDispatcher.camera.getNearPlane().getPointOnPlane((float)i * 0.525F, -0.1F).scale(l).yRot(handAngle * 0.5F).xRot(-handAngle * 0.7F);
         return player.getEyePosition(partialTick).add(vec3);
      } else {
         float f = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * ((float)Math.PI / 180F);
         double d = (double)Mth.sin(f);
         double e = (double)Mth.cos(f);
         float g = player.getScale();
         double h = (double)i * 0.35 * (double)g;
         double j = 0.8 * (double)g;
         float k = player.isCrouching() ? -0.1875F : 0.0F;
         return player.getEyePosition(partialTick).add(-e * h - d * j, (double)k - 0.45 * (double)g, -d * h + e * j);
      }
   }

   private static float fraction(int numerator, int denominator) {
      return (float)numerator / (float)denominator;
   }

   private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int packedLight, float x, int y, int u, int v) {
      consumer.addVertex(pose, x - 0.5F, (float)y - 0.5F, 0.0F).setColor(-1).setUv((float)u, (float)v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0F, 1.0F, 0.0F);
   }

   private static void stringVertex(float x, float y, float z, VertexConsumer consumer, PoseStack.Pose pose, float stringFraction, float nextStringFraction) {
      float f = x * stringFraction;
      float g = y * (stringFraction * stringFraction + stringFraction) * 0.5F + 0.25F;
      float h = z * stringFraction;
      float i = x * nextStringFraction - f;
      float j = y * (nextStringFraction * nextStringFraction + nextStringFraction) * 0.5F + 0.25F - g;
      float k = z * nextStringFraction - h;
      float l = Mth.sqrt(i * i + j * j + k * k);
      i /= l;
      j /= l;
      k /= l;
      consumer.addVertex(pose, f, g, h).setColor(-16777216).setNormal(pose, i, j, k);
   }

   public FishingHookRenderState createRenderState() {
      return new FishingHookRenderState();
   }

   public void extractRenderState(CustomFishingBobberEntity entity, FishingHookRenderState reusedState, float partialTick) {
      super.extractRenderState(entity, reusedState, partialTick);
      LivingEntity player = entity.getPlayerOwner();
      if (player == null) {
         reusedState.lineOriginOffset = Vec3.ZERO;
      } else {
         float f = player.getAttackAnim(partialTick);
         float g = Mth.sin(Mth.sqrt(f) * (float)Math.PI);
         Vec3 vec3 = this.getPlayerHandPos(player, g, partialTick);
         Vec3 vec32 = entity.getPosition(partialTick).add((double)0.0F, (double)0.25F, (double)0.0F);
         reusedState.lineOriginOffset = vec3.subtract(vec32);
      }
   }

   protected boolean affectedByCulling(FishingHook display) {
      return false;
   }

   static {
      RENDER_TYPE = RenderType.entityCutout(TEXTURE_LOCATION);
   }
}
