package adris.altoclef.player2api.status;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.ItemHelper;
import baritone.api.entity.IAutomatone;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

public class StatusUtils {
   public static String getInventoryString(AltoClefController mod) {
      Map<String, Integer> counts = new HashMap<>();

      for (int i = 0; i < mod.getBaritone().getEntityContext().inventory().getContainerSize(); i++) {
         ItemStack stack = mod.getBaritone().getEntityContext().inventory().getItem(i);
         if (!stack.isEmpty()) {
            String name = ItemHelper.stripItemName(stack.getItem());
            counts.put(name, counts.getOrDefault(name, 0) + stack.getCount());
         }
      }

      ObjectStatus status = new ObjectStatus();

      for (Entry<String, Integer> entry : counts.entrySet()) {
         status.add(entry.getKey(), entry.getValue().toString());
      }

      return status.toString();
   }

   public static String getDimensionString(AltoClefController mod) {
      return mod.getWorld().dimension().location().toString().replace("minecraft:", "");
   }

   public static String getWeatherString(AltoClefController mod) {
      boolean isRaining = mod.getWorld().isRaining();
      boolean isThundering = mod.getWorld().isThundering();
      ObjectStatus status = new ObjectStatus().add("isRaining", String.valueOf(isRaining)).add("isThundering",
            String.valueOf(isThundering));
      return status.toString();
   }

