package com.player2.playerengine.tasks.construction.build_structure;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.datafixers.util.Either;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.ConversationHistory;
import com.player2.playerengine.player2api.LLMCompleter;
import com.player2.playerengine.player2api.Player2APIService;
import com.player2.playerengine.player2api.Prompts;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.construction.build_structure.StructureFromCode.SetBlockCommand;
import com.player2.playerengine.util.time.TimerGame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public class BuildStructureTask extends Task {
    private static final int maxNumErrors = 2;
    private static Logger LOGGER = LogManager.getLogger();

    private boolean isDone = false;
    private String description;
    private PlayerEngineController mod;
    private Player2APIService service;
    private int numErrors;
    private Task actuallyRunningTask;
    private ConversationHistory history;
    private LLMCompleter completer;

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
            return String.format("Thinking about how to build structure with description (%s)", description);
        }

        @Override
        public boolean isFinished() {
            return llmResult.isPresent();
        }
    }

    private class BuildFromCode extends Task {
        String code;

        private TimerGame blockPlaceTimer = new TimerGame(0.1f);
        Deque<SetBlockCommand> setBlockQueue = new ArrayDeque<>();

        private ExecutorService buildThread;
        // outer Option: is done, inner option: is error
        Optional<Optional<String>> result = Optional.empty();

        public BuildFromCode(String code) {
            this.code = code;
            this.buildThread = Executors.newSingleThreadExecutor();
            synchronized (setBlockQueue) {
                setBlockQueue.clear();
            }

            buildThread.submit(() -> {
                StructureFromCode.buildStructureFromCode(code, setBlockData -> {
                    // Queue up
                    synchronized (setBlockQueue) {
                        setBlockQueue.add(setBlockData);
                    }                    
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
            synchronized (setBlockQueue) {
                setBlockQueue.clear();
            }
        }

        @Override
        protected Task onTick() {
            synchronized (setBlockQueue) {
                if (setBlockQueue.size() == 0) {
                    return null;
                }
                if (!blockPlaceTimer.elapsed()) {
                    return null;
                }
                SetBlockCommand setBlockData = setBlockQueue.poll();
                LOGGER.info("setBlock(x={}, y={}, z={}, blockName={})",
                        setBlockData.x, setBlockData.y, setBlockData.z, setBlockData.blockName);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", setBlockData.blockName);
                Block block = BuiltInRegistries.BLOCK.get(id);
                // 3 means send to clients (2) and notify neighbors/update block states (1).
                // maybe do 2 if you dont want
                // redstone/etc updating/torches falling probably
                mod.getWorld().setBlock(new BlockPos(setBlockData.x, setBlockData.y, setBlockData.z),
                        block.defaultBlockState(), 3);
                blockPlaceTimer.reset();
            }

            return null;
        }

        @Override
        public boolean isFinished() {
            return result.isPresent() && setBlockQueue.size() == 0;
        }

        @Override
        protected String toDebugString() {
            return String.format("Currently building the structure from description (%s)", description);
        }
    }

    public BuildStructureTask(String description, PlayerEngineController mod) {
        this.description = description;
        this.mod = mod;
        this.service = mod.getPlayer2APIService();
        this.numErrors = 0;
        this.history = new ConversationHistory(Prompts.getBuildStructurePrompt());
        history.addUserMessage(
                String.format("Build with the following description: (%s)", description),
                service);
        this.completer = new LLMCompleter();
    }

    @Override
    protected void onStart() {
        actuallyRunningTask = new RequestLLMCode();
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

        if (actuallyRunningTask instanceof RequestLLMCode) {
            LOGGER.info("Requesting llm code for description={}", description);
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
        return "BuildingStructure(" + description + ")";
    }
}