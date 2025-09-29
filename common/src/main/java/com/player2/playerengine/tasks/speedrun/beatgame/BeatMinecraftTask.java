package com.player2.playerengine.tasks.speedrun.beatgame;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.commands.BlockScanner;
import com.player2.playerengine.multiversion.blockpos.BlockPosVer;
import com.player2.playerengine.tasks.CraftInInventoryTask;
import com.player2.playerengine.tasks.DoToClosestBlockTask;
import com.player2.playerengine.tasks.GetRidOfExtraWaterBucketTask;
import com.player2.playerengine.tasks.InteractWithBlockTask;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.SafeNetherPortalTask;
import com.player2.playerengine.tasks.construction.DestroyBlockTask;
import com.player2.playerengine.tasks.construction.PlaceBlockNearbyTask;
import com.player2.playerengine.tasks.construction.PlaceObsidianBucketTask;
import com.player2.playerengine.tasks.container.CraftInTableTask;
import com.player2.playerengine.tasks.container.LootContainerTask;
import com.player2.playerengine.tasks.container.SmeltInFurnaceTask;
import com.player2.playerengine.tasks.container.SmeltInSmokerTask;
import com.player2.playerengine.tasks.misc.EquipArmorTask;
import com.player2.playerengine.tasks.misc.PlaceBedAndSetSpawnTask;
import com.player2.playerengine.tasks.misc.SleepThroughNightTask;
import com.player2.playerengine.tasks.movement.DefaultGoToDimensionTask;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.tasks.movement.GetToXZTask;
import com.player2.playerengine.tasks.movement.GetWithinRangeOfBlockTask;
import com.player2.playerengine.tasks.movement.GoToStrongholdPortalTask;
import com.player2.playerengine.tasks.movement.PickupDroppedItemTask;
import com.player2.playerengine.tasks.movement.SearchChunkForBlockTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.tasks.resources.CollectBlazeRodsTask;
import com.player2.playerengine.tasks.resources.CollectBucketLiquidTask;
import com.player2.playerengine.tasks.resources.CollectFoodTask;
import com.player2.playerengine.tasks.resources.CollectMeatTask;
import com.player2.playerengine.tasks.resources.GetBuildingMaterialsTask;
import com.player2.playerengine.tasks.resources.KillEndermanTask;
import com.player2.playerengine.tasks.resources.MineAndCollectTask;
import com.player2.playerengine.tasks.resources.TradeWithPiglinsTask;
import com.player2.playerengine.tasks.speedrun.BeatMinecraftConfig;
import com.player2.playerengine.tasks.speedrun.DragonBreathTracker;
import com.player2.playerengine.tasks.speedrun.KillEnderDragonTask;
import com.player2.playerengine.tasks.speedrun.KillEnderDragonWithBedsTask;
import com.player2.playerengine.tasks.speedrun.beatgame.prioritytask.prioritycalculators.CollectFoodPriorityCalculator;
import com.player2.playerengine.tasks.speedrun.beatgame.prioritytask.prioritycalculators.DistanceItemPriorityCalculator;
import com.player2.playerengine.tasks.speedrun.beatgame.prioritytask.prioritycalculators.StaticItemPriorityCalculator;
import com.player2.playerengine.tasks.speedrun.beatgame.prioritytask.tasks.ActionPriorityTask;
import com.player2.playerengine.tasks.speedrun.beatgame.prioritytask.tasks.CraftItemPriorityTask;
import com.player2.playerengine.tasks.speedrun.beatgame.prioritytask.tasks.MineBlockPriorityTask;
import com.player2.playerengine.tasks.speedrun.beatgame.prioritytask.tasks.PriorityTask;
import com.player2.playerengine.tasks.speedrun.beatgame.prioritytask.tasks.RecraftableItemPriorityTask;
import com.player2.playerengine.tasks.speedrun.beatgame.prioritytask.tasks.ResourcePriorityTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.trackers.EntityTracker;
import com.player2.playerengine.trackers.storage.ItemStorageTracker;
import com.player2.playerengine.util.Dimension;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.Pair;
import com.player2.playerengine.util.RecipeTarget;
import com.player2.playerengine.util.SmeltTarget;
import com.player2.playerengine.util.helpers.ConfigHelper;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.slots.Slot;
import com.player2.playerengine.util.time.TimerGame;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.api.utils.input.Input;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.ArrayUtils;

public class BeatMinecraftTask extends Task {
   private static final Item[] COLLECT_EYE_ARMOR = new Item[]{Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
   private static final Item[] COLLECT_IRON_ARMOR = ItemHelper.IRON_ARMORS;
   private static final Item[] COLLECT_EYE_ARMOR_END = ItemHelper.DIAMOND_ARMORS;
   private static final ItemTarget[] COLLECT_EYE_GEAR_MIN = combine(ItemTarget.of(Items.DIAMOND_SWORD), ItemTarget.of(Items.DIAMOND_PICKAXE));
   private static final int END_PORTAL_FRAME_COUNT = 12;
   private static final double END_PORTAL_BED_SPAWN_RANGE = 8.0;
   private static final Predicate<ItemStack> noCurseOfBinding = stack -> !stack.get(DataComponents.ENCHANTMENTS).entrySet().stream().anyMatch(e->e.getKey().unwrapKey().get().equals(Enchantments.BINDING_CURSE));
   private static BeatMinecraftConfig config;
   private static GoToStrongholdPortalTask locateStrongholdTask;
   private static boolean openingEndPortal = false;
   private final UselessItems uselessItems;
   private final HashMap<Item, Integer> cachedEndItemDrops = new HashMap<>();
   private final TimerGame cachedEndItemNothingWaitTime = new TimerGame(10.0);
   private final Task buildMaterialsTask;
   private final PlaceBedAndSetSpawnTask setBedSpawnTask = new PlaceBedAndSetSpawnTask();
   private final Task getOneBedTask = TaskCatalogue.getItemTask("bed", 1);
   private final Task sleepThroughNightTask = new SleepThroughNightTask();
   private final Task killDragonBedStratsTask = new KillEnderDragonWithBedsTask();
   private final DragonBreathTracker dragonBreathTracker = new DragonBreathTracker();
   private final TimerGame timer1 = new TimerGame(5.0);
   private final TimerGame timer2 = new TimerGame(35.0);
   private final TimerGame timer3 = new TimerGame(60.0);
   private final List<PriorityTask> gatherResources = new LinkedList<>();
   private final TimerGame changedTaskTimer = new TimerGame(3.0);
   private final TimerGame forcedTaskTimer = new TimerGame(10.0);
   private final List<BlockPos> blacklistedChests = new LinkedList<>();
   private final TimerGame waterPlacedTimer = new TimerGame(1.5);
   private final TimerGame fortressTimer = new TimerGame(20.0);
   private final PlayerEngineController mod;
   private PriorityTask lastGather = null;
   private Task lastTask = null;
   private boolean pickupFurnace = false;
   private boolean pickupSmoker = false;
   private boolean pickupCrafting = false;
   private Task rePickupTask = null;
   private Task searchTask = null;
   private boolean hasRods = false;
   private boolean gotToBiome = false;
   private GetRidOfExtraWaterBucketTask getRidOfExtraWaterBucketTask = null;
   private int repeated = 0;
   private boolean gettingPearls = false;
   private SafeNetherPortalTask safeNetherPortalTask;
   private boolean escaped = false;
   private boolean gotToFortress = false;
   private GetWithinRangeOfBlockTask cachedFortressTask = null;
   private boolean resetFortressTask = false;
   private BlockPos prevPos = null;
   private Task goToNetherTask = new DefaultGoToDimensionTask(Dimension.NETHER);
   private boolean dragonIsDead = false;
   private BlockPos endPortalCenterLocation;
   private boolean ranStrongholdLocator;
   private boolean endPortalOpened;
   private BlockPos bedSpawnLocation;
   private int cachedFilledPortalFrames = 0;
   private boolean enterindEndPortal = false;
   private Task lootTask;
   private boolean collectingEyes;
   private boolean escapingDragonsBreath = false;
   private Task getBedTask;
   private List<BeatMinecraftTask.TaskChange> taskChanges = new ArrayList<>();
   private PriorityTask prevLastGather = null;
   private BlockPos biomePos = null;

   public BeatMinecraftTask(PlayerEngineController mod) {
      this.mod = mod;
      locateStrongholdTask = new GoToStrongholdPortalTask(config.targetEyes);
      this.buildMaterialsTask = new GetBuildingMaterialsTask(config.buildMaterialCount);
      this.uselessItems = new UselessItems(config);
      if (mod.getWorld().getDifficulty() != Difficulty.EASY) {
         mod.logWarning("Detected that the difficulty is other than easy!");
         if (mod.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            mod.logWarning("No mobs spawn on peaceful difficulty, so the bot will not be able to beat the game. Please change it!");
         } else {
            mod.logWarning("This could cause the bot to die sooner, please consider changing it...");
         }
      }

      ItemStorageTracker itemStorage = mod.getItemStorage();
      this.gatherResources
         .add(
            new MineBlockPriorityTask(
               ItemHelper.itemsToBlocks(ItemHelper.LOG),
               ItemHelper.LOG,
               MiningRequirement.STONE,
               new DistanceItemPriorityCalculator(1050.0, 450.0, 5.0, 4, 10),
               a -> itemStorage.hasItem(Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE) && itemStorage.getItemCount(ItemHelper.LOG) < 5
            )
         );
      this.addOreMiningTasks();
      this.addCollectFoodTask(mod);
      this.addStoneToolsTasks();
      this.addPickaxeTasks(mod);
      this.addDiamondArmorTasks(mod);
      this.addLootChestsTasks(mod);
      this.addPickupImportantItemsTask(mod);
      this.gatherResources
         .add(
            new MineBlockPriorityTask(
               new Block[]{Blocks.GRAVEL},
               new Item[]{Items.FLINT},
               MiningRequirement.STONE,
               new DistanceItemPriorityCalculator(17500.0, 7500.0, 5.0, 1, 1),
               a -> itemStorage.hasItem(Items.STONE_SHOVEL) && !itemStorage.hasItem(Items.FLINT_AND_STEEL)
            )
         );
      this.gatherResources
         .add(
            new MineBlockPriorityTask(
               ItemHelper.itemsToBlocks(ItemHelper.BED),
               ItemHelper.BED,
               MiningRequirement.HAND,
               new DistanceItemPriorityCalculator(25000.0, 25000.0, 5.0, this.getTargetBeds(mod), this.getTargetBeds(mod))
            )
         );
      this.gatherResources.add(new CraftItemPriorityTask(200.0, this.getRecipeTarget(Items.SHIELD), a -> itemStorage.hasItem(Items.IRON_INGOT)));
      this.gatherResources
         .add(
            new CraftItemPriorityTask(
               300.0, mod.getCraftingRecipeTracker().getFirstRecipeTarget(Items.BUCKET, 2), a -> itemStorage.getItemCount(Items.IRON_INGOT) >= 6
            )
         );
      this.gatherResources
         .add(
            new CraftItemPriorityTask(
               100.0, this.getRecipeTarget(Items.FLINT_AND_STEEL), a -> itemStorage.hasItem(Items.IRON_INGOT) && itemStorage.hasItem(Items.FLINT)
            )
         );
      this.gatherResources
         .add(
            new CraftItemPriorityTask(
               330.0,
               this.getRecipeTarget(Items.DIAMOND_SWORD),
               a -> itemStorage.getItemCount(Items.DIAMOND) >= 2 && StorageHelper.miningRequirementMet(mod, MiningRequirement.DIAMOND)
            )
         );
      this.gatherResources
         .add(new CraftItemPriorityTask(400.0, this.getRecipeTarget(Items.GOLDEN_HELMET), a -> itemStorage.getItemCount(Items.GOLD_INGOT) >= 5));
      this.addSleepTask(mod);
      this.gatherResources.add(new ActionPriorityTask(a -> {
         Pair<Task, Double> pair = new Pair<>(TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1), Double.NEGATIVE_INFINITY);
         if (!itemStorage.hasItem(Items.WATER_BUCKET) && !hasItem(mod, Items.WATER_BUCKET)) {
            Optional<BlockPos> optionalPos = mod.getBlockScanner().getNearestBlock(Blocks.WATER);
            if (optionalPos.isEmpty()) {
               return pair;
            } else {
               double distance = Math.sqrt(BlockPosVer.getSquaredDistance(optionalPos.get(), mod.getPlayer().position()));
               if (distance > 55.0) {
                  return pair;
               } else {
                  pair.setRight(10.0 / distance * 77.3);
                  return pair;
               }
            }
         } else {
            return pair;
         }
      }, a -> itemStorage.hasItem(Items.BUCKET), false, true, true));
      this.addSmeltTasks(mod);
      this.addCookFoodTasks(mod);
   }

