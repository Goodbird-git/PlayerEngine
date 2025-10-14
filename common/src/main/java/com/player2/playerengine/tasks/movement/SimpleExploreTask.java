package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.PlayerEngineController;

import net.minecraft.world.phys.Vec3;

public class SimpleExploreTask extends Task {
    protected Vec3 origin;

    public SimpleExploreTask() {
    }

    @Override
    protected Task onTick() {
        // TODO Auto-generated method stub
        PlayerEngineController mod = this.controller;
        if (!mod.getBaritone().getExploreProcess().isActive()) {
            mod.getBaritone().getExploreProcess().explore((int) this.origin.x(), (int) this.origin.z());
        }
        return null;
    }

    @Override
    protected boolean isEqual(Task var1) {
        // TODO Auto-generated method stub
        return var1 instanceof SimpleExploreTask
                && ((((SimpleExploreTask) var1).origin == null && origin == null)
                        || ((SimpleExploreTask) var1).origin.equals(origin));
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        PlayerEngineController mod = this.controller;
        this.origin = mod.getPlayer().position();
    }

    @Override
    protected void onStop(Task var1) {
        // TODO Auto-generated method stub

    }

    @Override
    protected String toDebugString() {
        // TODO Auto-generated method stub
        return "exploring";
    }

}