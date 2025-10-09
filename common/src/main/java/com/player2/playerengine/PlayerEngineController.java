package com.player2.playerengine;

import com.player2.playerengine.automaton.AdditionalBaritoneSettings;
import com.player2.playerengine.chains.FoodChain;
import com.player2.playerengine.chains.ForceEquipArmorChain;
import com.player2.playerengine.chains.MLGBucketFallChain;
import com.player2.playerengine.chains.MobDefenseChain;
import com.player2.playerengine.chains.PlayerDefenseChain;
import com.player2.playerengine.chains.PlayerInteractionFixChain;
import com.player2.playerengine.chains.PreEquipItemChain;
import com.player2.playerengine.chains.UnstuckChain;
import com.player2.playerengine.chains.UserTaskChain;
import com.player2.playerengine.chains.WorldSurvivalChain;
import com.player2.playerengine.commands.BlockScanner;
import com.player2.playerengine.commands.base.CommandExecutor;
import com.player2.playerengine.control.InputControls;
import com.player2.playerengine.control.PlayerExtraController;
import com.player2.playerengine.control.SlotHandler;

import com.player2.playerengine.player2api.manager.ConversationManager;
import com.player2.playerengine.player2api.AIPersistantData;
import com.player2.playerengine.player2api.Player2APIService;

import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TaskRunner;
import com.player2.playerengine.trackers.CraftingRecipeTracker;
import com.player2.playerengine.trackers.EntityStuckTracker;
import com.player2.playerengine.trackers.EntityTracker;
import com.player2.playerengine.trackers.MiscBlockTracker;
import com.player2.playerengine.trackers.SimpleChunkTracker;
import com.player2.playerengine.trackers.TrackerManager;
import com.player2.playerengine.trackers.UserBlockRangeTracker;
import com.player2.playerengine.trackers.storage.ContainerSubTracker;
import com.player2.playerengine.trackers.storage.ItemStorageTracker;
import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import com.player2.playerengine.automaton.api.utils.IInteractionController;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.Playground;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

public class PlayerEngineController {
   private final IBaritone baritone;
   private AIPersistantData aiPersistantData;
   private Player2APIService player2apiService;
   private final IEntityContext ctx;
   private CommandExecutor commandExecutor;
   private TaskRunner taskRunner;
   private TrackerManager trackerManager;
   private BotBehaviour botBehaviour;
   private UserTaskChain userTaskChain;
   private FoodChain foodChain;
   private ForceEquipArmorChain forceEquipArmorChain;
   private MobDefenseChain mobDefenseChain;
   private MLGBucketFallChain mlgBucketChain;
   private ItemStorageTracker storageTracker;
   private ContainerSubTracker containerSubTracker;
   private EntityTracker entityTracker;
   private BlockScanner blockScanner;
   private SimpleChunkTracker chunkTracker;
   private MiscBlockTracker miscBlockTracker;
   private CraftingRecipeTracker craftingRecipeTracker;
   private EntityStuckTracker entityStuckTracker;
   private UserBlockRangeTracker userBlockRangeTracker;
   private InputControls inputControls;
   private SlotHandler slotHandler;
   private PlayerExtraController extraController;
   private PlayerEngineSettings settings;
   private boolean paused = false;
   private Task storedTask;
   public boolean isStopping = false;
   private Player owner;

