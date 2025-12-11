package com.player2.playerengine.tasks.construction.build_structure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.sandrohc.schematic4j.SchematicLoader;
import net.sandrohc.schematic4j.exception.ParsingException;
import net.sandrohc.schematic4j.schematic.Schematic;
import net.sandrohc.schematic4j.schematic.types.SchematicBlock;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.ConversationHistory;
import com.player2.playerengine.player2api.LLMCompleter;
import com.player2.playerengine.player2api.Player2APIService;
import com.player2.playerengine.player2api.Prompts;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.time.TimerGame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

public class BuildStructureTask extends Task {
    private static final int maxNumErrors = 2;
    private static Logger LOGGER = LogManager.getLogger();

    private boolean isDone = false;

    private BlockPos buildPosition;
    private String schematicQuery;
    private String description;

    private PlayerEngineController mod;
    private Player2APIService service;
    private int numErrors;
    private Task actuallyRunningTask;
    private ConversationHistory history;
    private LLMCompleter completer;

    private class BuildSchematicFromDescriptionTask extends Task {

        private Schematic schematic = null;
        private boolean finished = false;

        private BlockPos origin;

        private TimerGame blockPlaceTimer = new TimerGame(0.1f);
        private int blockPlaceProgress;

        public boolean hasSchematic() {
            return schematic != null;
        }

        @Override
        protected void onStart() {
            finished = false;
            schematic = null;
            blockPlaceProgress = 0;
            origin = buildPosition;// mod.getPlayer().blockPosition();

            String query = schematicQuery;

            LOGGER.info("Searching Schematic: {}", query);

            List<JsonObject> schematics = service.searchSchematics(query);

            LOGGER.info("Got {} results", schematics.size());

            if (schematics.size() == 0) {
                // no schematics: Nothing.
                finished = true;
                return;
            }


            ConversationHistory selectPromptHistory = new ConversationHistory(Prompts.getSelectSchematicPrompt());
            StringBuilder result = new StringBuilder("{");
            result.append(String.join(",\n", schematics.stream().map(s -> s.toString()).toList()));
            result.append("\n}");

            selectPromptHistory.addUserMessage(
                    result.toString(),
                    service);

            LOGGER.info("Querying LLM to pick best schematic...");

            completer.processToString(service, selectPromptHistory, schematicID -> {

                LOGGER.info("LLM Picked best schematic id: {}", schematicID);

                String b64String = service.getSchematicBinary(schematicID);

                LOGGER.debug("Got schematic b64: {}", b64String);

                if (b64String == null || b64String.length() == 0) {
                    finished = true;
                    return;
                }

                ByteArrayInputStream input = new ByteArrayInputStream(Base64.getDecoder().decode(b64String));

                // Load and store the schematic
                try {
                    schematic = SchematicLoader.load(input);
                    LOGGER.info("Loaded schematic successfully! {}x{}x{}", schematic.width(), schematic.height(), schematic.length());
                } catch (ParsingException | IOException e) {
                    // Fail
                    e.printStackTrace();
                    finished = true;
                    return;
                }

            }, errStr -> {
                LOGGER.info("LLM Transport Error={}", errStr);
                finished = true;
            }, false);
        }

        private static <T extends Enum<T> & StringRepresentable & Comparable<T>> BlockState setEnumProp(EnumProperty<T> enumProp, BlockState blockState, String value) {
            // Find the desired value
            for (var possible : enumProp.getPossibleValues()) {
                T possibleEnum = (T) possible;
                if (possibleEnum.name().toLowerCase().equals(value)) {
                    return blockState.setValue(enumProp, possibleEnum);
                }
            }
            // We did not find a valid enum!
            LOGGER.warn("Unable to find enum prop that matched the value \"{}\" to any of following enums: [{}]", value, String.join(", ",enumProp.getPossibleValues().stream().map(x -> "\"" + x.toString() + "\"").toList()));
            return blockState;
        }

