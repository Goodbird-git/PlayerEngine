package adris.altoclef.util.helpers;

import adris.altoclef.AltoClefController;
import adris.altoclef.multiversion.BlockTagVer;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.util.WoodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.DyeColor;

public class ItemHelper {
  public static final Item[] SAPLINGS = new Item[] { Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING, Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING, Items.MANGROVE_PROPAGULE, Items.CHERRY_SAPLING };
  
  public static final Block[] SAPLING_SOURCES = new Block[] { Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.MANGROVE_PROPAGULE, Blocks.CHERRY_LEAVES };
  
  public static final Item[] HOSTILE_MOB_DROPS = new Item[] { 
      Items.BLAZE_ROD, Items.FEATHER, Items.CHICKEN, Items.COOKED_CHICKEN, Items.ROTTEN_FLESH, Items.ZOMBIE_HEAD, Items.GUNPOWDER, Items.CREEPER_HEAD, Items.TOTEM_OF_UNDYING, Items.EMERALD, 
      Items.PORKCHOP, Items.COOKED_PORKCHOP, Items.LEATHER, Items.MAGMA_CREAM, Items.PHANTOM_MEMBRANE, Items.ARROW, Items.SADDLE, Items.SHULKER_SHELL, Items.BONE, Items.SKELETON_SKULL, 
      Items.SLIME_BALL, Items.STRING, Items.SPIDER_EYE, Items.SCULK_CATALYST, Items.GLASS_BOTTLE, Items.GLOWSTONE_DUST, Items.REDSTONE, Items.STICK, Items.SUGAR, Items.POTION, 
      Items.NETHER_STAR, Items.COAL, Items.WITHER_SKELETON_SKULL, Items.GHAST_TEAR, Items.IRON_INGOT, Items.CARROT, Items.POTATO, Items.BAKED_POTATO, Items.COPPER_INGOT };
  
  public static final Item[] DIRTS = new Item[] { Items.DIRT, Items.DIRT_PATH, Items.COARSE_DIRT, Items.ROOTED_DIRT };
  
  public static final Item[] PLANKS = new Item[] { 
      Items.ACACIA_PLANKS, Items.BIRCH_PLANKS, Items.CRIMSON_PLANKS, Items.DARK_OAK_PLANKS, Items.OAK_PLANKS, Items.JUNGLE_PLANKS, Items.SPRUCE_PLANKS, Items.WARPED_PLANKS, Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, 
      Items.BAMBOO_PLANKS };
  
  public static final Item[] LEAVES = new Item[] { Items.ACACIA_LEAVES, Items.BIRCH_LEAVES, Items.DARK_OAK_LEAVES, Items.OAK_LEAVES, Items.JUNGLE_LEAVES, Items.SPRUCE_LEAVES, Items.MANGROVE_LEAVES, Items.CHERRY_LEAVES };
  
  public static final Item[] WOOD = new Item[] { Items.ACACIA_WOOD, Items.BIRCH_WOOD, Items.CRIMSON_HYPHAE, Items.DARK_OAK_WOOD, Items.OAK_WOOD, Items.JUNGLE_WOOD, Items.SPRUCE_WOOD, Items.WARPED_HYPHAE, Items.MANGROVE_WOOD };
  
  public static final Item[] WOOD_BUTTON = new Item[] { 
      Items.ACACIA_BUTTON, Items.BIRCH_BUTTON, Items.CRIMSON_BUTTON, Items.DARK_OAK_BUTTON, Items.OAK_BUTTON, Items.JUNGLE_BUTTON, Items.SPRUCE_BUTTON, Items.WARPED_BUTTON, Items.MANGROVE_BUTTON, Items.BAMBOO_BUTTON, 
      Items.CHERRY_BUTTON };
  
  public static final Item[] WOOD_SIGN = new Item[] { 
      Items.ACACIA_SIGN, Items.BIRCH_SIGN, Items.CRIMSON_SIGN, Items.DARK_OAK_SIGN, Items.OAK_SIGN, Items.JUNGLE_SIGN, Items.SPRUCE_SIGN, Items.WARPED_SIGN, Items.MANGROVE_SIGN, Items.BAMBOO_SIGN, 
      Items.CHERRY_SIGN };
  