   public PlayerEngineController(IBaritone baritone, Character character, String player2GameId) {
      this.baritone = baritone;
      this.ctx = baritone.getEntityContext();
      this.commandExecutor = new CommandExecutor(this);
      this.taskRunner = new TaskRunner(this);
      this.trackerManager = new TrackerManager(this);
      this.userTaskChain = new UserTaskChain(this.taskRunner);
      this.mobDefenseChain = new MobDefenseChain(this.taskRunner);
      new PlayerInteractionFixChain(this.taskRunner);
      this.mlgBucketChain = new MLGBucketFallChain(this.taskRunner);
      new UnstuckChain(this.taskRunner);
      new PreEquipItemChain(this.taskRunner);
      new WorldSurvivalChain(this.taskRunner);
      this.foodChain = new FoodChain(this.taskRunner);
      this.forceEquipArmorChain = new ForceEquipArmorChain(this.taskRunner);
      new PlayerDefenseChain(this.taskRunner);
      this.storageTracker = new ItemStorageTracker(this, this.trackerManager,
            container -> this.containerSubTracker = container);
      this.entityTracker = new EntityTracker(this.trackerManager);
      this.blockScanner = new BlockScanner(this);
      this.chunkTracker = new SimpleChunkTracker(this);
      this.miscBlockTracker = new MiscBlockTracker(this);
      this.craftingRecipeTracker = new CraftingRecipeTracker(this.trackerManager);
      this.entityStuckTracker = new EntityStuckTracker(this.trackerManager);
      this.userBlockRangeTracker = new UserBlockRangeTracker(this.trackerManager);
      this.inputControls = new InputControls(this);
      this.slotHandler = new SlotHandler(this);
      this.extraController = new PlayerExtraController(this);
      this.initializeBaritoneSettings();
      this.botBehaviour = new BotBehaviour(this);
      this.initializeCommands();
      PlayerEngineSettings.load(
            newSettings -> {
               this.settings = newSettings;
               List<Item> baritoneCanPlace = Arrays.stream(this.settings.getThrowawayItems(this, true)).toList();
               this.getBaritoneSettings().acceptableThrowawayItems.get().addAll(baritoneCanPlace);
               if ((!this.getUserTaskChain().isActive() || this.getUserTaskChain().isRunningIdleTask())
                     && this.getModSettings().shouldRunIdleCommandWhenNotActive()) {
                  this.getUserTaskChain().signalNextTaskToBeIdleTask();
                  this.getCommandExecutor().executeWithPrefix(this.getModSettings().getIdleCommand());
               }

               this.getExtraBaritoneSettings().avoidBlockBreak(this.userBlockRangeTracker::isNearUserTrackedBlock);
               this.getExtraBaritoneSettings().avoidBlockPlace(this.entityStuckTracker::isBlockedByEntity);
            });
      Playground.IDLE_TEST_INIT_FUNCTION(this);

      // AI setup: (should be at end to ensure as many things are not null as
      // possible)
      ConversationManager.getOrCreateEventQueueData(this);
      this.aiPersistantData = new AIPersistantData(this, character);
      this.player2apiService = new Player2APIService(this, player2GameId);
   }

   public void serverTick() {
      this.inputControls.onTickPre();
      this.storageTracker.setDirty();
      this.miscBlockTracker.tick();
      this.trackerManager.tick();
      this.blockScanner.tick();
      this.taskRunner.tick();
      this.inputControls.onTickPost();
      this.baritone.serverTick();
      this.player2apiService.trySendHeartbeat();
   }

   static {
      TickEvent.SERVER_POST.register(PlayerEngineController::staticServerTick);
   }

   public static void staticServerTick(MinecraftServer server) {
      ConversationManager.injectOnTick(server);
   }

   public void stop() {
      this.getUserTaskChain().cancel(this);
      if (this.taskRunner.getCurrentTaskChain() != null) {
         this.taskRunner.getCurrentTaskChain().stop();
      }

      this.getTaskRunner().disable();
      this.getBaritone().getPathingBehavior().forceCancel();
      this.getBaritone().getInputOverrideHandler().clearAllKeys();
   }

   private void initializeBaritoneSettings() {
      this.getExtraBaritoneSettings().canWalkOnEndPortal(false);
      this.getExtraBaritoneSettings().avoidBlockPlace(this.entityStuckTracker::isBlockedByEntity);
      this.getExtraBaritoneSettings().avoidBlockBreak(this.userBlockRangeTracker::isNearUserTrackedBlock);
      this.getBaritoneSettings().freeLook.set(false);
      this.getBaritoneSettings().overshootTraverse.set(true);
      this.getBaritoneSettings().allowOvershootDiagonalDescend.set(true);
      this.getBaritoneSettings().allowInventory.set(true);
      this.getBaritoneSettings().allowParkour.set(false);
      this.getBaritoneSettings().allowParkourAscend.set(false);
      this.getBaritoneSettings().allowParkourPlace.set(false);
      this.getBaritoneSettings().allowDiagonalDescend.set(false);
      this.getBaritoneSettings().allowDiagonalAscend.set(false);
      this.getBaritoneSettings().fadePath.set(true);
      this.getBaritoneSettings().mineScanDroppedItems.set(false);
      this.getBaritoneSettings().mineDropLoiterDurationMSThanksLouca.set(0L);
      this.getExtraBaritoneSettings().configurePlaceBucketButDontFall(true);
      this.getBaritoneSettings().randomLooking.set(0.0);
      this.getBaritoneSettings().randomLooking113.set(0.0);
      this.getBaritoneSettings().failureTimeoutMS.reset();
      this.getBaritoneSettings().planAheadFailureTimeoutMS.reset();
      this.getBaritoneSettings().movementTimeoutTicks.reset();
   }

   private void initializeCommands() {
      try {
         PlayerEngineCommands.init(this);
      } catch (Exception var2) {
         var2.printStackTrace();
      }
   }