   public static BeatMinecraftConfig getConfig() {
      if (config == null) {
         Debug.logInternal("Initializing BeatMinecraftConfig");
         config = new BeatMinecraftConfig();
      }

      return config;
   }

   private static List<BlockPos> getFrameBlocks(PlayerEngineController mod, BlockPos endPortalCenter) {
      List<BlockPos> frameBlocks = new ArrayList<>();

      for (BlockPos pos : mod.getBlockScanner().getKnownLocations(Blocks.END_PORTAL_FRAME)) {
         if (pos.closerThan(endPortalCenter, 20.0)) {
            frameBlocks.add(pos);
         }
      }

      Debug.logInternal("Frame blocks: " + frameBlocks);
      return frameBlocks;
   }

   private static ItemTarget[] combine(ItemTarget[]... targets) {
      List<ItemTarget> combinedTargets = new ArrayList<>();

      for (ItemTarget[] targetArray : targets) {
         combinedTargets.addAll(Arrays.asList(targetArray));
      }

      Debug.logInternal("Combined Targets: " + combinedTargets);
      ItemTarget[] combinedArray = combinedTargets.toArray(new ItemTarget[0]);
      Debug.logInternal("Combined Array: " + Arrays.toString((Object[])combinedArray));
      return combinedArray;
   }

   private static boolean isEndPortalFrameFilled(PlayerEngineController mod, BlockPos pos) {
      if (!mod.getChunkTracker().isChunkLoaded(pos)) {
         Debug.logInternal("Chunk is not loaded");
         return false;
      } else {
         BlockState blockState = mod.getWorld().getBlockState(pos);
         if (blockState.getBlock() != Blocks.END_PORTAL_FRAME) {
            Debug.logInternal("Block is not an End Portal Frame");
            return false;
         } else {
            boolean isFilled = (Boolean)blockState.getValue(EndPortalFrameBlock.HAS_EYE);
            Debug.logInternal("End Portal Frame is " + (isFilled ? "filled" : "not filled"));
            return isFilled;
         }
      }
   }

   public static boolean isTaskRunning(PlayerEngineController mod, Task task) {
      if (task == null) {
         Debug.logInternal("Task is null");
         return false;
      } else {
         boolean taskActive = task.isActive();
         boolean taskFinished = task.isFinished();
         Debug.logInternal("Task is not null");
         Debug.logInternal("Task is " + (taskActive ? "active" : "not active"));
         Debug.logInternal("Task is " + (taskFinished ? "finished" : "not finished"));
         return taskActive && !taskFinished;
      }
   }

   public static void throwAwayItems(PlayerEngineController mod, Item... items) {
      throwAwaySlots(mod, mod.getItemStorage().getSlotsWithItemPlayerInventory(false, items));
   }

   public static void throwAwaySlots(PlayerEngineController mod, List<Slot> slots) {
      for (Slot slot : slots) {
         if (Slot.isCursor(slot)) {
            mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
         } else {
            mod.getSlotHandler().clickSlot(slot, 0, ClickType.PICKUP);
         }
      }
   }

   public static boolean hasItem(PlayerEngineController mod, Item item) {
      LivingEntity player = mod.getPlayer();
      LivingEntityInventory inv = mod.getInventory();

      for (List<ItemStack> list : List.of(inv.main, inv.armor, inv.offHand)) {
         for (ItemStack itemStack : list) {
            if (itemStack.getItem().equals(item)) {
               return true;
            }
         }
      }

      return false;
   }

   public static int getCountWithCraftedFromOre(PlayerEngineController mod, Item item) {
      ItemStorageTracker itemStorage = mod.getItemStorage();
      if (item == Items.COAL) {
         return itemStorage.getItemCount(item);
      } else if (item == Items.RAW_IRON) {
         int count = itemStorage.getItemCount(Items.RAW_IRON, Items.IRON_INGOT);
         count += itemStorage.getItemCount(Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.AXOLOTL_BUCKET, Items.POWDER_SNOW_BUCKET) * 3;
         count += hasItem(mod, Items.SHIELD) ? 1 : 0;
         count += hasItem(mod, Items.FLINT_AND_STEEL) ? 1 : 0;
         count += hasItem(mod, Items.IRON_SWORD) ? 2 : 0;
         count += hasItem(mod, Items.IRON_PICKAXE) ? 3 : 0;
         count += hasItem(mod, Items.IRON_HELMET) ? 5 : 0;
         count += hasItem(mod, Items.IRON_CHESTPLATE) ? 8 : 0;
         count += hasItem(mod, Items.IRON_LEGGINGS) ? 7 : 0;
         return count + (hasItem(mod, Items.IRON_BOOTS) ? 4 : 0);
      } else if (item == Items.RAW_GOLD) {
         int count = itemStorage.getItemCount(Items.RAW_GOLD, Items.GOLD_INGOT);
         count += hasItem(mod, Items.GOLDEN_PICKAXE) ? 3 : 0;
         count += hasItem(mod, Items.GOLDEN_HELMET) ? 5 : 0;
         count += hasItem(mod, Items.GOLDEN_CHESTPLATE) ? 8 : 0;
         count += hasItem(mod, Items.GOLDEN_LEGGINGS) ? 7 : 0;
         return count + (hasItem(mod, Items.GOLDEN_BOOTS) ? 4 : 0);
      } else if (item == Items.DIAMOND) {
         int count = itemStorage.getItemCount(Items.DIAMOND);
         count += hasItem(mod, Items.DIAMOND_SWORD) ? 2 : 0;
         count += hasItem(mod, Items.DIAMOND_PICKAXE) ? 3 : 0;
         count += hasItem(mod, Items.DIAMOND_HELMET) ? 5 : 0;
         count += hasItem(mod, Items.DIAMOND_CHESTPLATE) ? 8 : 0;
         count += hasItem(mod, Items.DIAMOND_LEGGINGS) ? 7 : 0;
         return count + (hasItem(mod, Items.DIAMOND_BOOTS) ? 4 : 0);
      } else {
         throw new IllegalStateException("Invalid ore item: " + item);
      }
   }

   private static Block[] mapOreItemToBlocks(Item item) {
      if (item.equals(Items.RAW_IRON)) {
         return new Block[]{Blocks.DEEPSLATE_IRON_ORE, Blocks.IRON_ORE};
      } else if (item.equals(Items.RAW_GOLD)) {
         return new Block[]{Blocks.DEEPSLATE_GOLD_ORE, Blocks.GOLD_ORE};
      } else if (item.equals(Items.DIAMOND)) {
         return new Block[]{Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DIAMOND_ORE};
      } else if (item.equals(Items.COAL)) {
         return new Block[]{Blocks.DEEPSLATE_COAL_ORE, Blocks.COAL_ORE};
      } else {
         throw new IllegalStateException("Invalid ore: " + item);
      }
   }

   private void addSleepTask(PlayerEngineController mod) {
      boolean[] skipNight = new boolean[]{false};
      this.gatherResources.add(new ActionPriorityTask(a -> new PlaceBedAndSetSpawnTask(), () -> {
         if (!WorldHelper.canSleep(mod)) {
            skipNight[0] = false;
            return Double.NEGATIVE_INFINITY;
         } else {
            if (this.lastTask instanceof PlaceBedAndSetSpawnTask && this.lastTask.isFinished()) {
               skipNight[0] = true;
               mod.log("Failed to sleep :(");
               mod.log("Skipping night");
            }

            if (skipNight[0]) {
               return Double.NEGATIVE_INFINITY;
            } else {
               Optional<BlockPos> pos = mod.getBlockScanner().getNearestBlock(ItemHelper.itemsToBlocks(ItemHelper.BED));
               return pos.isPresent() && pos.get().closerToCenterThan(mod.getPlayer().position(), 30.0) ? 1000000.0 : Double.NEGATIVE_INFINITY;
            }
         }
      }));
   }

   private RecipeTarget getRecipeTarget(Item item) {
      ResourceTask task = TaskCatalogue.getItemTask(item, 1);
      if (task instanceof CraftInTableTask craftInTableTask) {
         return craftInTableTask.getRecipeTargets()[0];
      } else if (task instanceof CraftInInventoryTask craftInInventoryTask) {
         return craftInInventoryTask.getRecipeTarget();
      } else {
         throw new IllegalStateException("Item isn't cataloged");
      }
   }

   private void addPickupImportantItemsTask(PlayerEngineController mod) {
      List<Item> importantItems = List.of(
         Items.IRON_PICKAXE,
         Items.DIAMOND_PICKAXE,
         Items.GOLDEN_HELMET,
         Items.DIAMOND_SWORD,
         Items.DIAMOND_CHESTPLATE,
         Items.DIAMOND_LEGGINGS,
         Items.DIAMOND_BOOTS,
         Items.FLINT_AND_STEEL
      );
      this.gatherResources
         .add(
            new ActionPriorityTask(
               mod1 -> {
                  Pair<Task, Double> pair = new Pair<>(null, 0.0);

                  for (Item item : importantItems) {
                     if ((item != Items.IRON_PICKAXE || !mod1.getItemStorage().hasItem(Items.DIAMOND_PICKAXE))
                        && !mod1.getItemStorage().hasItem(item)
                        && mod1.getEntityTracker().itemDropped(item)) {
                        pair.setLeft(new PickupDroppedItemTask(item, 1));
                        pair.setRight(8000.0);
                        return pair;
                     }
                  }

                  return pair;
               }
            )
         );
   }

   private void addCookFoodTasks(PlayerEngineController mod) {
      this.gatherResources.add(new ActionPriorityTask(a -> {
         Pair<Task, Double> pair = new Pair<>(null, Double.NEGATIVE_INFINITY);
         int rawFoodCount = a.getItemStorage().getItemCount(ItemHelper.RAW_FOODS);
         int readyFoodCount = a.getItemStorage().getItemCount(ItemHelper.COOKED_FOODS) + a.getItemStorage().getItemCount(Items.BREAD);
         double priority = rawFoodCount >= 8 ? 450.0 : rawFoodCount * 25;
         if (this.lastTask instanceof SmeltInSmokerTask) {
            priority = Double.POSITIVE_INFINITY;
         }

         if (readyFoodCount > 5 && priority < Double.POSITIVE_INFINITY) {
            priority = 0.01;
         }

         for (CollectFoodTask.CookableFoodTarget cookable : CollectMeatTask.COOKABLE_MEATS) {
            int rawCount = a.getItemStorage().getItemCount(cookable.getRaw());
            if (rawCount != 0) {
               int toSmelt = rawCount + a.getItemStorage().getItemCount(cookable.getCooked());
               SmeltTarget target = new SmeltTarget(new ItemTarget(cookable.cookedFood, toSmelt), new ItemTarget(cookable.rawFood, rawCount));
               pair.setLeft(new SmeltInSmokerTask(target));
               pair.setRight(priority);
               return pair;
            }
         }

         return pair;
      }, a -> StorageHelper.miningRequirementMet(mod, MiningRequirement.STONE), true, false, false));
   }