        @Override
        protected Task onTick() {
            if (schematic == null) {
                // Wait for schematic to load
                return null;
            }

            if (!blockPlaceTimer.elapsed()) {
                // Wait for block place delay
                return null;
            }

            boolean foundInvalidBlock = false;
            while (!foundInvalidBlock) {
                // one at a time, don't repeat since some blocks may not place correctly and we don't want to get stuck
                int xs = schematic.width();
                int zs = schematic.length();
                int ys = schematic.height();
                int xx = blockPlaceProgress % xs;
                int zz = (blockPlaceProgress / xs) % zs;
                int yy = (blockPlaceProgress) / (zs * xs);
                // We placed the last block
                if (blockPlaceProgress > xs*zs*ys) {
                    finished = true;
                    return null;
                }

                SchematicBlock desiredSchematicState = schematic.block(xx, yy, zz);
                String blockName = desiredSchematicState.block();
                while (blockName.startsWith("minecraft:")) {
                    blockName = blockName.substring("minecraft:".length());
                }
                ResourceLocation desiredSchematicId = ResourceLocation.fromNamespaceAndPath("minecraft", blockName);
                Block desiredSchematicBlock = BuiltInRegistries.BLOCK.get(desiredSchematicId);

                BlockPos worldPos = origin.offset(xx - xs / 2 , yy, zz - zs / 2);
                BlockState currentState = mod.getWorld().getBlockState(worldPos);

                BlockState desiredState = desiredSchematicBlock.defaultBlockState();
                // Apply properties
                Map<String, Property<?>> props = new HashMap<>();
                for (Property<?> p : desiredState.getProperties()) {
                    props.put(p.getName().toLowerCase(), p);
                }
                for(Entry<String, String> propEntry : desiredSchematicState.states().entrySet()) {
                    String propKey = propEntry.getKey().toLowerCase();
                    String propValue = propEntry.getValue();
                    Property<?> prop = props.get(propKey);
                    if (prop == null) {
                        LOGGER.warn("Failed to find prop with name {} for block {}. Ignoring this prop.", propKey, desiredSchematicBlock.getName());
                        continue;
                    }
                    if (prop instanceof BooleanProperty) {
                        desiredState = desiredState.setValue((BooleanProperty)prop, Boolean.parseBoolean(propValue));
                        continue;
                    }
                    if (prop instanceof IntegerProperty) {
                        desiredState = desiredState.setValue((IntegerProperty) prop, Integer.parseInt(propValue));
                        continue;
                    }
                    if (prop instanceof EnumProperty) {
                        EnumProperty<?> enumProp = (EnumProperty<?>) prop;
                        desiredState = setEnumProp(enumProp, desiredState, propValue);
                        continue;
                    }
                    LOGGER.warn("Did not account for this type of property: {}, {} when setting {}={}. Add support for this! Ignoring this state for now.", prop.getClass().getSimpleName(), prop.toString(), propKey, propValue);
                }

                if (!currentState.getBlock().getName().equals(desiredSchematicBlock.getName())) {
                    LOGGER.info("ASDF REPLACING BLOCK({}): {} -> {} ({})", worldPos, currentState.getBlock().getName(), desiredState.toString());
                    mod.getWorld().setBlock(worldPos, 
                    desiredState, 3);

                    // block place delay
                    // Hit a target of maximum 3 min build
                    // Speed up otherwise
                    float targetMaxBuildTime = 3f * 60f;
                    int blocksToPlace = xs * ys * zs;
                    float blockPlaceDelay = Math.min(blocksToPlace > 0 ? (targetMaxBuildTime / blocksToPlace) : 0.3f, 0.3f);
                    blockPlaceTimer.setInterval(blockPlaceDelay);
                    blockPlaceTimer.reset();
                    foundInvalidBlock = true;
                } else {
                    LOGGER.info("ASDF gucci {}", worldPos);
                }

                blockPlaceProgress++;
            }

            // next frame
            return null;
        }

        @Override
        protected void onStop(Task var1) {
        }

        @Override
        protected boolean isEqual(Task var1) {
            return var1 instanceof BuildSchematicFromDescriptionTask;
        }

        @Override
        protected String toDebugString() {
            return String.format("Building structure at (%s) from schematic search: (%s)", buildPosition.toShortString(), description);
        }

        @Override
        public boolean isFinished() {
            return finished;
        }
    }

    private class RequestLLMCode extends Task {
        // outer option: isDone, either: (left=code (success), right=errStr)
        Optional<Either<String, String>> llmResult = Optional.empty();

        @Override
        protected boolean isEqual(Task var1) {
            return var1 instanceof RequestLLMCode && ((RequestLLMCode) var1).llmResult == llmResult;
        }

        @Override
        protected void onStart() {
            // call LLM and either output err or code result.
            completer.processToString(service, history, codeResult -> {
                LOGGER.info("LLM generated code={}", codeResult);
                llmResult = Optional.of(Either.left(codeResult));
            }, errStr -> {
                LOGGER.info("LLM Transport Error={}", errStr);
                llmResult = Optional.of(Either.right(errStr));
            }, false);
        }

        @Override
        protected void onStop(Task var1) {

        }

        @Override
        protected Task onTick() {
            return null;
        }

        @Override
        protected String toDebugString() {
            return String.format("Thinking about how to build structure at (%s) with description (%s)", buildPosition.toShortString(), description);
        }

        @Override
        public boolean isFinished() {
            return llmResult.isPresent();
        }
    }

    private class BuildFromCode extends Task {
        String code;

        private ExecutorService buildThread;
        // outer Option: is done, inner option: is error
        Optional<Optional<String>> result = Optional.empty();

        public BuildFromCode(String code) {
            this.code = code;
            this.buildThread = Executors.newSingleThreadExecutor();
            buildThread.submit(() -> {
                StructureFromCode.buildStructureFromCode(code, setBlockData -> {
                    LOGGER.info("setBlock(x={}, y={}, z={}, blockName={})",
                            setBlockData.x, setBlockData.y, setBlockData.z, setBlockData.blockName);
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", setBlockData.blockName);
                    Block block = BuiltInRegistries.BLOCK.get(id);
                    // 3 means send to clients (2) and notify neighbors/update block states (1).
                    // maybe do 2 if you dont want
                    // redstone/etc updating/torches falling probably
                    mod.getWorld().setBlock(new BlockPos(setBlockData.x, setBlockData.y, setBlockData.z),
                            block.defaultBlockState(), 3);
                }, (errStr) -> {
                    result = Optional.of(Optional.of(errStr));
                }, () -> {
                    result = Optional.of(Optional.empty());
                }, mod);
            });
        }