   public void runUserTask(Task task, Runnable onFinish) {
      this.userTaskChain.runTask(this, task, onFinish);
   }

   public void runUserTask(Task task) {
      this.runUserTask(task, () -> {
      });
   }

   public void cancelUserTask() {
      this.userTaskChain.cancel(this);
   }

   public CommandExecutor getCommandExecutor() {
      return this.commandExecutor;
   }

   public LivingEntity getEntity() {
      return this.ctx.entity();
   }

   public ServerLevel getWorld() {
      return this.ctx.world();
   }

   public IInteractionController getInteractionManager() {
      return this.ctx.playerController();
   }

   public IBaritone getBaritone() {
      return this.baritone;
   }

   public com.player2.playerengine.automaton.api.Settings getBaritoneSettings() {
      return this.baritone.settings();
   }

   public AdditionalBaritoneSettings getExtraBaritoneSettings() {
      return ((Baritone) this.baritone).getExtraBaritoneSettings();
   }

   public TaskRunner getTaskRunner() {
      return this.taskRunner;
   }

   public UserTaskChain getUserTaskChain() {
      return this.userTaskChain;
   }

   public BotBehaviour getBehaviour() {
      return this.botBehaviour;
   }

   public boolean isPaused() {
      return this.paused;
   }

   public void setPaused(boolean pausing) {
      this.paused = pausing;
   }

   public Task getStoredTask() {
      return this.storedTask;
   }

   public void setStoredTask(Task currentTask) {
      this.storedTask = currentTask;
   }

   public ItemStorageTracker getItemStorage() {
      return this.storageTracker;
   }

   public EntityTracker getEntityTracker() {
      return this.entityTracker;
   }

   public CraftingRecipeTracker getCraftingRecipeTracker() {
      return this.craftingRecipeTracker;
   }

   public BlockScanner getBlockScanner() {
      return this.blockScanner;
   }

   public SimpleChunkTracker getChunkTracker() {
      return this.chunkTracker;
   }

   public MiscBlockTracker getMiscBlockTracker() {
      return this.miscBlockTracker;
   }

   public PlayerEngineSettings getModSettings() {
      return this.settings;
   }

   public FoodChain getFoodChain() {
      return this.foodChain;
   }

   public MobDefenseChain getMobDefenseChain() {
      return this.mobDefenseChain;
   }

   public MLGBucketFallChain getMLGBucketChain() {
      return this.mlgBucketChain;
   }

   public void log(String message) {
      Debug.logMessage(message);
   }

   public void logWarning(String message) {
      Debug.logWarning(message);
   }

   public static boolean inGame() {
      return true;
   }

   public LivingEntity getPlayer() {
      return this.ctx.entity();
   }

   public InputControls getInputControls() {
      return this.inputControls;
   }

   public SlotHandler getSlotHandler() {
      return this.slotHandler;
   }

   public LivingEntityInventory getInventory() {
      return this.getBaritone().getEntityContext().inventory();
   }

   public PlayerExtraController getControllerExtras() {
      return this.extraController;
   }

   public void setChatClefEnabled(boolean enabled) {
      ConversationManager.getOrCreateEventQueueData(this).setEnabled(enabled);

      if (!enabled) {
         this.getUserTaskChain().cancel(this);
         this.getTaskRunner().disable();
      }
   }

   public void logCharacterMessage(String message, Character character, boolean isPublic) {
      int maxLength = 256;
      int start = 0;

      while (start < message.length()) {
         int end = Math.min(start + maxLength, message.length());
         String chunk = message.substring(start, end);
         if (chunk.length() > 0 && !chunk.isBlank()) {
            Debug.logCharacterMessage(chunk, character, isPublic);
         }

         start = end;
      }
   }

   public Player getOwner() {
      return this.owner;
   }

   public void setOwner(Player owner) {
      this.owner = owner;
      aiPersistantData.updateSystemPrompt();
   }

   public boolean isOwner(UUID playerToCheck) {
      return playerToCheck.equals(owner.getUUID());
   }

   public AIPersistantData getAIPersistantData() {
      return this.aiPersistantData;
   }

   public Player2APIService getPlayer2APIService() {
      return this.player2apiService;
   }

   public String getOwnerUsername() {
      if (getOwner() == null) {
         return "UNKNOWN OWNER";
      }
      return getOwner().getName().getString();
   }

   public Optional<ServerPlayer> getClosestPlayer() {
      return this.getWorld().players().stream().sorted((a, b) -> {
         float adist = a.distanceTo(this.getEntity());
         float bdist = b.distanceTo(this.getEntity());
         return Float.compare(adist, bdist);
      }).findFirst();
   }
}
