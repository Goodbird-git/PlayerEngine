package adris.altoclef.tasks.movement;

import adris.altoclef.Playground;
import adris.altoclef.tasksystem.Task;

public class IdleTask extends Task {
   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      
      Playground.IDLE_TEST_TICK_FUNCTION(this.controller);
      return null;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      return false;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof IdleTask;
   }

   @Override
   protected String toDebugString() {
      return "Idle";
   }
}