   private void addSmeltTasks(PlayerEngineController mod) {
      ItemStorageTracker itemStorage = mod.getItemStorage();
      this.gatherResources.add(new ActionPriorityTask(a -> {
         Pair<Task, Double> pair = new Pair<>(null, Double.NEGATIVE_INFINITY);
         boolean hasSufficientPickaxe = itemStorage.hasItem(Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE);
         int neededIron = 11;
         if (itemStorage.hasItem(Items.FLINT_AND_STEEL)) {
            neededIron--;
         }

         if (hasItem(mod, Items.SHIELD)) {
            neededIron--;
         }

         if (hasSufficientPickaxe) {
            neededIron -= 3;
         }

         neededIron -= Math.min(itemStorage.getItemCount(Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET), 2) * 3;
         int count = itemStorage.getItemCount(Items.RAW_IRON);
         int includedCount = count + itemStorage.getItemCount(Items.IRON_INGOT);
         if ((hasSufficientPickaxe || includedCount < 3) && (hasItem(mod, Items.SHIELD) || includedCount < 1) && includedCount < neededIron) {
            return pair;
         } else {
            int toSmelt = Math.min(includedCount, neededIron);
            if (toSmelt <= 0) {
               return pair;
            } else {
               pair.setLeft(new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(Items.IRON_INGOT, toSmelt), new ItemTarget(Items.RAW_IRON, toSmelt))));
               pair.setRight(350.0);
               return pair;
            }
         }
      }, a -> itemStorage.hasItem(Items.RAW_IRON), true, false, false));
      this.gatherResources
         .add(
            new ActionPriorityTask(
               a -> new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(Items.GOLD_INGOT, 5), new ItemTarget(Items.RAW_GOLD, 5))),
               () -> 140.0,
               a -> itemStorage.getItemCount(Items.RAW_GOLD, Items.GOLD_INGOT) >= 5 && !itemStorage.hasItem(Items.GOLDEN_HELMET),
               true,
               true,
               false
            )
         );
   }

   private void addLootChestsTasks(PlayerEngineController mod) {
      this.gatherResources.add(new ActionPriorityTask(a -> {
         Pair<Task, Double> pair = new Pair<>(null, Double.NEGATIVE_INFINITY);
         Optional<BlockPos> chest = this.locateClosestUnopenedChest(mod);
         if (chest.isEmpty()) {
            return pair;
         } else {
            double dst = Math.sqrt(BlockPosVer.getSquaredDistance(chest.get(), mod.getPlayer().position()));
            pair.setRight(30.0 / dst * 175.0);
            pair.setLeft(new GetToBlockTask(chest.get().above()));
            return pair;
         }
      }, a -> true, false, false, true));
      this.gatherResources.add(new ActionPriorityTask(m -> {
         Pair<Task, Double> pair = new Pair<>(null, Double.NEGATIVE_INFINITY);
         Optional<BlockPos> chest = this.locateClosestUnopenedChest(mod);
         if (chest.isEmpty()) {
            return pair;
         } else {
            if (LookHelper.cleanLineOfSight(mod.getPlayer(), chest.get(), 10.0) && chest.get().closerToCenterThan(mod.getPlayer().getEyePosition(), 5.0)) {
               pair.setLeft(new LootContainerTask(chest.get(), this.lootableItems(mod), noCurseOfBinding));
               pair.setRight(Double.POSITIVE_INFINITY);
            }

            return pair;
         }
      }, a -> true, true, false, true));
   }

   private void addCollectFoodTask(PlayerEngineController mod) {
      List<Item> food = new LinkedList<>(ItemHelper.cookableFoodMap.values());
      food.addAll(ItemHelper.cookableFoodMap.keySet());
      food.addAll(List.of(Items.WHEAT, Items.BREAD));
      this.gatherResources
         .add(
            new ResourcePriorityTask(
               new CollectFoodPriorityCalculator(mod, config.foodUnits),
               a -> StorageHelper.miningRequirementMet(mod, MiningRequirement.STONE)
                  && mod.getItemStorage().hasItem(Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD)
                  && CollectFoodTask.calculateFoodPotential(mod) < config.foodUnits,
               new CollectFoodTask(config.foodUnits),
               ItemTarget.of(food.toArray(new Item[0]))
            )
         );
      this.gatherResources
         .add(
            new ActionPriorityTask(
               mod12 -> {
                  Pair<Task, Double> pair = new Pair<>(null, 0.0);
                  pair.setLeft(
                     TaskCatalogue.getItemTask(
                        Items.WHEAT, mod12.getItemStorage().getItemCount(Items.HAY_BLOCK) * 9 + mod12.getItemStorage().getItemCount(Items.WHEAT)
                     )
                  );
                  pair.setRight(10.0);
                  if (StorageHelper.calculateInventoryFoodScore(mod) < 5) {
                     pair.setRight(270.0);
                  }

                  return pair;
               },
               a -> mod.getItemStorage().hasItem(Items.HAY_BLOCK)
            )
         );
      this.gatherResources
         .add(
            new ActionPriorityTask(
               mod1 -> {
                  Pair<Task, Double> pair = new Pair<>(null, 0.0);
                  pair.setLeft(
                     TaskCatalogue.getItemTask("bread", mod1.getItemStorage().getItemCount(Items.WHEAT) / 3 + mod1.getItemStorage().getItemCount(Items.BREAD))
                  );
                  pair.setRight(5.0);
                  if (StorageHelper.calculateInventoryFoodScore(mod) < 5) {
                     pair.setRight(250.0);
                  }

                  return pair;
               },
               a -> mod.getItemStorage().getItemCount(Items.WHEAT) >= 3
            )
         );
   }

   private void addOreMiningTasks() {
      this.gatherResources.add(this.getOrePriorityTask(Items.COAL, MiningRequirement.STONE, 1050, 250, 5, 4, 7));
      this.gatherResources.add(this.getOrePriorityTask(Items.RAW_IRON, MiningRequirement.STONE, 1050, 250, 5, 11, 11));
      this.gatherResources.add(this.getOrePriorityTask(Items.RAW_GOLD, MiningRequirement.IRON, 1050, 250, 5, 5, 5));
      this.gatherResources.add(this.getOrePriorityTask(Items.DIAMOND, MiningRequirement.IRON, 1050, 250, 5, 27, 30));
   }

   private PriorityTask getOrePriorityTask(
      Item item, MiningRequirement requirement, int multiplier, int unneededMultiplier, int unneededThreshold, int minCount, int maxCount
   ) {
      Block[] blocks = mapOreItemToBlocks(item);
      return new MineBlockPriorityTask(
         blocks,
         new Item[]{item},
         requirement,
         new BeatMinecraftTask.DistanceOrePriorityCalculator(item, multiplier, unneededMultiplier, unneededThreshold, minCount, maxCount)
      );
   }

   private void addStoneToolsTasks() {
      this.gatherResources
         .add(
            new ResourcePriorityTask(
               StaticItemPriorityCalculator.of(520),
               altoClef -> StorageHelper.miningRequirementMet(this.mod, MiningRequirement.STONE),
               true,
               true,
               false,
               ItemTarget.of(Items.STONE_AXE, Items.STONE_SWORD, Items.STONE_SHOVEL, Items.STONE_HOE)
            )
         );
      this.gatherResources
         .add(
            new CraftItemPriorityTask(
               300.0,
               this.getRecipeTarget(Items.STONE_SWORD),
               a -> StorageHelper.miningRequirementMet(this.mod, MiningRequirement.STONE)
                  && !this.mod.getItemStorage().hasItem(Items.DIAMOND_SWORD, Items.IRON_SWORD)
            )
         );
      this.gatherResources
         .add(
            new CraftItemPriorityTask(
               300.0,
               this.getRecipeTarget(Items.STONE_AXE),
               a -> StorageHelper.miningRequirementMet(this.mod, MiningRequirement.STONE)
                  && !this.mod.getItemStorage().hasItem(Items.DIAMOND_AXE, Items.IRON_AXE)
            )
         );
   }

   private void addDiamondArmorTasks(PlayerEngineController mod) {
      this.gatherResources
         .add(new CraftItemPriorityTask(350.0, this.getRecipeTarget(Items.DIAMOND_CHESTPLATE), a -> mod.getItemStorage().getItemCount(Items.DIAMOND) >= 8));
      this.gatherResources
         .add(new CraftItemPriorityTask(300.0, this.getRecipeTarget(Items.DIAMOND_LEGGINGS), a -> mod.getItemStorage().getItemCount(Items.DIAMOND) >= 7));
      this.gatherResources
         .add(new CraftItemPriorityTask(220.0, this.getRecipeTarget(Items.DIAMOND_BOOTS), a -> mod.getItemStorage().getItemCount(Items.DIAMOND) >= 5));
   }

   private void addPickaxeTasks(PlayerEngineController mod) {
      this.gatherResources
         .add(
            new ResourcePriorityTask(
               StaticItemPriorityCalculator.of(400),
               a -> !mod.getItemStorage().hasItem(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE),
               ItemTarget.of(Items.WOODEN_PICKAXE)
            )
         );
      this.gatherResources
         .add(
            new RecraftableItemPriorityTask(
               410.0,
               10000.0,
               this.getRecipeTarget(Items.STONE_PICKAXE),
               a -> {
                  List<Slot> list = mod.getItemStorage().getSlotsWithItemPlayerInventory(false);
                  boolean hasSafeIronPick = false;

                  for (Slot slot : list) {
                     if (slot.getInventorySlot() != -1) {
                        ItemStack stack = mod.getBaritone().getEntityContext().inventory().getItem(slot.getInventorySlot());
                        if (!StorageHelper.shouldSaveStack(mod, Blocks.STONE, stack) && stack.getItem().equals(Items.IRON_PICKAXE)) {
                           hasSafeIronPick = true;
                           break;
                        }
                     }
                  }

                  return StorageHelper.miningRequirementMet(mod, MiningRequirement.WOOD)
                     && !mod.getItemStorage().hasItem(Items.STONE_PICKAXE)
                     && !hasSafeIronPick
                     && !mod.getItemStorage().hasItem(Items.DIAMOND_PICKAXE);
               }
            )
         );
      this.gatherResources
         .add(
            new CraftItemPriorityTask(
               420.0,
               this.getRecipeTarget(Items.IRON_PICKAXE),
               a -> !mod.getItemStorage().hasItem(Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE) && mod.getItemStorage().getItemCount(Items.IRON_INGOT) >= 3
            )
         );
      this.gatherResources
         .add(new CraftItemPriorityTask(430.0, this.getRecipeTarget(Items.DIAMOND_PICKAXE), a -> mod.getItemStorage().getItemCount(Items.DIAMOND) >= 3));
   }

   @Override
   public boolean isFinished() {
      if (WorldHelper.getCurrentDimension(this.mod) == Dimension.OVERWORLD && this.dragonIsDead) {
         Debug.logInternal("isFinished - Dragon is dead in the Overworld");
         return true;
      } else {
         Debug.logInternal("isFinished - Returning false");
         return false;
      }
   }

   private boolean needsBuildingMaterials(PlayerEngineController mod) {
      int materialCount = StorageHelper.getBuildingMaterialCount(mod);
      boolean shouldForce = isTaskRunning(mod, this.buildMaterialsTask);
      if (materialCount >= config.minBuildMaterialCount && !shouldForce) {
         Debug.logInternal("Building materials not needed");
         return false;
      } else {
         Debug.logInternal("Building materials needed: " + materialCount);
         Debug.logInternal("Force build materials: " + shouldForce);
         return true;
      }
   }

   private void updateCachedEndItems(PlayerEngineController mod) {
      List<ItemEntity> droppedItems = mod.getEntityTracker().getDroppedItems();
      if (droppedItems.isEmpty() && !this.cachedEndItemNothingWaitTime.elapsed()) {
         Debug.logInternal("No dropped items and cache wait time not elapsed.");
      } else {
         this.cachedEndItemNothingWaitTime.reset();
         this.cachedEndItemDrops.clear();

         for (ItemEntity entity : droppedItems) {
            Item item = entity.getItem().getItem();
            int count = entity.getItem().getCount();
            this.cachedEndItemDrops.put(item, this.cachedEndItemDrops.getOrDefault(item, 0) + count);
            Debug.logInternal("Added dropped item: " + item + " with count: " + count);
         }
      }
   }

   private List<Item> lootableItems(PlayerEngineController mod) {
      List<Item> lootable = new ArrayList<>();
      lootable.add(Items.APPLE);
      lootable.add(Items.GOLDEN_APPLE);
      lootable.add(Items.ENCHANTED_GOLDEN_APPLE);
      lootable.add(Items.GOLDEN_CARROT);
      lootable.add(Items.OBSIDIAN);
      lootable.add(Items.STICK);
      lootable.add(Items.COAL);
      lootable.addAll(Arrays.stream(ItemHelper.LOG).toList());
      lootable.add(Items.BREAD);
      boolean isGoldenHelmetEquipped = StorageHelper.isArmorEquipped(mod, Items.GOLDEN_HELMET);
      boolean hasGoldenHelmet = mod.getItemStorage().hasItemInventoryOnly(Items.GOLDEN_HELMET);
      if (!mod.getItemStorage().hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
         lootable.add(Items.IRON_PICKAXE);
      }

      if (mod.getItemStorage().getItemCount(Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET) < 2) {
         lootable.add(Items.BUCKET);
      }

      boolean hasEnoughGoldIngots = mod.getItemStorage().getItemCountInventoryOnly(Items.GOLD_INGOT) >= 5;
      if (!isGoldenHelmetEquipped && !hasGoldenHelmet) {
         lootable.add(Items.GOLDEN_HELMET);
      }

      if (!hasEnoughGoldIngots && !isGoldenHelmetEquipped && !hasGoldenHelmet || config.barterPearlsInsteadOfEndermanHunt) {
         lootable.add(Items.GOLD_INGOT);
      }

      lootable.add(Items.FLINT_AND_STEEL);
      if (!mod.getItemStorage().hasItemInventoryOnly(Items.FLINT_AND_STEEL) && !mod.getItemStorage().hasItemInventoryOnly(Items.FIRE_CHARGE)) {
         lootable.add(Items.FIRE_CHARGE);
      }

      if (!mod.getItemStorage().hasItemInventoryOnly(Items.BUCKET) && !mod.getItemStorage().hasItemInventoryOnly(Items.WATER_BUCKET)) {
         lootable.add(Items.IRON_INGOT);
      }

      if (!StorageHelper.itemTargetsMetInventory(mod, COLLECT_EYE_GEAR_MIN)) {
         lootable.add(Items.DIAMOND);
      }

      if (!mod.getItemStorage().hasItemInventoryOnly(Items.FLINT)) {
         lootable.add(Items.FLINT);
      }

      Debug.logInternal("Lootable items: " + lootable);
      return lootable;
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.mod.getExtraBaritoneSettings().canWalkOnEndPortal(false);
      this.mod.getBehaviour().pop();
      Debug.logInternal("Stopped onStop method");
      Debug.logInternal("canWalkOnEndPortal set to false");
      Debug.logInternal("Behaviour popped");
      Debug.logInternal("Stopped tracking BED blocks");
      Debug.logInternal("Stopped tracking TRACK_BLOCKS");
   }

   @Override
   protected boolean isEqual(Task other) {
      boolean isSameTask = other instanceof BeatMinecraftTask;
      if (!isSameTask) {
         Debug.logInternal("The 'other' task is not of type BeatMinecraftTask");
      }

      return isSameTask;
   }

   @Override
   protected String toDebugString() {
      return "Beating the game (Miran version).";
   }

   private boolean endPortalFound(PlayerEngineController mod, BlockPos endPortalCenter) {
      if (endPortalCenter == null) {
         Debug.logInternal("End portal center is null");
         return false;
      } else {
         return true;
      }
   }

   private boolean endPortalOpened(PlayerEngineController mod, BlockPos endPortalCenter) {
      if (this.endPortalOpened && endPortalCenter != null) {
         BlockScanner blockTracker = mod.getBlockScanner();
         if (blockTracker != null) {
            boolean isValid = blockTracker.isBlockAtPosition(endPortalCenter, Blocks.END_PORTAL);
            Debug.logInternal("End Portal is " + (isValid ? "valid" : "invalid"));
            return isValid;
         }
      }

      Debug.logInternal("End Portal is not opened yet");
      return false;
   }

   private boolean spawnSetNearPortal(PlayerEngineController mod, BlockPos endPortalCenter) {
      if (this.bedSpawnLocation == null) {
         Debug.logInternal("Bed spawn location is null");
         return false;
      } else {
         BlockScanner blockTracker = mod.getBlockScanner();
         boolean isValid = blockTracker.isBlockAtPosition(this.bedSpawnLocation, ItemHelper.itemsToBlocks(ItemHelper.BED));
         Debug.logInternal("Spawn set near portal: " + isValid);
         return isValid;
      }
   }

   private Optional<BlockPos> locateClosestUnopenedChest(PlayerEngineController mod) {
      return !WorldHelper.getCurrentDimension(mod).equals(Dimension.OVERWORLD)
         ? Optional.empty()
         : mod.getBlockScanner()
            .getNearestBlock(
               (Predicate<BlockPos>)(blockPos -> {
                  if (this.blacklistedChests.contains(blockPos)) {
                     return false;
                  } else {
                     boolean isUnopenedChest = WorldHelper.isUnopenedChest(mod, blockPos);
                     boolean isWithinDistance = mod.getPlayer().blockPosition().closerThan(blockPos, 150.0);
                     boolean isLootableChest = this.canBeLootablePortalChest(mod, blockPos);
                     Optional<BlockPos> nearestSpawner = mod.getBlockScanner().getNearestBlock(WorldHelper.toVec3d(blockPos), Blocks.SPAWNER);
                     if (nearestSpawner.isPresent() && nearestSpawner.get().closerThan(blockPos, 6.0)) {
                        this.blacklistedChests.add(blockPos);
                        return false;
                     } else {
                        AABB box = new AABB(
                           blockPos.getX() - 5, blockPos.getY() - 5, blockPos.getZ() - 5, blockPos.getX() + 5, blockPos.getY() + 5, blockPos.getZ() + 5
                        );
                        Stream<BlockState> states = BlockPos.betweenClosedStream(box).map(pos -> mod.getWorld().getBlockState(pos));
                        if (states.anyMatch(state -> state.getBlock().equals(Blocks.WATER))) {
                           this.blacklistedChests.add(blockPos);
                           return false;
                        } else {
                           Debug.logInternal("isUnopenedChest: " + isUnopenedChest);
                           Debug.logInternal("isWithinDistance: " + isWithinDistance);
                           Debug.logInternal("isLootableChest: " + isLootableChest);
                           return isUnopenedChest && isWithinDistance && isLootableChest;
                        }
                     }
                  }
               }),
               Blocks.CHEST
            );
   }

   @Override
   protected void onStart() {
      this.resetTimers();
      this.mod.getBehaviour().push();
      this.addThrowawayItemsWarning(this.mod);
      this.addProtectedItems(this.mod);
      this.allowWalkingOnEndPortal(this.mod);
      this.avoidDragonBreath(this.mod);
      this.avoidBreakingBed(this.mod);
      this.mod.getBehaviour().avoidBlockBreaking((Predicate<BlockPos>)(pos -> this.mod.getWorld().getBlockState(pos).getBlock().equals(Blocks.NETHER_PORTAL)));
   }

   private void resetTimers() {
      this.timer1.reset();
      this.timer2.reset();
      this.timer3.reset();
   }

   private void addThrowawayItemsWarning(PlayerEngineController mod) {
      String settingsWarningTail = "in \".minecraft/"+ PlayerEngine.MOD_ID +"_settings.json\". @gamer may break if you don't add this! (sorry!)";
      if (!ArrayUtils.contains(mod.getModSettings().getThrowawayItems(mod), Items.END_STONE)) {
         Debug.logWarning("\"end_stone\" is not part of your \"throwawayItems\" list " + settingsWarningTail);
      }

      if (!mod.getModSettings().shouldThrowawayUnusedItems()) {
         Debug.logWarning("\"throwawayUnusedItems\" is not set to true " + settingsWarningTail);
      }
   }

   private void addProtectedItems(PlayerEngineController mod) {
      mod.getBehaviour()
         .addProtectedItems(
            Items.ENDER_EYE,
            Items.BLAZE_ROD,
            Items.BLAZE_POWDER,
            Items.ENDER_PEARL,
            Items.CRAFTING_TABLE,
            Items.IRON_INGOT,
            Items.WATER_BUCKET,
            Items.FLINT_AND_STEEL,
            Items.SHIELD,
            Items.SHEARS,
            Items.BUCKET,
            Items.GOLDEN_HELMET,
            Items.SMOKER,
            Items.FURNACE
         );
      mod.getBehaviour().addProtectedItems(ItemHelper.BED);
      mod.getBehaviour().addProtectedItems(ItemHelper.IRON_ARMORS);
      mod.getBehaviour().addProtectedItems(ItemHelper.LOG);
      Debug.logInternal("Protected items added successfully.");
   }

   private void allowWalkingOnEndPortal(PlayerEngineController mod) {
      mod.getBehaviour().allowWalkingOn(blockPos -> {
         if (this.enterindEndPortal && mod.getChunkTracker().isChunkLoaded(blockPos)) {
            BlockState blockState = mod.getWorld().getBlockState(blockPos);
            boolean isEndPortal = blockState.getBlock() == Blocks.END_PORTAL;
            if (isEndPortal) {
               Debug.logInternal("Walking on End Portal at " + blockPos.toString());
            }

            return isEndPortal;
         } else {
            return false;
         }
      });
   }

   private void avoidDragonBreath(PlayerEngineController mod) {
      mod.getBehaviour().avoidWalkingThrough(blockPos -> {
         Dimension currentDimension = WorldHelper.getCurrentDimension(mod);
         boolean isEndDimension = currentDimension == Dimension.END;
         boolean isTouchingDragonBreath = this.dragonBreathTracker.isTouchingDragonBreath(blockPos);
         if (isEndDimension && !this.escapingDragonsBreath && isTouchingDragonBreath) {
            Debug.logInternal("Avoiding dragon breath at blockPos: " + blockPos);
            return true;
         } else {
            return false;
         }
      });
   }

   private void avoidBreakingBed(PlayerEngineController mod) {
      mod.getBehaviour().avoidBlockBreaking((Predicate<BlockPos>)(blockPos -> {
         if (this.bedSpawnLocation == null) {
            return false;
         } else {
            BlockPos bedHead = WorldHelper.getBedHead(mod, this.bedSpawnLocation);
            BlockPos bedFoot = WorldHelper.getBedFoot(mod, this.bedSpawnLocation);
            boolean shouldAvoidBreaking = blockPos.equals(bedHead) || blockPos.equals(bedFoot);
            if (shouldAvoidBreaking) {
               Debug.logInternal("Avoiding breaking bed at block position: " + blockPos);
            }

            return shouldAvoidBreaking;
         }
      }));
   }

   private void blackListDangerousBlock(PlayerEngineController mod, Block block) {
      Optional<BlockPos> nearestTracking = mod.getBlockScanner().getNearestBlock(block);
      if (nearestTracking.isPresent()) {
         for (Entity entity : mod.getWorld().getAllEntities()) {
            if (!mod.getBlockScanner().isUnreachable(nearestTracking.get())
               && entity instanceof Monster
               && mod.getPlayer().distanceToSqr(entity.position()) < 150.0
               && nearestTracking.get().closerToCenterThan(entity.position(), 30.0)) {
               Debug.logMessage("Blacklisting dangerous " + block.toString());
               mod.getBlockScanner().requestBlockUnreachable(nearestTracking.get(), 0);
            }
         }
      }
   }

   @Override
   protected Task onTick() {
      ItemStorageTracker itemStorage = this.mod.getItemStorage();
      double blockPlacementPenalty = 10.0;
      if (StorageHelper.getNumberOfThrowawayBlocks(this.mod) > 128) {
         blockPlacementPenalty = 5.0;
      } else if (StorageHelper.getNumberOfThrowawayBlocks(this.mod) > 64) {
         blockPlacementPenalty = 7.5;
      }

      this.mod.getBaritoneSettings().blockPlacementPenalty.set(blockPlacementPenalty);
      if (this.mod.getPlayer().getMainHandItem().getItem() instanceof EnderEyeItem && !openingEndPortal) {
         for (ItemStack itemStack : itemStorage.getItemStacksPlayerInventory(true)) {
            Item item = itemStack.getItem();
            if (itemStack.is(ItemTags.SWORDS) || item instanceof AxeItem) {
               this.mod.getSlotHandler().forceEquipItem(item);
            }
         }
      }

      boolean shouldSwap = false;
      boolean hasInHotbar = false;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mod.getBaritone().getEntityContext().inventory().getItem(i);
         if (stack.getItem().equals(Items.IRON_PICKAXE) && StorageHelper.shouldSaveStack(this.mod, Blocks.STONE, stack)) {
            shouldSwap = true;
         }

         if (stack.getItem().equals(Items.STONE_PICKAXE)) {
            hasInHotbar = true;
         }
      }

      if (shouldSwap && !hasInHotbar && itemStorage.hasItem(Items.STONE_PICKAXE)) {
         this.mod.getSlotHandler().forceEquipItem(Items.STONE_PICKAXE);
      }

      boolean eyeGearSatisfied = StorageHelper.isArmorEquippedAll(this.mod, COLLECT_EYE_ARMOR);
      boolean ironGearSatisfied = StorageHelper.isArmorEquippedAll(this.mod, COLLECT_IRON_ARMOR);
      if (itemStorage.hasItem(Items.DIAMOND_PICKAXE)) {
         this.mod.getBehaviour().setBlockBreakAdditionalPenalty(1.2);
      } else {
         this.mod.getBehaviour().setBlockBreakAdditionalPenalty(this.mod.getBaritoneSettings().blockBreakAdditionalPenalty.defaultValue);
      }

      Predicate<Task> isCraftingTableTask = task -> task instanceof CraftInTableTask;

      for (BlockPos craftingTable : this.mod.getBlockScanner().getKnownLocations(Blocks.CRAFTING_TABLE)) {
         if (itemStorage.hasItem(Items.CRAFTING_TABLE)
            && !this.thisOrChildSatisfies(isCraftingTableTask)
            && !this.mod.getBlockScanner().isUnreachable(craftingTable)) {
            Debug.logMessage("Blacklisting extra crafting table.");
            this.mod.getBlockScanner().requestBlockUnreachable(craftingTable, 0);
         }

         if (!this.mod.getBlockScanner().isUnreachable(craftingTable)) {
            BlockState craftingTablePosUp = this.mod.getWorld().getBlockState(craftingTable.above(2));
            if (this.mod.getEntityTracker().entityFound(Witch.class)) {
               Optional<Entity> witch = this.mod.getEntityTracker().getClosestEntity(Witch.class);
               if (witch.isPresent() && craftingTable.closerToCenterThan(witch.get().position(), 15.0)) {
                  Debug.logMessage("Blacklisting witch crafting table.");
                  this.mod.getBlockScanner().requestBlockUnreachable(craftingTable, 0);
               }
            }

            if (craftingTablePosUp.getBlock() == Blocks.WHITE_WOOL) {
               Debug.logMessage("Blacklisting pillage crafting table.");
               this.mod.getBlockScanner().requestBlockUnreachable(craftingTable, 0);
            }
         }
      }

      for (BlockPos smoker : this.mod.getBlockScanner().getKnownLocations(Blocks.SMOKER)) {
         if (itemStorage.hasItem(Items.SMOKER) && !this.mod.getBlockScanner().isUnreachable(smoker)) {
            Debug.logMessage("Blacklisting extra smoker.");
            this.mod.getBlockScanner().requestBlockUnreachable(smoker, 0);
         }
      }

      for (BlockPos furnace : this.mod.getBlockScanner().getKnownLocations(Blocks.FURNACE)) {
         if (itemStorage.hasItem(Items.FURNACE)
            && !this.goToNetherTask.isActive()
            && !this.ranStrongholdLocator
            && !this.mod.getBlockScanner().isUnreachable(furnace)) {
            Debug.logMessage("Blacklisting extra furnace.");
            this.mod.getBlockScanner().requestBlockUnreachable(furnace, 0);
         }
      }

      for (BlockPos log : this.mod.getBlockScanner().getKnownLocations(ItemHelper.itemsToBlocks(ItemHelper.LOG))) {
         for (Entity entity : this.mod.getWorld().getAllEntities()) {
            if (entity instanceof Pillager && !this.mod.getBlockScanner().isUnreachable(log) && log.closerToCenterThan(entity.position(), 40.0)) {
               Debug.logMessage("Blacklisting pillage log.");
               this.mod.getBlockScanner().requestBlockUnreachable(log, 0);
            }
         }

         if (log.getY() < 62 && !this.mod.getBlockScanner().isUnreachable(log) && !ironGearSatisfied && !eyeGearSatisfied) {
            Debug.logMessage("Blacklisting dangerous log.");
            this.mod.getBlockScanner().requestBlockUnreachable(log, 0);
         }
      }

      if (!ironGearSatisfied && !eyeGearSatisfied) {
         this.blackListDangerousBlock(this.mod, Blocks.DEEPSLATE_COAL_ORE);
         this.blackListDangerousBlock(this.mod, Blocks.COAL_ORE);
         this.blackListDangerousBlock(this.mod, Blocks.DEEPSLATE_IRON_ORE);
         this.blackListDangerousBlock(this.mod, Blocks.IRON_ORE);
      }

      List<Block> ancientCityBlocks = List.of(
         Blocks.DEEPSLATE_BRICKS,
         Blocks.SCULK,
         Blocks.SCULK_VEIN,
         Blocks.SCULK_SENSOR,
         Blocks.SCULK_SHRIEKER,
         Blocks.DEEPSLATE_TILE_STAIRS,
         Blocks.CRACKED_DEEPSLATE_BRICKS,
         Blocks.SOUL_LANTERN,
         Blocks.DEEPSLATE_TILES,
         Blocks.POLISHED_DEEPSLATE
      );
      int radius = 5;

      label691:
      for (BlockPos pos : this.mod.getBlockScanner().getKnownLocations(ItemHelper.itemsToBlocks(ItemHelper.WOOL))) {
         for (int x = -5; x < 5; x++) {
            for (int y = -5; y < 5; y++) {
               for (int z = -5; z < 5; z++) {
                  BlockPos p = pos.offset(x, y, z);
                  Block block = this.mod.getWorld().getBlockState(p).getBlock();
                  if (ancientCityBlocks.contains(block)) {
                     Debug.logMessage("Blacklisting ancient city wool " + pos);
                     this.mod.getBlockScanner().requestBlockUnreachable(pos, 0);
                     continue label691;
                  }
               }
            }
         }
      }

      if (locateStrongholdTask.isActive()
         && WorldHelper.getCurrentDimension(this.mod) == Dimension.OVERWORLD
         && !this.mod.getBaritone().getExploreProcess().isActive()
         && this.timer1.elapsed()) {
         this.timer1.reset();
      }

      if ((this.getOneBedTask != null && this.getOneBedTask.isActive() || this.sleepThroughNightTask.isActive() && !itemStorage.hasItem(ItemHelper.BED))
         && this.getBedTask == null
         && !this.mod.getBaritone().getExploreProcess().isActive()
         && this.timer3.elapsed()) {
         this.timer3.reset();
      }

      if (WorldHelper.getCurrentDimension(this.mod) != Dimension.END
         && itemStorage.hasItem(Items.SHIELD)
         && !itemStorage.hasItemInOffhand(this.controller, Items.SHIELD)) {
         return new EquipArmorTask(Items.SHIELD);
      } else {
         if (WorldHelper.getCurrentDimension(this.mod) == Dimension.NETHER) {
            if (itemStorage.hasItem(Items.GOLDEN_HELMET)) {
               return new EquipArmorTask(Items.GOLDEN_HELMET);
            }

            if (itemStorage.hasItem(Items.DIAMOND_HELMET) && !hasItem(this.mod, Items.GOLDEN_HELMET)) {
               return new EquipArmorTask(Items.DIAMOND_HELMET);
            }
         } else if (itemStorage.hasItem(Items.DIAMOND_HELMET)) {
            return new EquipArmorTask(Items.DIAMOND_HELMET);
         }

         if (itemStorage.hasItem(Items.DIAMOND_CHESTPLATE)) {
            return new EquipArmorTask(Items.DIAMOND_CHESTPLATE);
         } else if (itemStorage.hasItem(Items.DIAMOND_LEGGINGS)) {
            return new EquipArmorTask(Items.DIAMOND_LEGGINGS);
         } else if (itemStorage.hasItem(Items.DIAMOND_BOOTS)) {
            return new EquipArmorTask(Items.DIAMOND_BOOTS);
         } else if (itemStorage.getItemCount(Items.FURNACE) > 1) {
            return new PlaceBlockNearbyTask(p -> this.controller.getWorld().getBlockState(p).getBlock() != Blocks.CRAFTING_TABLE, Blocks.FURNACE);
         } else if (itemStorage.getItemCount(Items.CRAFTING_TABLE) > 1) {
            return new PlaceBlockNearbyTask(Blocks.CRAFTING_TABLE);
         } else {
            throwAwayItems(this.mod, Items.SAND, Items.RED_SAND);
            throwAwayItems(this.mod, Items.TORCH);
            throwAwayItems(this.mod, this.uselessItems.uselessItems);
            if (itemStorage.hasItem(Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)) {
               throwAwayItems(this.mod, Items.WOODEN_PICKAXE);
            }

            if (itemStorage.hasItem(Items.DIAMOND_PICKAXE)) {
               throwAwayItems(this.mod, Items.IRON_PICKAXE, Items.STONE_PICKAXE);
            }

            if (itemStorage.hasItem(Items.DIAMOND_SWORD)) {
               throwAwayItems(this.mod, Items.STONE_SWORD, Items.IRON_SWORD);
            }

            if (itemStorage.hasItem(Items.GOLDEN_HELMET)) {
               throwAwayItems(this.mod, Items.RAW_GOLD, Items.GOLD_INGOT);
            }

            if (itemStorage.hasItem(Items.FLINT) || itemStorage.hasItem(Items.FLINT_AND_STEEL)) {
               throwAwayItems(this.mod, Items.GRAVEL);
            }

            if (itemStorage.hasItem(Items.FLINT_AND_STEEL)) {
               throwAwayItems(this.mod, Items.FLINT);
            }

            if (isTaskRunning(this.mod, this.getRidOfExtraWaterBucketTask)) {
               return this.getRidOfExtraWaterBucketTask;
            } else if (itemStorage.getItemCount(Items.WATER_BUCKET) > 1) {
               this.getRidOfExtraWaterBucketTask = new GetRidOfExtraWaterBucketTask();
               return this.getRidOfExtraWaterBucketTask;
            } else {
               if (itemStorage.getItemCount(Items.FLINT_AND_STEEL) > 1) {
                  throwAwayItems(this.mod, Items.FLINT_AND_STEEL);
               }

               if (itemStorage.getItemCount(ItemHelper.BED) > this.getTargetBeds(this.mod)
                  && !this.endPortalFound(this.mod, this.endPortalCenterLocation)
                  && WorldHelper.getCurrentDimension(this.mod) != Dimension.END) {
                  throwAwayItems(this.mod, ItemHelper.BED);
               }

               this.enterindEndPortal = false;
               if (WorldHelper.getCurrentDimension(this.mod) != Dimension.END) {
                  this.cachedEndItemNothingWaitTime.reset();
                  if (!this.endPortalOpened(this.mod, this.endPortalCenterLocation) && WorldHelper.getCurrentDimension(this.mod) == Dimension.OVERWORLD) {
                     Optional<BlockPos> endPortal = this.mod.getBlockScanner().getNearestBlock(Blocks.END_PORTAL);
                     if (endPortal.isPresent()) {
                        this.endPortalCenterLocation = endPortal.get();
                        this.endPortalOpened = true;
                     } else {
                        this.endPortalCenterLocation = this.doSimpleSearchForEndPortal(this.mod);
                     }
                  }

                  if (isTaskRunning(this.mod, this.rePickupTask)) {
                     return this.rePickupTask;
                  } else if (!this.endPortalOpened
                     && WorldHelper.getCurrentDimension(this.mod) != Dimension.END
                     && config.rePickupCraftingTable
                     && !itemStorage.hasItem(Items.CRAFTING_TABLE)
                     && !this.thisOrChildSatisfies(isCraftingTableTask)
                     && (
                        this.mod
                              .getBlockScanner()
                              .anyFound(blockPos -> WorldHelper.canBreak(this.mod, blockPos) && WorldHelper.canReach(this.mod, blockPos), Blocks.CRAFTING_TABLE)
                           || this.mod.getEntityTracker().itemDropped(Items.CRAFTING_TABLE)
                     )
                     && this.pickupCrafting) {
                     this.setDebugState("Picking up the crafting table while we are at it.");
                     return new MineAndCollectTask(Items.CRAFTING_TABLE, 1, new Block[]{Blocks.CRAFTING_TABLE}, MiningRequirement.HAND);
                  } else if (config.rePickupSmoker
                     && !this.endPortalOpened
                     && WorldHelper.getCurrentDimension(this.mod) != Dimension.END
                     && !itemStorage.hasItem(Items.SMOKER)
                     && (
                        this.mod
                              .getBlockScanner()
                              .anyFound(blockPos -> WorldHelper.canBreak(this.mod, blockPos) && WorldHelper.canReach(this.mod, blockPos), Blocks.SMOKER)
                           || this.mod.getEntityTracker().itemDropped(Items.SMOKER)
                     )
                     && this.pickupSmoker) {
                     this.setDebugState("Picking up the smoker while we are at it.");
                     this.rePickupTask = new MineAndCollectTask(Items.SMOKER, 1, new Block[]{Blocks.SMOKER}, MiningRequirement.WOOD);
                     return this.rePickupTask;
                  } else if (config.rePickupFurnace
                     && !this.endPortalOpened
                     && WorldHelper.getCurrentDimension(this.mod) != Dimension.END
                     && !itemStorage.hasItem(Items.FURNACE)
                     && (
                        this.mod
                              .getBlockScanner()
                              .anyFound(blockPos -> WorldHelper.canBreak(this.mod, blockPos) && WorldHelper.canReach(this.mod, blockPos), Blocks.FURNACE)
                           || this.mod.getEntityTracker().itemDropped(Items.FURNACE)
                     )
                     && !this.goToNetherTask.isActive()
                     && !this.ranStrongholdLocator
                     && this.pickupFurnace) {
                     this.setDebugState("Picking up the furnace while we are at it.");
                     this.rePickupTask = new MineAndCollectTask(Items.FURNACE, 1, new Block[]{Blocks.FURNACE}, MiningRequirement.WOOD);
                     return this.rePickupTask;
                  } else {
                     this.pickupFurnace = false;
                     this.pickupSmoker = false;
                     this.pickupCrafting = false;
                     if (config.sleepThroughNight && !this.endPortalOpened && WorldHelper.getCurrentDimension(this.mod) == Dimension.OVERWORLD) {
                        if (WorldHelper.canSleep(this.mod)) {
                           if (this.timer2.elapsed()) {
                              this.timer2.reset();
                           }

                           if (this.timer2.getDuration() >= 30.0 && !this.mod.getPlayer().isSleeping()) {
                              if (this.mod.getEntityTracker().itemDropped(ItemHelper.BED) && this.needsBeds(this.mod)) {
                                 this.setDebugState("Resetting sleep through night task.");
                                 return new PickupDroppedItemTask(new ItemTarget(ItemHelper.BED), true);
                              }

                              if (this.anyBedsFound(this.mod)) {
                                 this.setDebugState("Resetting sleep through night task.");
                                 return new DoToClosestBlockTask(DestroyBlockTask::new, ItemHelper.itemsToBlocks(ItemHelper.BED));
                              }
                           }

                           this.setDebugState("Sleeping through night");
                           return this.sleepThroughNightTask;
                        }

                        if (!itemStorage.hasItem(ItemHelper.BED)
                           && (
                              this.mod
                                    .getBlockScanner()
                                    .anyFound(blockPos -> WorldHelper.canBreak(this.mod, blockPos), ItemHelper.itemsToBlocks(ItemHelper.BED))
                                 || isTaskRunning(this.mod, this.getOneBedTask)
                           )) {
                           this.setDebugState("Getting one bed to sleep in at night.");
                           return this.getOneBedTask;
                        }
                     }

                     boolean needsEyes = !this.endPortalOpened(this.mod, this.endPortalCenterLocation)
                        && WorldHelper.getCurrentDimension(this.mod) != Dimension.END;
                     int filledPortalFrames = this.getFilledPortalFrames(this.mod, this.endPortalCenterLocation);
                     int eyesNeededMin = needsEyes ? config.minimumEyes - filledPortalFrames : 0;
                     int eyesNeeded = needsEyes ? config.targetEyes - filledPortalFrames : 0;
                     int eyes = itemStorage.getItemCount(Items.ENDER_EYE);
                     if (eyes >= eyesNeededMin && (this.ranStrongholdLocator || !this.collectingEyes || eyes >= eyesNeeded)) {
                        this.collectingEyes = false;
                        if (itemStorage.getItemCount(Items.DIAMOND) >= 3 && !itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
                           return TaskCatalogue.getItemTask(Items.DIAMOND_PICKAXE, 1);
                        } else if (itemStorage.getItemCount(Items.IRON_INGOT) >= 3 && !itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
                           return TaskCatalogue.getItemTask(Items.IRON_PICKAXE, 1);
                        } else if (!itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE)) {
                           return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
                        } else if (!itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE)) {
                           return TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
                        } else if (WorldHelper.getCurrentDimension(this.mod) == Dimension.OVERWORLD) {
                           if (itemStorage.hasItem(Items.DIAMOND_PICKAXE)) {
                              Item[] throwGearItems = new Item[]{Items.STONE_SWORD, Items.STONE_PICKAXE, Items.IRON_SWORD, Items.IRON_PICKAXE};
                              List<Slot> ironArmors = itemStorage.getSlotsWithItemPlayerInventory(true, COLLECT_IRON_ARMOR);
                              List<Slot> throwGears = itemStorage.getSlotsWithItemPlayerInventory(true, throwGearItems);
                              if (itemStorage.hasItem(Items.FLINT_AND_STEEL) || itemStorage.hasItem(Items.FIRE_CHARGE)) {
                                 for (Slot throwGear : throwGears) {
                                    if (Slot.isCursor(throwGear)) {
                                       if (!this.mod.getControllerExtras().isBreakingBlock()) {
                                          LookHelper.randomOrientation(this.controller);
                                       }

                                       this.mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
                                    } else {
                                       this.mod.getSlotHandler().clickSlot(throwGear, 0, ClickType.PICKUP);
                                    }
                                 }

                                 for (Slot ironArmor : ironArmors) {
                                    if (Slot.isCursor(ironArmor)) {
                                       if (!this.mod.getControllerExtras().isBreakingBlock()) {
                                          LookHelper.randomOrientation(this.controller);
                                       }

                                       this.mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
                                    } else {
                                       this.mod.getSlotHandler().clickSlot(ironArmor, 0, ClickType.PICKUP);
                                    }
                                 }
                              }
                           }

                           this.ranStrongholdLocator = true;
                           if (WorldHelper.getCurrentDimension(this.mod) == Dimension.OVERWORLD && this.needsBeds(this.mod)) {
                              this.setDebugState("Getting beds before stronghold search.");
                              if (!this.mod.getBaritone().getExploreProcess().isActive() && this.timer1.elapsed()) {
                                 this.timer1.reset();
                              }

                              this.getBedTask = this.getBedTask(this.mod);
                              return this.getBedTask;
                           } else {
                              this.getBedTask = null;
                              if (!itemStorage.hasItem(Items.WATER_BUCKET)) {
                                 this.setDebugState("Getting water bucket.");
                                 return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
                              } else if (!itemStorage.hasItem(Items.FLINT_AND_STEEL)) {
                                 this.setDebugState("Getting flint and steel.");
                                 return TaskCatalogue.getItemTask(Items.FLINT_AND_STEEL, 1);
                              } else if (this.needsBuildingMaterials(this.mod)) {
                                 this.setDebugState("Collecting building materials.");
                                 return this.buildMaterialsTask;
                              } else if (!this.endPortalFound(this.mod, this.endPortalCenterLocation)) {
                                 this.setDebugState("Locating End Portal...");
                                 return locateStrongholdTask;
                              } else {
                                 if (StorageHelper.miningRequirementMetInventory(this.controller, MiningRequirement.WOOD)) {
                                    Optional<BlockPos> silverfish = this.mod
                                       .getBlockScanner()
                                       .getNearestBlock(
                                          (Predicate<BlockPos>)(blockPos -> WorldHelper.getSpawnerEntity(this.mod, blockPos) instanceof Silverfish),
                                          Blocks.SPAWNER
                                       );
                                    if (silverfish.isPresent()) {
                                       this.setDebugState("Breaking silverfish spawner.");
                                       return new DestroyBlockTask(silverfish.get());
                                    }
                                 }

                                 if (this.endPortalOpened(this.mod, this.endPortalCenterLocation)) {
                                    openingEndPortal = false;
                                    if (this.needsBuildingMaterials(this.mod)) {
                                       this.setDebugState("Collecting building materials.");
                                       return this.buildMaterialsTask;
                                    } else if (config.placeSpawnNearEndPortal
                                       && itemStorage.hasItem(ItemHelper.BED)
                                       && !this.spawnSetNearPortal(this.mod, this.endPortalCenterLocation)) {
                                       this.setDebugState("Setting spawn near end portal");
                                       return this.setSpawnNearPortalTask(this.mod);
                                    } else {
                                       this.setDebugState("Entering End");
                                       this.enterindEndPortal = true;
                                       if (!this.mod.getExtraBaritoneSettings().isCanWalkOnEndPortal()) {
                                          this.mod.getExtraBaritoneSettings().canWalkOnEndPortal(true);
                                       }

                                       return new DoToClosestBlockTask(blockPos -> new GetToBlockTask(blockPos.above()), Blocks.END_PORTAL);
                                    }
                                 } else if (itemStorage.hasItem(Items.OBSIDIAN)) {
                                    this.setDebugState("Opening End Portal");
                                    openingEndPortal = true;
                                    return new DoToClosestBlockTask(
                                       blockPos -> new InteractWithBlockTask(Items.ENDER_EYE, blockPos),
                                       blockPos -> !isEndPortalFrameFilled(this.mod, blockPos),
                                       Blocks.END_PORTAL_FRAME
                                    );
                                 } else if (!this.mod.getBlockScanner().anyFoundWithinDistance(10.0, Blocks.OBSIDIAN)
                                    && !this.mod.getEntityTracker().itemDropped(Items.OBSIDIAN)) {
                                    if (this.repeated > 2 && !itemStorage.hasItem(Items.WATER_BUCKET)) {
                                       return new CollectBucketLiquidTask.CollectWaterBucketTask(1);
                                    } else if (!this.waterPlacedTimer.elapsed()) {
                                       this.setDebugState(this.waterPlacedTimer.getDuration() + "");
                                       return null;
                                    } else if (!itemStorage.hasItem(Items.WATER_BUCKET)) {
                                       this.repeated++;
                                       this.waterPlacedTimer.reset();
                                       return null;
                                    } else {
                                       this.repeated = 0;
                                       return new PlaceObsidianBucketTask(
                                          this.mod
                                             .getBlockScanner()
                                             .getNearestBlock(
                                                WorldHelper.toVec3d(this.endPortalCenterLocation),
                                                blockPos -> !blockPos.closerThan(this.endPortalCenterLocation, 8.0),
                                                Blocks.LAVA
                                             )
                                             .get()
                                       );
                                    }
                                 } else if (!itemStorage.hasItem(Items.WATER_BUCKET)) {
                                    return new CollectBucketLiquidTask.CollectWaterBucketTask(1);
                                 } else if (!this.waterPlacedTimer.elapsed()) {
                                    this.setDebugState("waitin " + this.waterPlacedTimer.getDuration());
                                    return null;
                                 } else {
                                    return TaskCatalogue.getItemTask(Items.OBSIDIAN, 1);
                                 }
                              }
                           }
                        } else if (WorldHelper.getCurrentDimension(this.mod) != Dimension.NETHER) {
                           return null;
                        } else {
                           Item[] throwGearItems = new Item[]{Items.STONE_SWORD, Items.STONE_PICKAXE, Items.IRON_SWORD, Items.IRON_PICKAXE};
                           List<Slot> ironArmors = itemStorage.getSlotsWithItemPlayerInventory(true, COLLECT_IRON_ARMOR);
                           List<Slot> throwGears = itemStorage.getSlotsWithItemPlayerInventory(true, throwGearItems);
                           if (itemStorage.hasItem(Items.FLINT_AND_STEEL) || itemStorage.hasItem(Items.FIRE_CHARGE)) {
                              for (Slot throwGearx : throwGears) {
                                 if (Slot.isCursor(throwGearx)) {
                                    if (!this.mod.getControllerExtras().isBreakingBlock()) {
                                       LookHelper.randomOrientation(this.controller);
                                    }

                                    this.mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
                                 } else {
                                    this.mod.getSlotHandler().clickSlot(throwGearx, 0, ClickType.PICKUP);
                                 }
                              }

                              for (Slot ironArmorx : ironArmors) {
                                 if (Slot.isCursor(ironArmorx)) {
                                    if (!this.mod.getControllerExtras().isBreakingBlock()) {
                                       LookHelper.randomOrientation(this.controller);
                                    }

                                    this.mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
                                 } else {
                                    this.mod.getSlotHandler().clickSlot(ironArmorx, 0, ClickType.PICKUP);
                                 }
                              }
                           }

                           this.setDebugState("Locating End Portal...");
                           return locateStrongholdTask;
                        }
                     } else {
                        this.collectingEyes = true;
                        return this.getEyesOfEnderTask(this.mod, eyesNeeded);
                     }
                  }
               } else if (!this.mod.getWorld().hasChunk(0, 0)) {
                  this.setDebugState("Waiting for chunks to load");
                  return null;
               } else {
                  this.updateCachedEndItems(this.mod);
                  if (!this.mod.getEntityTracker().itemDropped(ItemHelper.BED)
                     || !this.needsBeds(this.mod) && WorldHelper.getCurrentDimension(this.mod) != Dimension.END) {
                     if (!itemStorage.hasItem(Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)) {
                        if (this.mod.getEntityTracker().itemDropped(Items.IRON_PICKAXE)) {
                           return new PickupDroppedItemTask(Items.IRON_PICKAXE, 1);
                        }

                        if (this.mod.getEntityTracker().itemDropped(Items.DIAMOND_PICKAXE)) {
                           return new PickupDroppedItemTask(Items.DIAMOND_PICKAXE, 1);
                        }
                     }

                     if (!itemStorage.hasItem(Items.WATER_BUCKET) && this.mod.getEntityTracker().itemDropped(Items.WATER_BUCKET)) {
                        return new PickupDroppedItemTask(Items.WATER_BUCKET, 1);
                     } else {
                        for (Item armorCheck : COLLECT_EYE_ARMOR_END) {
                           if (!StorageHelper.isArmorEquipped(this.mod, armorCheck)) {
                              if (itemStorage.hasItem(armorCheck)) {
                                 this.setDebugState("Equipping armor.");
                                 return new EquipArmorTask(armorCheck);
                              }

                              if (this.mod.getEntityTracker().itemDropped(armorCheck)) {
                                 return new PickupDroppedItemTask(armorCheck, 1);
                              }
                           }
                        }

                        this.dragonBreathTracker.updateBreath(this.mod);

                        for (BlockPos playerIn : WorldHelper.getBlocksTouchingPlayer(this.controller.getPlayer())) {
                           if (this.dragonBreathTracker.isTouchingDragonBreath(playerIn)) {
                              this.setDebugState("ESCAPE dragons breath");
                              this.escapingDragonsBreath = true;
                              return this.dragonBreathTracker.getRunAwayTask();
                           }
                        }

                        this.escapingDragonsBreath = false;
                        if (this.mod.getBlockScanner().anyFound(Blocks.END_PORTAL)) {
                           this.setDebugState("WOOHOO");
                           this.dragonIsDead = true;
                           this.enterindEndPortal = true;
                           if (!this.mod.getExtraBaritoneSettings().isCanWalkOnEndPortal()) {
                              this.mod.getExtraBaritoneSettings().canWalkOnEndPortal(true);
                           }

                           return new DoToClosestBlockTask(
                              blockPos -> new GetToBlockTask(blockPos.above()), pos -> Math.abs(pos.getX()) + Math.abs(pos.getZ()) <= 1, Blocks.END_PORTAL
                           );
                        } else if (!itemStorage.hasItem(ItemHelper.BED) && !this.mod.getBlockScanner().anyFound(ItemHelper.itemsToBlocks(ItemHelper.BED))) {
                           this.setDebugState("No beds, regular strats.");
                           return new KillEnderDragonTask();
                        } else {
                           this.setDebugState("Bed strats");
                           return this.killDragonBedStratsTask;
                        }
                     }
                  } else {
                     return new PickupDroppedItemTask(new ItemTarget(ItemHelper.BED), true);
                  }
               }
            }
         }
      }
   }

   private Task setSpawnNearPortalTask(PlayerEngineController mod) {
      if (this.setBedSpawnTask.isSpawnSet()) {
         this.bedSpawnLocation = this.setBedSpawnTask.getBedSleptPos();
      } else {
         this.bedSpawnLocation = null;
      }

      if (isTaskRunning(mod, this.setBedSpawnTask)) {
         this.setDebugState("Setting spawnpoint now.");
         return this.setBedSpawnTask;
      } else if (WorldHelper.inRangeXZ(mod.getPlayer(), WorldHelper.toVec3d(this.endPortalCenterLocation), 8.0)) {
         return this.setBedSpawnTask;
      } else {
         this.setDebugState("Approaching portal (to set spawnpoint)");
         return new GetToXZTask(this.endPortalCenterLocation.getX(), this.endPortalCenterLocation.getZ());
      }
   }

   private Task getBlazeRodsTask(PlayerEngineController mod, int count) {
      EntityTracker entityTracker = mod.getEntityTracker();
      if (entityTracker.itemDropped(Items.BLAZE_ROD)) {
         Debug.logInternal("Blaze Rod dropped, picking it up.");
         return new PickupDroppedItemTask(Items.BLAZE_ROD, 1);
      } else if (entityTracker.itemDropped(Items.BLAZE_POWDER)) {
         Debug.logInternal("Blaze Powder dropped, picking it up.");
         return new PickupDroppedItemTask(Items.BLAZE_POWDER, 1);
      } else {
         Debug.logInternal("No Blaze Rod or Blaze Powder dropped, collecting Blaze Rods.");
         return new CollectBlazeRodsTask(count);
      }
   }

   private Task getEnderPearlTask(PlayerEngineController mod, int count) {
      if (mod.getEntityTracker().itemDropped(Items.ENDER_PEARL)) {
         return new PickupDroppedItemTask(Items.ENDER_PEARL, 1);
      } else if (config.barterPearlsInsteadOfEndermanHunt) {
         return (Task)(!StorageHelper.isArmorEquipped(mod, Items.GOLDEN_HELMET)
            ? new EquipArmorTask(Items.GOLDEN_HELMET)
            : new TradeWithPiglinsTask(32, Items.ENDER_PEARL, count));
      } else {
         boolean endermanFound = mod.getEntityTracker().entityFound(EnderMan.class);
         boolean pearlDropped = mod.getEntityTracker().itemDropped(Items.ENDER_PEARL);
         if (endermanFound || pearlDropped) {
            Optional<Entity> toKill = mod.getEntityTracker().getClosestEntity(EnderMan.class);
            if (toKill.isPresent() && mod.getEntityTracker().isEntityReachable(toKill.get())) {
               return new KillEndermanTask(count);
            }
         }

         this.setDebugState("Waiting for endermen to spawn... ");
         return null;
      }
   }

   private int getTargetBeds(PlayerEngineController mod) {
      boolean needsToSetSpawn = config.placeSpawnNearEndPortal
         && !this.spawnSetNearPortal(mod, this.endPortalCenterLocation)
         && !isTaskRunning(mod, this.setBedSpawnTask);
      int bedsInEnd = Arrays.stream(ItemHelper.BED).mapToInt(bed -> this.cachedEndItemDrops.getOrDefault(bed, 0)).sum();
      int targetBeds = config.requiredBeds + (needsToSetSpawn ? 1 : 0) - bedsInEnd;
      Debug.logInternal("needsToSetSpawn: " + needsToSetSpawn);
      Debug.logInternal("bedsInEnd: " + bedsInEnd);
      Debug.logInternal("targetBeds: " + targetBeds);
      return targetBeds;
   }

   private boolean needsBeds(PlayerEngineController mod) {
      int totalEndItems = 0;

      for (Item bed : ItemHelper.BED) {
         totalEndItems += this.cachedEndItemDrops.getOrDefault(bed, 0);
      }

      int itemCount = mod.getItemStorage().getItemCount(ItemHelper.BED);
      int targetBeds = this.getTargetBeds(mod);
      Debug.logInternal("Total End Items: " + totalEndItems);
      Debug.logInternal("Item Count: " + itemCount);
      Debug.logInternal("Target Beds: " + targetBeds);
      boolean needsBeds = itemCount + totalEndItems < targetBeds;
      Debug.logInternal("Needs Beds: " + needsBeds);
      return needsBeds;
   }

   private Task getBedTask(PlayerEngineController mod) {
      int targetBeds = this.getTargetBeds(mod);
      if (!mod.getItemStorage().hasItem(Items.SHEARS) && !this.anyBedsFound(mod)) {
         Debug.logInternal("Getting shears.");
         return TaskCatalogue.getItemTask(Items.SHEARS, 1);
      } else {
         Debug.logInternal("Getting beds.");
         return TaskCatalogue.getItemTask("bed", targetBeds);
      }
   }

   private boolean anyBedsFound(PlayerEngineController mod) {
      BlockScanner blockTracker = mod.getBlockScanner();
      EntityTracker entityTracker = mod.getEntityTracker();
      boolean bedsFoundInBlocks = blockTracker.anyFound(ItemHelper.itemsToBlocks(ItemHelper.BED));
      boolean bedsFoundInEntities = entityTracker.itemDropped(ItemHelper.BED);
      if (bedsFoundInBlocks) {
         Debug.logInternal("Beds found in blocks");
      }

      if (bedsFoundInEntities) {
         Debug.logInternal("Beds found in entities");
      }

      return bedsFoundInBlocks || bedsFoundInEntities;
   }

   private BlockPos doSimpleSearchForEndPortal(PlayerEngineController mod) {
      List<BlockPos> frames = mod.getBlockScanner().getKnownLocations(Blocks.END_PORTAL_FRAME);
      if (frames.size() >= 12) {
         Vec3 average = frames.stream()
            .reduce(
               Vec3.ZERO,
               (accum, bpos) -> accum.add((int)Math.round(bpos.getX() + 0.5), (int)Math.round(bpos.getY() + 0.5), (int)Math.round(bpos.getZ() + 0.5)),
               Vec3::add
            )
            .scale(1.0 / frames.size());
         mod.log("Average Position: " + average);
         return new BlockPos(new Vec3i((int)average.x, (int)average.y, (int)average.z));
      } else {
         Debug.logInternal("Not enough frames");
         return null;
      }
   }

   private int getFilledPortalFrames(PlayerEngineController mod, BlockPos endPortalCenter) {
      if (endPortalCenter == null) {
         return 0;
      } else {
         List<BlockPos> frameBlocks = getFrameBlocks(mod, endPortalCenter);
         if (frameBlocks.stream().allMatch(blockPos -> mod.getChunkTracker().isChunkLoaded(blockPos))) {
            this.cachedFilledPortalFrames = frameBlocks.stream().mapToInt(blockPos -> {
               boolean isFilled = isEndPortalFrameFilled(mod, blockPos);
               if (isFilled) {
                  Debug.logInternal("Portal frame at " + blockPos + " is filled.");
               } else {
                  Debug.logInternal("Portal frame at " + blockPos + " is not filled.");
               }

               return isFilled ? 1 : 0;
            }).sum();
         }

         return this.cachedFilledPortalFrames;
      }
   }

   private boolean canBeLootablePortalChest(PlayerEngineController mod, BlockPos blockPos) {
      return mod.getWorld().getBlockState(blockPos.above()).getBlock() != Blocks.WATER && blockPos.getY() >= 50;
   }

   private Task getEyesOfEnderTask(PlayerEngineController mod, int targetEyes) {
      if (mod.getEntityTracker().itemDropped(Items.ENDER_EYE)) {
         this.setDebugState("Picking up Dropped Eyes");
         return new PickupDroppedItemTask(Items.ENDER_EYE, targetEyes);
      } else {
         int eyeCount = mod.getItemStorage().getItemCount(Items.ENDER_EYE);
         int blazePowderCount = mod.getItemStorage().getItemCount(Items.BLAZE_POWDER);
         int blazeRodCount = mod.getItemStorage().getItemCount(Items.BLAZE_ROD);
         int blazeRodTarget = (int)Math.ceil((targetEyes - eyeCount - blazePowderCount) / 2.0);
         int enderPearlTarget = targetEyes - eyeCount;
         boolean needsBlazeRods = blazeRodCount < blazeRodTarget;
         boolean needsBlazePowder = eyeCount + blazePowderCount < targetEyes;
         boolean needsEnderPearls = mod.getItemStorage().getItemCount(Items.ENDER_PEARL) < enderPearlTarget;
         if (needsBlazePowder && !needsBlazeRods) {
            this.setDebugState("Crafting blaze powder");
            return TaskCatalogue.getItemTask(Items.BLAZE_POWDER, targetEyes - eyeCount);
         } else if (!needsBlazePowder && !needsEnderPearls) {
            this.setDebugState("Crafting Ender Eyes");
            return TaskCatalogue.getItemTask(Items.ENDER_EYE, targetEyes);
         } else {
            switch (WorldHelper.getCurrentDimension(mod)) {
               case OVERWORLD:
                  PriorityTask toGather = null;
                  double maxPriority = 0.0;
                  if (!this.gatherResources.isEmpty()) {
                     if (!this.forcedTaskTimer.elapsed()
                        && isTaskRunning(mod, this.lastTask)
                        && this.lastGather != null
                        && this.lastGather.calculatePriority(mod) > 0.0) {
                        return this.lastTask;
                     }

                     if (!this.changedTaskTimer.elapsed() && this.lastTask != null && !this.lastGather.bypassForceCooldown && isTaskRunning(mod, this.lastTask)
                        )
                      {
                        return this.lastTask;
                     }

                     if (isTaskRunning(mod, this.lastTask) && this.lastGather != null && this.lastGather.shouldForce()) {
                        return this.lastTask;
                     }

                     for (PriorityTask gatherResource : this.gatherResources) {
                        double priority = gatherResource.calculatePriority(mod);
                        if (priority > maxPriority) {
                           maxPriority = priority;
                           toGather = gatherResource;
                        }
                     }
                  }

                  if (toGather != null) {
                     boolean sameTask = this.lastGather == toGather;
                     this.setDebugState("Priority: " + String.format(Locale.US, "%.2f", maxPriority) + ", " + toGather);
                     if (!sameTask
                        && this.prevLastGather == toGather
                        && this.lastTask != null
                        && this.lastGather.calculatePriority(mod) > 0.0
                        && isTaskRunning(mod, this.lastTask)) {
                        mod.logWarning("might be stuck or switching too much, forcing current resource for a bit more");
                        this.changedTaskTimer.reset();
                        this.prevLastGather = null;
                        this.setDebugState("Priority: FORCED, " + this.lastGather);
                        return this.lastTask;
                     } else if (sameTask && toGather.canCache()) {
                        return this.lastTask;
                     } else {
                        if (!sameTask) {
                           this.taskChanges.add(0, new BeatMinecraftTask.TaskChange(this.lastGather, toGather, mod.getPlayer().blockPosition()));
                        }

                        if (this.taskChanges.size() >= 3 && !sameTask) {
                           BeatMinecraftTask.TaskChange t1 = this.taskChanges.get(0);
                           BeatMinecraftTask.TaskChange t2 = this.taskChanges.get(1);
                           BeatMinecraftTask.TaskChange t3 = this.taskChanges.get(2);
                           if (t1.original == t2.interrupt && t1.pos.closerThan(t3.pos, 5.0) && t3.original == t1.interrupt) {
                              this.forcedTaskTimer.reset();
                              mod.logWarning("Probably stuck! Forcing timer...");
                              this.taskChanges.clear();
                              return this.lastTask;
                           }

                           if (this.taskChanges.size() > 3) {
                              this.taskChanges.remove(this.taskChanges.size() - 1);
                           }
                        }

                        this.prevLastGather = this.lastGather;
                        this.lastGather = toGather;
                        Task task = toGather.getTask(mod);
                        if (!sameTask) {
                           if (this.lastTask instanceof SmeltInFurnaceTask
                              && !(task instanceof SmeltInFurnaceTask)
                              && !mod.getItemStorage().hasItem(Items.FURNACE)) {
                              this.pickupFurnace = true;
                              this.lastGather = null;
                              this.lastTask = null;
                              return null;
                           }

                           if (this.lastTask instanceof SmeltInSmokerTask
                              && !(task instanceof SmeltInSmokerTask)
                              && !mod.getItemStorage().hasItem(Items.SMOKER)) {
                              this.pickupSmoker = true;
                              this.lastGather = null;
                              this.lastTask = null;
                              return null;
                           }

                           if (this.lastTask != null && task != null && !toGather.needCraftingOnStart(mod)) {
                              this.pickupCrafting = true;
                              this.lastGather = null;
                              this.lastTask = null;
                              return null;
                           }
                        }

                        this.lastTask = task;
                        this.changedTaskTimer.reset();
                        return task;
                     }
                  } else if (this.needsBuildingMaterials(mod)) {
                     this.setDebugState("Collecting building materials.");
                     return this.buildMaterialsTask;
                  } else {
                     this.setDebugState("Going to Nether");
                     ItemStorageTracker itemStorageTracker1 = mod.getItemStorage();
                     if (itemStorageTracker1.getItemCount(Items.DIAMOND) >= 3 && !itemStorageTracker1.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
                        return TaskCatalogue.getItemTask(Items.DIAMOND_PICKAXE, 1);
                     } else if (itemStorageTracker1.getItemCount(Items.IRON_INGOT) >= 3
                        && !itemStorageTracker1.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
                        return TaskCatalogue.getItemTask(Items.IRON_PICKAXE, 1);
                     } else if (!itemStorageTracker1.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE)) {
                        return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
                     } else if (!itemStorageTracker1.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE)) {
                        return TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
                     } else {
                        this.gatherResources.clear();
                        if (!(this.lastTask instanceof DefaultGoToDimensionTask)) {
                           this.goToNetherTask = new DefaultGoToDimensionTask(Dimension.NETHER);
                        }

                        this.lastTask = this.goToNetherTask;
                        return this.goToNetherTask;
                     }
                  }
               case NETHER:
                  if (isTaskRunning(mod, this.safeNetherPortalTask)) {
                     return this.safeNetherPortalTask;
                  } else if (mod.getPlayer().getPortalCooldown() != 0 && this.safeNetherPortalTask == null) {
                     this.safeNetherPortalTask = new SafeNetherPortalTask();
                     return this.safeNetherPortalTask;
                  } else {
                     mod.getInputControls().release(Input.MOVE_FORWARD);
                     mod.getInputControls().release(Input.MOVE_LEFT);
                     mod.getInputControls().release(Input.SNEAK);
                     BlockPos pos = mod.getPlayer().getOnPos();
                     if (this.escaped
                        || !mod.getWorld().getBlockState(pos).getBlock().equals(Blocks.SOUL_SAND)
                        || !mod.getWorld().getBlockState(pos.east()).getBlock().equals(Blocks.OBSIDIAN)
                           && !mod.getWorld().getBlockState(pos.west()).getBlock().equals(Blocks.OBSIDIAN)
                           && !mod.getWorld().getBlockState(pos.south()).getBlock().equals(Blocks.OBSIDIAN)
                           && !mod.getWorld().getBlockState(pos.north()).getBlock().equals(Blocks.OBSIDIAN)) {
                        if (!this.escaped) {
                           this.escaped = true;
                           mod.getInputControls().release(Input.CLICK_LEFT);
                        }

                        ItemStorageTracker itemStorage = mod.getItemStorage();
                        if (itemStorage.getItemCount(Items.DIAMOND) >= 3 && !itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
                           return TaskCatalogue.getItemTask(Items.DIAMOND_PICKAXE, 1);
                        }

                        if (itemStorage.getItemCount(Items.IRON_INGOT) >= 3 && !itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
                           return TaskCatalogue.getItemTask(Items.IRON_PICKAXE, 1);
                        }

                        if (!itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE)) {
                           return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
                        }

                        if (!itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE)) {
                           return TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
                        }

                        if (mod.getItemStorage().getItemCount(Items.BLAZE_ROD) * 2
                              + mod.getItemStorage().getItemCount(Items.BLAZE_POWDER)
                              + mod.getItemStorage().getItemCount(Items.ENDER_EYE)
                           >= 14) {
                           this.hasRods = true;
                        }

                        double rodDistance = mod.getBlockScanner().distanceToClosest(Blocks.NETHER_BRICKS);
                        double pearlDistance = mod.getBlockScanner()
                           .distanceToClosest(Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.WARPED_HYPHAE, Blocks.WARPED_NYLIUM);
                        if (pearlDistance == Double.POSITIVE_INFINITY && rodDistance == Double.POSITIVE_INFINITY) {
                           this.setDebugState("Neither fortress or warped forest found... wandering");
                           if (isTaskRunning(mod, this.searchTask)) {
                              return this.searchTask;
                           }

                           this.searchTask = new SearchChunkForBlockTask(
                              Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.WARPED_HYPHAE, Blocks.WARPED_NYLIUM, Blocks.NETHER_BRICKS
                           );
                           return this.searchTask;
                        }

                        if ((!(rodDistance < pearlDistance) || this.hasRods || this.gettingPearls) && needsEnderPearls) {
                           if (!mod.getBlockScanner().anyFound(Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.WARPED_HYPHAE, Blocks.WARPED_NYLIUM)) {
                              return new TimeoutWanderTask();
                           }

                           if (!this.gotToBiome
                              && (
                                 this.biomePos == null
                                    || !WorldHelper.inRangeXZ(mod.getPlayer(), this.biomePos, 30.0)
                                    || !mod.getBaritone().getPathingBehavior().isSafeToCancel()
                              )) {
                              if (this.biomePos != null) {
                                 this.setDebugState("Going to biome");
                                 return new GetWithinRangeOfBlockTask(this.biomePos, 20);
                              }

                              this.gettingPearls = true;
                              this.setDebugState("Getting Ender Pearls");
                              Optional<BlockPos> closestBlock = mod.getBlockScanner()
                                 .getNearestBlock(Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.WARPED_HYPHAE, Blocks.WARPED_NYLIUM);
                              if (closestBlock.isPresent()) {
                                 this.biomePos = closestBlock.get();
                              } else {
                                 this.setDebugState("biome not found, wandering");
                              }

                              return new TimeoutWanderTask();
                           }

                           this.gotToBiome = true;
                           return this.getEnderPearlTask(mod, enderPearlTarget);
                        }

                        if (!this.gotToFortress) {
                           if (mod.getBlockScanner().anyFoundWithinDistance(5.0, Blocks.NETHER_BRICKS)) {
                              this.gotToFortress = true;
                           } else {
                              if (!mod.getBlockScanner().anyFound(Blocks.NETHER_BRICKS)) {
                                 this.setDebugState("Searching for fortress");
                                 return new TimeoutWanderTask();
                              }

                              if (WorldHelper.inRangeXZ(
                                 mod.getPlayer().position(), WorldHelper.toVec3d(mod.getBlockScanner().getNearestBlock(Blocks.NETHER_BRICKS).get()), 2.0
                              )) {
                                 this.setDebugState("trying to get to fortress");
                                 return new GetToBlockTask(mod.getBlockScanner().getNearestBlock(Blocks.NETHER_BRICKS).get());
                              }

                              this.setDebugState("Getting close to fortress");
                              if ((
                                    this.cachedFortressTask != null
                                          && !this.fortressTimer.elapsed()
                                          && mod.getPlayer().position().distanceTo(WorldHelper.toVec3d(this.cachedFortressTask.blockPos)) - 1.0
                                             > this.prevPos.distManhattan(this.cachedFortressTask.blockPos) / 2.0
                                       || !mod.getBaritone().getPathingBehavior().isSafeToCancel()
                                 )
                                 && this.cachedFortressTask != null) {
                                 mod.log(
                                    mod.getPlayer().position().distanceTo(WorldHelper.toVec3d(this.cachedFortressTask.blockPos))
                                       + " : "
                                       + mod.getPlayer().position().distanceTo(WorldHelper.toVec3d(this.cachedFortressTask.blockPos))
                                 );
                                 return this.cachedFortressTask;
                              }

                              if (this.resetFortressTask) {
                                 this.resetFortressTask = false;
                                 return null;
                              }

                              this.resetFortressTask = true;
                              this.fortressTimer.reset();
                              mod.log("new");
                              this.prevPos = mod.getPlayer().blockPosition();
                              BlockPos p = mod.getBlockScanner().getNearestBlock(Blocks.NETHER_BRICKS).get();
                              int distance = (int)(mod.getPlayer().position().distanceTo(WorldHelper.toVec3d(p)) / 2.0);
                              if (this.cachedFortressTask != null) {
                                 distance = Math.min(this.cachedFortressTask.range - 1, distance);
                              }

                              if (distance >= 0) {
                                 this.cachedFortressTask = new GetWithinRangeOfBlockTask(p, distance);
                                 return this.cachedFortressTask;
                              }

                              this.gotToFortress = true;
                           }
                        }

                        this.setDebugState("Getting Blaze Rods");
                        return this.getBlazeRodsTask(mod, blazeRodTarget);
                     }

                     LookHelper.lookAt(mod, pos);
                     mod.getInputControls().hold(Input.CLICK_LEFT);
                     return null;
                  }
               case END:
                  throw new UnsupportedOperationException("You're in the end. Don't collect eyes here.");
               default:
                  return null;
            }
         }
      }
   }

   static {
      ConfigHelper.loadConfig("configs/beat_minecraft.json", BeatMinecraftConfig::new, BeatMinecraftConfig.class, newConfig -> config = newConfig);
   }

   private class DistanceOrePriorityCalculator extends DistanceItemPriorityCalculator {
      private final Item oreItem;

      public DistanceOrePriorityCalculator(
         Item oreItem, double multiplier, double unneededMultiplier, double unneededDistanceThreshold, int minCount, int maxCount
      ) {
         super(multiplier, unneededMultiplier, unneededDistanceThreshold, minCount, maxCount);
         this.oreItem = oreItem;
      }

      @Override
      public void update(int count) {
         super.update(BeatMinecraftTask.getCountWithCraftedFromOre(BeatMinecraftTask.this.mod, this.oreItem));
      }
   }

   private record TaskChange(PriorityTask original, PriorityTask interrupt, BlockPos pos) {
   }
}
