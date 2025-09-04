package adris.altoclef.player2api.status;

import adris.altoclef.AltoClefController;
import net.minecraft.world.entity.LivingEntity;

public class AgentStatus extends ObjectStatus {
   public static AgentStatus fromMod(AltoClefController mod) {
      LivingEntity player = mod.getPlayer();
      return (AgentStatus)new AgentStatus()
         .add("health", String.format("%.2f/20", player.getHealth()))
         .add("food", String.format("%.2f/20", (float)mod.getBaritone().getEntityContext().hungerManager().getFoodLevel()))
         .add("saturation", String.format("%.2f/20", mod.getBaritone().getEntityContext().hungerManager().getSaturationLevel()))
         .add("inventory", StatusUtils.getInventoryString(mod))
         .add("taskStatus", StatusUtils.getTaskStatusString(mod))
         .add("oxygenLevel", StatusUtils.getOxygenString(mod))
         .add("armor", StatusUtils.getEquippedArmorStatusString(mod))
         .add("gamemode", StatusUtils.getGamemodeString(mod));
         // .add("taskTree", StatusUtils.getTaskTree(mod));
   }
}