        @Override
        protected boolean isEqual(Task var1) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        protected void onStart() {
            // TODO Auto-generated method stub

        }

        @Override
        protected void onStop(Task var1) {
            // TODO Auto-generated method stub
            buildThread.shutdownNow();
        }

        @Override
        protected Task onTick() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean isFinished() {
            return result.isPresent();
        }

        @Override
        protected String toDebugString() {
            return String.format("Currently building the structure at (%s) from description (%s)", buildPosition.toShortString(), description);
        }
    }

    public BuildStructureTask(BlockPos position, String schematicQuery, String description, PlayerEngineController mod) {
        this.buildPosition = position;
        this.schematicQuery = schematicQuery;
        this.description = description;
        this.mod = mod;
        this.service = mod.getPlayer2APIService();
        this.numErrors = 0;
        this.history = new ConversationHistory(Prompts.getBuildStructurePrompt());
        history.addUserMessage(
                String.format("Build with the following description: (%s). Build at position (%s)", description, buildPosition.toShortString()),
                service);
        this.completer = new LLMCompleter();
    }

    @Override
    protected void onStart() {
        actuallyRunningTask = new BuildSchematicFromDescriptionTask();
    }

    @Override
    protected Task onTick() {
        if (numErrors > maxNumErrors) {
            LOGGER.info("Too many errors, finishing.");
            // TODO: change to error from Task
            isDone = true;
            return null;
        }
        if (actuallyRunningTask == null || !actuallyRunningTask.isFinished()) {
            return actuallyRunningTask;
        }
        // ---------- now task is finished, switch to next task: -------

        if (actuallyRunningTask instanceof BuildSchematicFromDescriptionTask) {
            BuildSchematicFromDescriptionTask buildSchematicTask = (BuildSchematicFromDescriptionTask) actuallyRunningTask;
            if (buildSchematicTask.hasSchematic()) {
                // We finished building and are done
                isDone = true;
                actuallyRunningTask = null;
            } else {
                // We STOPPED building, move on to request LLM code
                actuallyRunningTask = new RequestLLMCode();
            }
            return actuallyRunningTask;
        }
        if (actuallyRunningTask instanceof RequestLLMCode) {
            LOGGER.info("Requesting llm code for pos={} description={}", buildPosition.toShortString(), description);
            Either<String, String> result = ((RequestLLMCode) actuallyRunningTask).llmResult.get();
            // set actually running task to next task:
            result.mapBoth(
                    code -> {
                        LOGGER.info("LLM returned code={}", code);
                        actuallyRunningTask = new BuildFromCode(code);
                        return null;
                    }, errStr -> {
                        ++numErrors;
                        String tryAgainMessage = String.format(
                                "When trying to call the llm with the description, got this error: \n(%s)\n. Try again and generate code using the same description:\n(%s)",
                                errStr, description);
                        history.addUserMessage(tryAgainMessage, service);
                        LOGGER.info(tryAgainMessage);
                        actuallyRunningTask = new RequestLLMCode();
                        return null;
                    });
            return actuallyRunningTask;
        }
        if (actuallyRunningTask instanceof BuildFromCode) {
            Optional<String> result = ((BuildFromCode) actuallyRunningTask).result.get();
            // set actually running task in both cases
            result.ifPresentOrElse(
                    errStr -> {
                        String code = ((BuildFromCode) actuallyRunningTask).code;
                        history.addAssistantMessage(code, service);
                        String tryAgainMessage = String.format(
                                "The code was executed, but got error \n(%s)\nTry again and generate code with the same description:\n(%s). | REMEMBER: do NOT use any math functions such as Math.sin, Math.abs, etc. Do NOT make functions, as these will not be parsed.",
                                errStr, description);
                        LOGGER.info(tryAgainMessage);
                        history.addUserMessage(tryAgainMessage, service);
                        actuallyRunningTask = new RequestLLMCode();
                    }, () -> {
                        isDone = true;
                        actuallyRunningTask = null;
                    });
            return actuallyRunningTask;
        }
        LOGGER.error("actually running task in buildStructureTask set to incorrect type");
        return null;
    }

    @Override
    public boolean isFinished() {
        return isDone;
    }

    @Override
    protected boolean isEqual(Task other) {
        if (!(other instanceof BuildStructureTask))
            return false;
        BuildStructureTask o = (BuildStructureTask) other;
        return o.description == this.description;
    }

    @Override
    protected void onStop(Task next) {
    }

    @Override
    protected String toDebugString() {
        return "BuildingStructure(pos=" + buildPosition.toShortString() + ", description=" + description + ")";
    }
}