  public static final Item[] WOOD_HANGING_SIGN = new Item[] { 
      Items.ACACIA_HANGING_SIGN, Items.BIRCH_HANGING_SIGN, Items.CRIMSON_HANGING_SIGN, Items.DARK_OAK_HANGING_SIGN, Items.OAK_HANGING_SIGN, Items.JUNGLE_HANGING_SIGN, Items.SPRUCE_HANGING_SIGN, Items.WARPED_HANGING_SIGN, Items.MANGROVE_HANGING_SIGN, Items.BAMBOO_HANGING_SIGN, 
      Items.CHERRY_HANGING_SIGN };
  
  public static final Item[] WOOD_PRESSURE_PLATE = new Item[] { 
      Items.ACACIA_PRESSURE_PLATE, Items.BIRCH_PRESSURE_PLATE, Items.CRIMSON_PRESSURE_PLATE, Items.DARK_OAK_PRESSURE_PLATE, Items.OAK_PRESSURE_PLATE, Items.JUNGLE_PRESSURE_PLATE, Items.SPRUCE_PRESSURE_PLATE, Items.WARPED_PRESSURE_PLATE, Items.MANGROVE_PRESSURE_PLATE, Items.BAMBOO_PRESSURE_PLATE, 
      Items.CHERRY_PRESSURE_PLATE };
  
  public static final Item[] WOOD_FENCE = new Item[] { 
      Items.ACACIA_FENCE, Items.BIRCH_FENCE, Items.DARK_OAK_FENCE, Items.OAK_FENCE, Items.JUNGLE_FENCE, Items.SPRUCE_FENCE, Items.CRIMSON_FENCE, Items.WARPED_FENCE, Items.MANGROVE_FENCE, Items.BAMBOO_FENCE, 
      Items.CHERRY_FENCE };
  
  public static final Item[] WOOD_FENCE_GATE = new Item[] { 
      Items.ACACIA_FENCE_GATE, Items.BIRCH_FENCE_GATE, Items.DARK_OAK_FENCE_GATE, Items.OAK_FENCE_GATE, Items.JUNGLE_FENCE_GATE, Items.SPRUCE_FENCE_GATE, Items.CRIMSON_FENCE_GATE, Items.WARPED_FENCE_GATE, Items.MANGROVE_FENCE_GATE, Items.BAMBOO_FENCE_GATE, 
      Items.CHERRY_FENCE_GATE };
  
  public static final Item[] WOOD_BOAT = new Item[] { Items.ACACIA_BOAT, Items.BIRCH_BOAT, Items.DARK_OAK_BOAT, Items.OAK_BOAT, Items.JUNGLE_BOAT, Items.SPRUCE_BOAT, Items.MANGROVE_BOAT, Items.CHERRY_BOAT };
  
  public static final Item[] WOOD_DOOR = new Item[] { 
      Items.ACACIA_DOOR, Items.BIRCH_DOOR, Items.CRIMSON_DOOR, Items.DARK_OAK_DOOR, Items.OAK_DOOR, Items.JUNGLE_DOOR, Items.SPRUCE_DOOR, Items.WARPED_DOOR, Items.MANGROVE_DOOR, Items.BAMBOO_DOOR, 
      Items.CHERRY_DOOR };
  
  public static final Item[] WOOD_SLAB = new Item[] { 
      Items.ACACIA_SLAB, Items.BIRCH_SLAB, Items.CRIMSON_SLAB, Items.DARK_OAK_SLAB, Items.OAK_SLAB, Items.JUNGLE_SLAB, Items.SPRUCE_SLAB, Items.WARPED_SLAB, Items.MANGROVE_SLAB, Items.BAMBOO_SLAB, 
      Items.CHERRY_SLAB };
  
  public static final Item[] WOOD_STAIRS = new Item[] { 
      Items.ACACIA_STAIRS, Items.BIRCH_STAIRS, Items.CRIMSON_STAIRS, Items.DARK_OAK_STAIRS, Items.OAK_STAIRS, Items.JUNGLE_STAIRS, Items.SPRUCE_STAIRS, Items.WARPED_STAIRS, Items.MANGROVE_STAIRS, Items.BAMBOO_STAIRS, 
      Items.CHERRY_STAIRS };
  
  public static final Item[] WOOD_TRAPDOOR = new Item[] { 
      Items.ACACIA_TRAPDOOR, Items.BIRCH_TRAPDOOR, Items.CRIMSON_TRAPDOOR, Items.DARK_OAK_TRAPDOOR, Items.OAK_TRAPDOOR, Items.JUNGLE_TRAPDOOR, Items.SPRUCE_TRAPDOOR, Items.WARPED_TRAPDOOR, Items.MANGROVE_TRAPDOOR, Items.BAMBOO_TRAPDOOR, 
      Items.CHERRY_TRAPDOOR };
  
