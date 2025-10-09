package com.player2.playerengine.chains;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.tasks.misc.EquipArmorTask;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.tasks.base.TaskRunner;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.PlayerEngineController;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ForceEquipArmorChain extends SingleTaskChain {
    private ItemTarget[] toEquip = new ItemTarget[0];
    public static final Logger LOGGER = LogManager.getLogger();

    public static final ItemTarget[][] ARMOR_BY_TIER_DESC = Arrays.stream(new Item[][] {
            ItemHelper.NETHERITE_ARMORS,
            ItemHelper.DIAMOND_ARMORS,
            ItemHelper.IRON_ARMORS,
            ItemHelper.CHAINMAIL_ARMORS,
            ItemHelper.GOLDEN_ARMORS,
            ItemHelper.LEATHER_ARMORS
    })
            .map(ItemTarget::of)
            .toArray(ItemTarget[][]::new);

    private ItemTarget[] computeBetterArmorToEquip(PlayerEngineController controller) {
        int slots = ARMOR_BY_TIER_DESC[0].length; // 4 = len([helmet, chestplate, leggings, boots])
        List<ItemTarget> upgrades = new java.util.ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            // find best item per slot:
            Optional<ItemTarget> ma = Optional.empty();
            for (int tier = 0; tier < ARMOR_BY_TIER_DESC.length; tier++) {
                ItemTarget equippedCandidate = ARMOR_BY_TIER_DESC[tier][slot];
                if (StorageHelper.isArmorEquipped(controller, equippedCandidate.getMatches())
                        || controller.getItemStorage().hasItem(equippedCandidate.getMatches())) {
                    ma = Optional.of(equippedCandidate);
                    break;
                }
            }
            // only add if best item is not equipped already:
            if (ma.isPresent() && !StorageHelper.isArmorEquipped(controller, ma.get().getMatches())) {
                upgrades.add(ma.get());
            }
        }
        return upgrades.toArray(new ItemTarget[0]);
    }

    public ForceEquipArmorChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    public float getPriority() {
        LOGGER.info("getPriority force armor chain");
        this.toEquip = computeBetterArmorToEquip(this.controller);
        if (this.toEquip.length > 0) {
            LOGGER.info("Better armor, equipping");
            this.setTask(new EquipArmorTask(this.toEquip));
            return 100.0f;
        }
        return 0.0f;
    }

    @Override
    protected void onTaskFinish(PlayerEngineController controller) {
    }

    @Override
    public String getName() {
        return "ForceEquipArmorChain";
    }

    @Override
    public boolean isActive() {
        return true;
    }
}