   public static String getSpawnPosString(AltoClefController mod) {
      BlockPos spawnPos = mod.getWorld().getSharedSpawnPos();
      return String.format("(%d, %d, %d)", spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
   }

   public static String getTaskStatusString(AltoClefController mod) {
      String noTask = "No tasks currently running.";
      List<Task> tasks = mod.getUserTaskChain().getTasks();
      // ignore lookATOwner task
      return tasks.isEmpty() ? noTask
            : tasks.get(0).toString().contains("LookAtOwner") ? noTask : tasks.get(0).toString();
   }

   public static String getNearbyBlocksString(AltoClefController mod) {
      int radius = 12;
      BlockPos center = mod.getPlayer().blockPosition();
      Map<String, Integer> blockCounts = new HashMap<>();

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               BlockPos pos = center.offset(dx, dy, dz);
               String blockName = mod.getWorld().getBlockState(pos).getBlock().getDescriptionId()
                     .replace("block.minecraft.", "");
               if (!blockName.equals("air")) {
                  blockCounts.put(blockName, blockCounts.getOrDefault(blockName, 0) + 1);
               }
            }
         }
      }

      ObjectStatus status = new ObjectStatus();

      for (Entry<String, Integer> entry : blockCounts.entrySet()) {
         status.add(entry.getKey(), entry.getValue().toString());
      }

      return status.toString();
   }

   public static String getOxygenString(AltoClefController mod) {
      return String.format("%s/300", mod.getPlayer().getAirSupply());
   }

   public static String getNearbyHostileMobs(AltoClefController mod) {
      int radius = 32;
      List<String> descriptions = new ArrayList<>();

      for (Entity entity : mod.getWorld().getAllEntities()) {
         if (entity instanceof Monster && entity.distanceTo(mod.getPlayer()) < radius) {
            String type = entity.getType().getDescriptionId();
            String niceName = type.replace("entity.minecraft.", "");
            String position = entity.position().align(EnumSet.allOf(Axis.class)).toString();
            descriptions.add(niceName + " at " + position);
         }
      }

      return descriptions.isEmpty()
            ? String.format("no nearby hostile mobs within %d", radius)
            : "[" + String.join(",", descriptions.stream().map(s -> "\"" + s + "\"").toArray(String[]::new)) + "]";
   }

   public static String getEquippedArmorStatusString(AltoClefController mod) {
      LivingEntity player = mod.getPlayer();
      ObjectStatus status = new ObjectStatus();
      ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
      ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
      ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
      ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
      ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
      status.add("helmet",
            !head.isEmpty() && head.getItem() instanceof ArmorItem
                  ? head.getItem().getDescriptionId().replace("item.minecraft.", "")
                  : "none");
      status.add(
            "chestplate",
            !chest.isEmpty() && chest.getItem() instanceof ArmorItem
                  ? chest.getItem().getDescriptionId().replace("item.minecraft.", "")
                  : "none");
      status.add("leggings",
            !legs.isEmpty() && legs.getItem() instanceof ArmorItem
                  ? legs.getItem().getDescriptionId().replace("item.minecraft.", "")
                  : "none");
      status.add("boots",
            !feet.isEmpty() && feet.getItem() instanceof ArmorItem
                  ? feet.getItem().getDescriptionId().replace("item.minecraft.", "")
                  : "none");
      status.add(
            "offhand_shield",
            !offhand.isEmpty() && offhand.getItem() instanceof ShieldItem
                  ? offhand.getItem().getDescriptionId().replace("item.minecraft.", "")
                  : "none");
      return status.toString();
   }

   public static String getNearbyPlayers(AltoClefController mod) {
      List<String> descriptions = new ArrayList<>();

      for (Entity entity : mod.getEntityTracker().getCloseEntities()) {
         if (entity instanceof Player player && entity.distanceTo(mod.getPlayer()) < 32.0F) {
            String username = player.getName().getString();
            String position = entity.position().align(EnumSet.allOf(Axis.class)).toString();
            descriptions.add(username + " at " + position);
         }
      }

      return descriptions.isEmpty()
            ? String.format("no nearby users within %d", 32)
            : "[" + String.join(",", descriptions.stream().map(s -> "\"" + s + "\"").toArray(String[]::new)) + "]";
   }

   public static String getNearbyNPCs(AltoClefController mod) {
      List<String> descriptions = new ArrayList<>();

      for (Entity entity : mod.getEntityTracker().getCloseEntities()) {
         if (entity instanceof IAutomatone && entity.distanceTo(mod.getPlayer()) < 32.0F) {
            String username = entity.getDisplayName().getString();
            if (!Objects.equals(username, mod.getPlayer().getDisplayName().getString())) {
               String position = entity.position().align(EnumSet.allOf(Axis.class)).toString();
               descriptions.add(username + " at " + position);
            }
         }
      }

      return descriptions.isEmpty()
            ? String.format("no nearby npcs within %d", 32)
            : "[" + String.join(",", descriptions.stream().map(s -> "\"" + s + "\"").toArray(String[]::new)) + "]";
   }

   public static float getUserNameDistance(AltoClefController mod, String targetUsername) {
      for (Player player : mod.getWorld().players()) {
         String username = player.getName().getString();
         if (username.equals(targetUsername)) {
            return player.distanceTo(mod.getPlayer());
         }
      }

      return Float.MAX_VALUE;
   }

   public static String getDifficulty(AltoClefController mod) {
      return mod.getWorld().getDifficulty().toString();
   }

   public static String getTimeString(AltoClefController mod) {
      ObjectStatus status = new ObjectStatus();
      status.add("isDay", Boolean.toString(mod.getWorld().isDay()));
      status.add("timeOfDay", String.format("%d/24,000", mod.getWorld().getDayTime() % 24000L));
      return status.toString();
   }

   public static String getGamemodeString(AltoClefController mod) {
      return mod.getInteractionManager().getGameType().isCreative() ? "creative" : "survival";
   }

   public static String getTaskTree(AltoClefController mod) {
      Task task = mod.getUserTaskChain().getCurrentTask();
      return task == null ? "Task tree is empty" : task.getTaskTree();
   }

   public static float getDistanceToUUID(AltoClefController mod, UUID target) {
      // for (Player player : mod.getWorld().players()) {
      // if (player.getUUID().equals(target)) {
      // return player.distanceTo(mod.getPlayer());
      // }
      // }
      for (Entity entity : mod.getWorld().getAllEntities()) {
         if (entity.getUUID().equals(target)) {
            return entity.distanceTo(mod.getPlayer());
         }
      }

      return Float.MAX_VALUE;
   }

   public static float getDistanceToUsername(AltoClefController mod, String username) {
      return mod.getWorld().players().stream()
            .filter(p -> p.getName().getString().equals(username))
            .findFirst()
            .map(p -> p.distanceTo(mod.getPlayer()))
            .orElse(Float.MAX_VALUE);
   }
}