  public static final Item[] LOG = new Item[] { 
      Items.ACACIA_LOG, Items.BIRCH_LOG, Items.DARK_OAK_LOG, Items.OAK_LOG, Items.JUNGLE_LOG, Items.SPRUCE_LOG, Items.ACACIA_WOOD, Items.BIRCH_WOOD, Items.DARK_OAK_WOOD, Items.OAK_WOOD, 
      Items.JUNGLE_WOOD, Items.SPRUCE_WOOD, Items.STRIPPED_ACACIA_LOG, Items.STRIPPED_BIRCH_LOG, Items.STRIPPED_DARK_OAK_LOG, Items.STRIPPED_OAK_LOG, Items.STRIPPED_JUNGLE_LOG, Items.STRIPPED_SPRUCE_LOG, Items.STRIPPED_ACACIA_WOOD, Items.STRIPPED_BIRCH_WOOD, 
      Items.STRIPPED_DARK_OAK_WOOD, Items.STRIPPED_OAK_WOOD, Items.STRIPPED_JUNGLE_WOOD, Items.STRIPPED_SPRUCE_WOOD, Items.CRIMSON_STEM, Items.WARPED_STEM, Items.CRIMSON_HYPHAE, Items.WARPED_HYPHAE, Items.STRIPPED_CRIMSON_STEM, Items.STRIPPED_WARPED_STEM, 
      Items.STRIPPED_CRIMSON_HYPHAE, Items.STRIPPED_WARPED_HYPHAE, Items.MANGROVE_LOG, Items.MANGROVE_WOOD, Items.STRIPPED_MANGROVE_LOGS, Items.STRIPPED_MANGROVE_WOOD, Items.CHERRY_LOG, Items.CHERRY_WOOD, Items.STRIPPED_CHERRY_LOG, Items.STRIPPED_CHERRY_WOOD };
  
  public static final Item[] STRIPPED_LOGS = new Item[] { Items.STRIPPED_ACACIA_LOG, Items.STRIPPED_BIRCH_LOG, Items.STRIPPED_DARK_OAK_LOG, Items.STRIPPED_OAK_LOG, Items.STRIPPED_JUNGLE_LOG, Items.STRIPPED_SPRUCE_LOG, Items.STRIPPED_CRIMSON_STEM, Items.STRIPPED_WARPED_STEM, Items.STRIPPED_MANGROVE_LOGS, Items.STRIPPED_CHERRY_LOG };
  
  public static final Item[] STRIPPABLE_LOGS = new Item[] { Items.ACACIA_LOG, Items.BIRCH_LOG, Items.DARK_OAK_LOG, Items.OAK_LOG, Items.JUNGLE_LOG, Items.SPRUCE_LOG, Items.CRIMSON_STEM, Items.WARPED_STEM, Items.MANGROVE_LOG, Items.CHERRY_LOG };
  
  public static final Item[] DYE = new Item[] { 
      Items.WHITE_DYE, Items.BLACK_DYE, Items.BLUE_DYE, Items.BROWN_DYE, Items.CYAN_DYE, Items.GRAY_DYE, Items.GREEN_DYE, Items.LIGHT_BLUE_DYE, Items.LIGHT_GRAY_DYE, Items.LIME_DYE, 
      Items.MAGENTA_DYE, Items.ORANGE_DYE, Items.PINK_DYE, Items.PURPLE_DYE, Items.RED_DYE, Items.YELLOW_DYE };
  
  public static final Item[] WOOL = new Item[] { 
      Items.WHITE_WOOL, Items.BLACK_WOOL, Items.BLUE_WOOL, Items.BROWN_WOOL, Items.CYAN_WOOL, Items.GRAY_WOOL, Items.GREEN_WOOL, Items.LIGHT_BLUE_WOOL, Items.LIGHT_GRAY_WOOL, Items.LIME_WOOL, 
      Items.MAGENTA_WOOL, Items.ORANGE_WOOL, Items.PINK_WOOL, Items.PURPLE_WOOL, Items.RED_WOOL, Items.YELLOW_WOOL };
  
  public static final Item[] BED = new Item[] { 
      Items.WHITE_BED, Items.BLACK_BED, Items.BLUE_BED, Items.BROWN_BED, Items.CYAN_BED, Items.GRAY_BED, Items.GREEN_BED, Items.LIGHT_BLUE_BED, Items.LIGHT_GRAY_BED, Items.LIME_BED, 
      Items.MAGENTA_BED, Items.ORANGE_BED, Items.PINK_BED, Items.PURPLE_BED, Items.RED_BED, Items.YELLOW_BED };
  
