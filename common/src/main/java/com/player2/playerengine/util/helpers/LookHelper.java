package com.player2.playerengine.util.helpers;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import com.player2.playerengine.automaton.api.utils.RayTraceUtils;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.RotationUtils;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public interface LookHelper {
   static Optional<Rotation> getReach(PlayerEngineController controller, BlockPos target, Direction side) {
      IEntityContext context = controller.getBaritone().getEntityContext();
      Optional<Rotation> reachableRotation;
      if (side == null) {
         reachableRotation = RotationUtils.reachable(context.entity(), target, context.playerController().getBlockReachDistance());
      } else {
         Vec3i sideVector = side.getUnitVec3i();
         Vec3 centerOffset = new Vec3(0.5 + sideVector.getX() * 0.5, 0.5 + sideVector.getY() * 0.5, 0.5 + sideVector.getZ() * 0.5);
         Vec3 sidePoint = centerOffset.add(target.getX(), target.getY(), target.getZ());
         reachableRotation = RotationUtils.reachableOffset(context.entity(), target, sidePoint, context.playerController().getBlockReachDistance(), false);
         if (reachableRotation.isPresent()) {
            Vec3 cameraPos = context.entity().getEyePosition(1.0F);
            Vec3 vecToPlayerPos = cameraPos.subtract(sidePoint);
            double dotProduct = vecToPlayerPos.normalize().dot(new Vec3(sideVector.getX(), sideVector.getY(), sideVector.getZ()));
            if (dotProduct < 0.0) {
               return Optional.empty();
            }
         }
      }

      return reachableRotation;
   }

   static Optional<Rotation> getReach(PlayerEngineController controller, BlockPos target) {
      Debug.logInternal("Target: " + target);
      return getReach(controller, target, null);
   }

   static EntityHitResult raycast(Entity from, Entity to, double reachDistance) {
      Vec3 start = getCameraPos(from);
      Vec3 end = getCameraPos(to);
      Vec3 direction = end.subtract(start).normalize().scale(reachDistance);
      AABB box = to.getBoundingBox();
      return ProjectileUtil.getEntityHitResult(from, start, start.add(direction), box, entity -> entity.equals(to), 0.0);
   }

   static boolean seesPlayer(Entity entity, Entity player, double maxRange, Vec3 entityOffset, Vec3 playerOffset) {
      return seesPlayerOffset(entity, player, maxRange, entityOffset, playerOffset)
         || seesPlayerOffset(entity, player, maxRange, entityOffset, playerOffset.add(0.0, -1.0, 0.0));
   }

   static boolean seesPlayer(Entity entity, Entity player, double maxRange) {
      return seesPlayer(entity, player, maxRange, new Vec3(0.0, 0.0, 0.0), new Vec3(0.0, 0.0, 0.0));
   }

   static boolean cleanLineOfSight(Entity entity, Vec3 start, Vec3 end, double maxRange) {
      BlockHitResult blockHitResult = raycast(entity, start, end, maxRange);
      return blockHitResult.getType() == Type.MISS;
   }

   static boolean cleanLineOfSight(Entity entity, Vec3 end, double maxRange) {
      Vec3 start = getCameraPos(entity);
      return cleanLineOfSight(entity, start, end, maxRange);
   }

   static boolean cleanLineOfSight(PlayerEngineController controller, Vec3 end, double maxRange) {
      LivingEntity clientPlayerEntity = controller.getPlayer();
      return cleanLineOfSight(clientPlayerEntity, end, maxRange);
   }

   // $VF: Unable to simplify switch-on-enum, as the enum class was not able to be found.
   // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
   static boolean cleanLineOfSight(Entity entity, BlockPos block, double maxRange) {
      Vec3 targetPosition = WorldHelper.toVec3d(block);
      BlockHitResult hitResult = raycast(entity, getCameraPos(entity), targetPosition, maxRange);
      if (hitResult == null) {
         return true;
      } else {
         switch (hitResult.getType().ordinal()) {
            case 1:
            case 2:
            case 3:
               return false;
            default:
               throw new IncompatibleClassChangeError();
         }
      }
   }

   static Vec3 toVec3d(Rotation rotation) throws NullPointerException {
      Objects.requireNonNull(rotation, "Rotation cannot be null");
      return calcLookDirectionFromRotation(rotation);
   }

   static Vec3 calcLookDirectionFromRotation(Rotation rotation) {
      float flatZ = Mth.cos(-rotation.getYaw() * (float) (Math.PI / 180.0) - (float) Math.PI);
      float flatX = Mth.sin(-rotation.getYaw() * (float) (Math.PI / 180.0) - (float) Math.PI);
      float pitchBase = -Mth.cos(-rotation.getPitch() * (float) (Math.PI / 180.0));
      float pitchHeight = Mth.sin(-rotation.getPitch() * (float) (Math.PI / 180.0));
      return new Vec3(flatX * pitchBase, pitchHeight, flatZ * pitchBase);
   }

   static BlockHitResult raycast(Entity entity, Vec3 start, Vec3 end, double maxRange) {
      Vec3 direction = end.subtract(start);
      if (direction.lengthSqr() > maxRange * maxRange) {
         direction = direction.normalize().scale(maxRange);
         end = start.add(direction);
      }

      Level world = entity.level();
      ClipContext context = new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, entity);
      return world.clip(context);
   }

   static BlockHitResult raycast(Entity entity, Vec3 end, double maxRange) {
      Vec3 start = getCameraPos(entity);
      return raycast(entity, start, end, maxRange);
   }

   static Rotation getLookRotation(Entity entity) {
      float pitch = entity.getXRot();
      float yaw = entity.getYRot();
      return new Rotation(yaw, pitch);
   }

   static Rotation getLookRotation(PlayerEngineController mod) {
      LivingEntity clientPlayerEntity = mod.getEntity();
      return clientPlayerEntity == null ? new Rotation(0.0F, 0.0F) : getLookRotation(clientPlayerEntity);
   }

   static Vec3 getCameraPos(Entity entity) {
      boolean isPlayerSneaking = entity instanceof LivingEntity && entity.isShiftKeyDown();
      return isPlayerSneaking ? RayTraceUtils.inferSneakingEyePosition(entity) : entity.getEyePosition(1.0F);
   }

   static Vec3 getCameraPos(PlayerEngineController mod) {
      IEntityContext playerContext = mod.getBaritone().getEntityContext();
      return playerContext.entity().getEyePosition(1.0F);
   }

   static double getLookCloseness(Entity entity, Vec3 pos) {
      Vec3 rotDirection = entity.getForward();
      Vec3 lookStart = getCameraPos(entity);
      Vec3 deltaToPos = pos.subtract(lookStart);
      Vec3 deltaDirection = deltaToPos.normalize();
      return rotDirection.dot(deltaDirection);
   }

   private static boolean seesPlayerOffset(Entity entity, Entity player, double maxRange, Vec3 offsetEntity, Vec3 offsetPlayer) {
      Vec3 entityCameraPos = getCameraPos(entity).add(offsetEntity);
      Vec3 playerCameraPos = getCameraPos(player).add(offsetPlayer);
      return cleanLineOfSight(entity, entityCameraPos, playerCameraPos, maxRange);
   }

   static void randomOrientation(PlayerEngineController mod) {
      float randomRotationX = (float)(Math.random() * 360.0);
      float randomRotationY = -90.0F + (float)(Math.random() * 180.0);
      Rotation r = new Rotation(randomRotationX, randomRotationY);
      lookAt(mod, r);
   }

   static boolean isLookingAt(PlayerEngineController mod, Rotation rotation) {
      return rotation.isReallyCloseTo(getLookRotation(mod));
   }

   static boolean isLookingAt(PlayerEngineController mod, BlockPos pos) {
      return mod.getBaritone().getEntityContext().isLookingAt(pos);
   }

   static boolean isLookingAt(Entity entity, Vec3 toLookAt, double angleThreshold) {
      Vec3 head = entity.position().add(new Vec3(0.0, entity.getEyeHeight(), 0.0));
      Rotation rotation = new Rotation(entity.getYRot(), entity.getXRot());
      Vec3 look = calcLookDirectionFromRotation(rotation);
      Vec3 targetLook = toLookAt.subtract(head).normalize();
      double dot = look.dot(targetLook);
      double angle = Math.toDegrees(Math.acos(dot));
      return Math.abs(angle) < angleThreshold;
   }

   static void lookAt(PlayerEngineController mod, Rotation rotation, boolean withBaritone) {
      if (withBaritone) {
         mod.getBaritone().getLookBehavior().updateTarget(rotation, true);
      }

      mod.getPlayer().setYRot(rotation.getYaw());
      mod.getPlayer().setXRot(rotation.getPitch());
   }

   static void lookAt(PlayerEngineController mod, Rotation rotation) {
      mod.getBaritone().getLookBehavior().updateTarget(rotation, true);
      LivingEntity player = mod.getBaritone().getEntityContext().entity();
      player.setYRot(rotation.getYaw());
      player.setXRot(rotation.getPitch());
   }

   static void lookAt(PlayerEngineController mod, Vec3 toLook, boolean withBaritone) {
      if (mod != null && toLook != null) {
         Rotation targetRotation = getLookRotation(mod, toLook);
         lookAt(mod, targetRotation, withBaritone);
      } else {
         throw new IllegalArgumentException("mod and toLook cannot be null");
      }
   }

   static void lookAt(PlayerEngineController mod, Vec3 toLook) {
      if (mod != null && toLook != null) {
         Rotation targetRotation = getLookRotation(mod, toLook);
         lookAt(mod, targetRotation, true);
      } else {
         throw new IllegalArgumentException("mod and toLook cannot be null");
      }
   }

   static void lookAt(PlayerEngineController mod, BlockPos toLook, Direction side, boolean withBaritone) {
      double centerX = toLook.getX() + 0.5;
      double centerY = toLook.getY() + 0.5;
      double centerZ = toLook.getZ() + 0.5;
      if (side != null) {
         double offsetX = side.getUnitVec3i().getX() * 0.5;
         double offsetY = side.getUnitVec3i().getY() * 0.5;
         double offsetZ = side.getUnitVec3i().getZ() * 0.5;
         centerX += offsetX;
         centerY += offsetY;
         centerZ += offsetZ;
      }

      Vec3 target = new Vec3(centerX, centerY, centerZ);
      lookAt(mod, target, withBaritone);
   }

   static void lookAt(PlayerEngineController mod, BlockPos toLook, Direction side) {
      double centerX = toLook.getX() + 0.5;
      double centerY = toLook.getY() + 0.5;
      double centerZ = toLook.getZ() + 0.5;
      if (side != null) {
         double offsetX = side.getUnitVec3i().getX() * 0.5;
         double offsetY = side.getUnitVec3i().getY() * 0.5;
         double offsetZ = side.getUnitVec3i().getZ() * 0.5;
         centerX += offsetX;
         centerY += offsetY;
         centerZ += offsetZ;
      }

      Vec3 target = new Vec3(centerX, centerY, centerZ);
      lookAt(mod, target, true);
   }

   static void lookAt(PlayerEngineController mod, BlockPos toLook, boolean withBaritone) {
      lookAt(mod, toLook, null, withBaritone);
   }

   static void lookAt(PlayerEngineController mod, BlockPos toLook) {
      lookAt(mod, toLook, null, true);
   }

   static Rotation getLookRotation(PlayerEngineController mod, Vec3 toLook) {
      Vec3 playerHead = mod.getBaritone().getEntityContext().headPos();
      Rotation playerRotations = mod.getBaritone().getEntityContext().entityRotations();
      return RotationUtils.calcRotationFromVec3d(playerHead, toLook, playerRotations);
   }

   static Rotation getLookRotation(PlayerEngineController mod, BlockPos toLook) {
      Vec3 targetPosition = WorldHelper.toVec3d(toLook);
      return getLookRotation(mod, targetPosition);
   }
}
