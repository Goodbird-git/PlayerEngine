package com.player2.playerengine.tasks.misc;

import com.player2.playerengine.tasks.base.Task;
import java.util.Arrays;
import net.minecraft.world.item.Item;

public class EatFoodTask extends Task {
    Item foodItem;
    boolean hasAte;
    public EatFoodTask(Item foodItem) {
        this.foodItem = foodItem;
        this.hasAte = false;
    }

    @Override
    protected void onStart() {

    }

    @Override
    protected Task onTick() {
        if(this.controller.getBaritone().getEntityContext().hungerManager().getFoodLevel() >= 20){
            this.fail("Hunger already at 20 (full), cannot eat.");
            hasAte = true;
            return null;
        }
        if (this.controller.getSlotHandler().forceEquipItem(foodItem)) {
            this.controller.getBaritone().getEntityContext().hungerManager().eat(foodItem);
            this.controller.getInventory().removeItem(this.controller.getInventory().selectedSlot, 1);
            hasAte = true;
        }
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    @Override
    public boolean isFinished() {
        return hasAte;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EatFoodTask task ? true : false;
    }

    @Override
    protected String toDebugString() {
        return "Eating food: " + foodItem.toString();
    }
}