  public static final Item[] CARPET = new Item[] { 
      Items.WHITE_CARPET, Items.BLACK_CARPET, Items.BLUE_CARPET, Items.BROWN_CARPET, Items.CYAN_CARPET, Items.GRAY_CARPET, Items.GREEN_CARPET, Items.LIGHT_BLUE_CARPET, Items.LIGHT_GRAY_CARPET, Items.LIME_CARPET, 
      Items.MAGENTA_CARPET, Items.ORANGE_CARPET, Items.PINK_CARPET, Items.PURPLE_CARPET, Items.RED_CARPET, Items.YELLOW_CARPET };
  
  public static final Item[] SHULKER_BOXES = new Item[] { 
      Items.WHITE_SHULKER_BOX, Items.BLACK_SHULKER_BOX, Items.BLUE_SHULKER_BOX, Items.BROWN_SHULKER_BOX, Items.CYAN_SHULKER_BOX, Items.GRAY_SHULKER_BOX, Items.GREEN_SHULKER_BOX, Items.LIGHT_BLUE_SHULKER_BOX, Items.LIGHT_GRAY_SHULKER_BOX, Items.LIME_SHULKER_BOX, 
      Items.MAGENTA_SHULKER_BOX, Items.ORANGE_SHULKER_BOX, Items.PINK_SHULKER_BOX, Items.PURPLE_SHULKER_BOX, Items.RED_SHULKER_BOX, Items.YELLOW_SHULKER_BOX };
  
  public static final Item[] FLOWER = new Item[] { 
      Items.ALLIUM, Items.AZURE_BLUET, Items.BLUE_ORCHID, Items.CORNFLOWER, Items.DANDELION, Items.LILAC, Items.LILY_OF_THE_VALLEY, Items.ORANGE_TULIP, Items.OXEYE_DAISY, Items.PINK_TULIP, 
      Items.POPPY, Items.PEONY, Items.RED_TULIP, Items.ROSE_BUSH, Items.SUNFLOWER, Items.WHITE_TULIP };
  
  public static final Item[] LEATHER_ARMORS = new Item[] { Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_HELMET, Items.LEATHER_BOOTS };
  
  public static final Item[] GOLDEN_ARMORS = new Item[] { Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_HELMET, Items.GOLDEN_BOOTS };
  
  public static final Item[] IRON_ARMORS = new Item[] { Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_HELMET, Items.IRON_BOOTS };
  
  public static final Item[] DIAMOND_ARMORS = new Item[] { Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_HELMET, Items.DIAMOND_BOOTS };
  
  public static final Item[] NETHERITE_ARMORS = new Item[] { Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_HELMET, Items.NETHERITE_BOOTS };
  
  public static final Item[] WOODEN_TOOLS = new Item[] { Items.WOODEN_PICKAXE, Items.WOODEN_SHOVEL, Items.WOODEN_SWORD, Items.WOODEN_AXE, Items.WOODEN_HOE };
  
  public static final Item[] STONE_TOOLS = new Item[] { Items.STONE_PICKAXE, Items.STONE_SHOVEL, Items.STONE_SWORD, Items.STONE_AXE, Items.STONE_HOE };
  
  public static final Item[] IRON_TOOLS = new Item[] { Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_HOE };
  
  public static final Item[] GOLDEN_TOOLS = new Item[] { Items.GOLDEN_PICKAXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_SWORD, Items.GOLDEN_AXE, Items.GOLDEN_HOE };
  
  public static final Item[] DIAMOND_TOOLS = new Item[] { Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_HOE };
  
  public static final Item[] NETHERITE_TOOLS = new Item[] { Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_HOE };
  
  public static final Block[] WOOD_SIGNS_ALL = new Block[] { 
      Blocks.ACACIA_SIGN, Blocks.BIRCH_SIGN, Blocks.DARK_OAK_SIGN, Blocks.OAK_SIGN, Blocks.JUNGLE_SIGN, Blocks.SPRUCE_SIGN, Blocks.ACACIA_WALL_SIGN, Blocks.BIRCH_WALL_SIGN, Blocks.DARK_OAK_WALL_SIGN, Blocks.OAK_WALL_SIGN, 
      Blocks.JUNGLE_WALL_SIGN, Blocks.SPRUCE_WALL_SIGN, Blocks.MANGROVE_SIGN, Blocks.MANGROVE_WALL_SIGN, Blocks.BAMBOO_SIGN, Blocks.BAMBOO_WALL_SIGN, Blocks.CHERRY_SIGN, Blocks.CHERRY_WALL_SIGN };
  
  private static final Map<Item, Item> logToPlanks = (Map<Item, Item>)new Object();
  
  private static final Map<Item, Item> planksToLogs = (Map<Item, Item>)new Object();
  
  private static final Map<Item, Item> strippedToLogs = (Map<Item, Item>)new Object();
  
  private static final Map<MapColor, ColorfulItems> colorMap = (Map<MapColor, ColorfulItems>)new Object();
  
  private static final Map<WoodType, WoodItems> woodMap = (Map<WoodType, WoodItems>)new Object();
  
  public static final HashMap<Item, Item> cookableFoodMap = (HashMap<Item, Item>)new Object();
  
  public static final Item[] RAW_FOODS;
  
  public static final Item[] COOKED_FOODS;
  
  static {
    RAW_FOODS = (Item[])cookableFoodMap.keySet().toArray(x$0 -> new Item[x$0]);
    COOKED_FOODS = (Item[])cookableFoodMap.values().toArray(x$0 -> new Item[x$0]);
  }
  
  private static Map<Item, Integer> fuelTimeMap = null;
  
  public static String stripItemName(Item item) {
    String[] possibilities = { "item.minecraft.", "block.minecraft." };
    for (String possible : possibilities) {
      if (item.getTranslationKey().startsWith(possible))
        return item.getTranslationKey().substring(possible.length()); 
    } 
    return item.getTranslationKey();
  }
  
  public static Item[] blocksToItems(Block[] blocks) {
    Item[] result = new Item[blocks.length];
    for (int i = 0; i < blocks.length; i++)
      result[i] = blocks[i].asItem(); 
    return result;
  }
  
  public static Block[] itemsToBlocks(Item[] items) {
    ArrayList<Block> result = new ArrayList<>();
    for (Item item : items) {
      if (item instanceof net.minecraft.item.BlockItem) {
        Block b = Block.getBlockFromItem(item);
        if (b != null && b != Blocks.AIR)
          result.add(b); 
      } 
    } 
    return (Block[])result.toArray(x$0 -> new Block[x$0]);
  }
  
  public static Item logToPlanks(Item logItem) {
    return logToPlanks.getOrDefault(logItem, null);
  }
  
  public static Item planksToLog(Item plankItem) {
    return planksToLogs.getOrDefault(plankItem, null);
  }
  
  public static Item strippedToLogs(Item logItem) {
    return strippedToLogs.getOrDefault(logItem, null);
  }
  
  public static ColorfulItems getColorfulItems(MapColor color) {
    return colorMap.get(color);
  }
  
  public static ColorfulItems getColorfulItems(DyeColor color) {
    return getColorfulItems(color.getMapColor());
  }
  
  public static Collection<ColorfulItems> getColorfulItems() {
    return colorMap.values();
  }
  
  public static WoodItems getWoodItems(WoodType type) {
    return woodMap.get(type);
  }
  
  public static Collection<WoodItems> getWoodItems() {
    return woodMap.values();
  }
  
  public static Optional<Item> getCookedFood(Item rawFood) {
    return Optional.ofNullable(cookableFoodMap.getOrDefault(rawFood, null));
  }
  
  public static String trimItemName(String name) {
    if (name.startsWith("block.minecraft.")) {
      name = name.substring("block.minecraft.".length());
    } else if (name.startsWith("item.minecraft.")) {
      name = name.substring("item.minecraft.".length());
    } 
    return name;
  }
  
  public static boolean areShearsEffective(Block b) {
    return (b instanceof net.minecraft.block.LeavesBlock || b == Blocks.COBWEB || b == Blocks.GRASS || b == Blocks.TALL_GRASS || b == Blocks.LILY_PAD || b == Blocks.FERN || b == Blocks.DEAD_BUSH || b == Blocks.VINE || b == Blocks.TRIPWIRE || 
      
      BlockTagVer.isWool(b) || b == Blocks.NETHER_SPROUTS);
  }
  
  private static boolean isStackProtected(AltoClefController mod, ItemStack stack) {
    if (stack.hasEnchantments() && mod.getModSettings().getDontThrowAwayEnchantedItems())
      return true; 
    if (ItemVer.hasCustomName(stack) && mod.getModSettings().getDontThrowAwayCustomNameItems())
      return true; 
    return (mod.getBehaviour().isProtected(stack.getItem()) || mod.getModSettings().isImportant(stack.getItem()));
  }
  
  public static boolean canThrowAwayStack(AltoClefController mod, ItemStack stack) {
    if (stack.isEmpty())
      return false; 
    if (isStackProtected(mod, stack))
      return false; 
    return (mod.getModSettings().isThrowaway(stack.getItem()) || mod.getModSettings().shouldThrowawayUnusedItems());
  }
  
  public static boolean canStackTogether(ItemStack from, ItemStack to) {
    if (to.isEmpty() && from.getCount() <= from.getMaxCount())
      return true; 
    return (to.getItem().equals(from.getItem()) && from.getCount() + to.getCount() < to.getMaxCount());
  }
  
  private static Map<Item, Integer> getFuelTimeMap() {
    if (fuelTimeMap == null)
      fuelTimeMap = AbstractFurnaceBlockEntity.createFuelTimeMap(); 
    return fuelTimeMap;
  }
  
  public static double getFuelAmount(Item... items) {
    double total = 0.0D;
    for (Item item : items) {
      if (getFuelTimeMap().containsKey(item)) {
        int timeTicks = ((Integer)getFuelTimeMap().get(item)).intValue();
        total += timeTicks / 200.0D;
      } 
    } 
    return total;
  }
  
  public static double getFuelAmount(ItemStack stack) {
    return getFuelAmount(new Item[] { stack.getItem() }) * stack.getCount();
  }
  
  public static boolean isFuel(Item item) {
    return getFuelTimeMap().containsKey(item);
  }
  
  public boolean isRawFood(Item item) {
    return cookableFoodMap.containsKey(item);
  }

  public static class ColorfulItems {
    public DyeColor color;
    public String colorName;
    public Item dye;
    public Item wool;
    public Item bed;
    public Item carpet;
    public Item stainedGlass;
    public Item stainedGlassPane;
    public Item terracotta;
    public Item glazedTerracotta;
    public Item concrete;
    public Item concretePowder;
    public Item banner;
    public Item shulker;
    public Block wallBanner;

    public ColorfulItems(DyeColor color, String colorName, Item dye, Item wool, Item bed, Item carpet, Item stainedGlass, Item stainedGlassPane, Item terracotta, Item glazedTerracotta, Item concrete, Item concretePowder, Item banner, Item shulker, Block wallBanner) {
      this.color = color;
      this.colorName = colorName;
      this.dye = dye;
      this.wool = wool;
      this.bed = bed;
      this.carpet = carpet;
      this.stainedGlass = stainedGlass;
      this.stainedGlassPane = stainedGlassPane;
      this.terracotta = terracotta;
      this.glazedTerracotta = glazedTerracotta;
      this.concrete = concrete;
      this.concretePowder = concretePowder;
      this.banner = banner;
      this.shulker = shulker;
      this.wallBanner = wallBanner;
    }
  }

  public static class WoodItems {
    public String prefix;
    public Item planks;
    public Item log;
    public Item strippedLog;
    public Item strippedWood;
    public Item wood;
    public Item sign;
    public Item hangingSign;
    public Item door;
    public Item button;
    public Item stairs;
    public Item slab;
    public Item fence;
    public Item fenceGate;
    public Item boat;
    public Item sapling;
    public Item leaves;
    public Item pressurePlate;
    public Item trapdoor;

    public WoodItems(String prefix, Item planks, Item log, Item strippedLog, Item strippedWood, Item wood, Item sign, Item hangingSign, Item door, Item button, Item stairs, Item slab, Item fence, Item fenceGate, Item boat, Item sapling, Item leaves, Item pressurePlate, Item trapdoor) {
      this.prefix = prefix;
      this.planks = planks;
      this.log = log;
      this.strippedLog = strippedLog;
      this.strippedWood = strippedWood;
      this.wood = wood;
      this.sign = sign;
      this.hangingSign = hangingSign;
      this.door = door;
      this.button = button;
      this.stairs = stairs;
      this.slab = slab;
      this.fence = fence;
      this.fenceGate = fenceGate;
      this.boat = boat;
      this.sapling = sapling;
      this.leaves = leaves;
      this.pressurePlate = pressurePlate;
      this.trapdoor = trapdoor;
    }

    public boolean isNetherWood() {
      return planks == Items.CRIMSON_PLANKS || planks == Items.WARPED_PLANKS;
    }
  }
}
