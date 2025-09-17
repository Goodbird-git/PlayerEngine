package adris.altoclef.tasks.construction.build_structure;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.datafixers.util.Either;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.ConversationHistory;
import adris.altoclef.player2api.LLMCompleter;
import adris.altoclef.player2api.Player2APIService;
import adris.altoclef.player2api.Prompts;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import adris.altoclef.util.time.TimerGame;

public class BuildStructureTask extends Task {
    private static final int maxNumErrors = 2;
    private static Logger LOGGER = LogManager.getLogger();

    private boolean isDone = false;
    private String description;
    private AltoClefController mod;
    private Player2APIService service;
    private int numErrors;
    private Task actuallyRunningTask;
    private ConversationHistory history;
    private LLMCompleter completer;
    private long tickCounter = 0;
    private TimerGame statusMsgTimerGame = new TimerGame(40.0);
    private boolean started = false;

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
            if (statusMsgTimerGame.elapsed() && started) {
                statusMsgTimerGame.reset();
                mod.updateCommandstatus(
                        "Still thinking/planning about how to build, you aren't actually building yet but are still planning. This may still take a while or may be close to being done. Make sure to not repeat the same message over and over.");
            }
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

        private ExecutorService buildThread;
        // outer Option: is done, inner option: is error
        Optional<Optional<String>> result = Optional.empty();

        public BuildFromCode(String code) {
            statusMsgTimerGame.reset();
            this.code = code;
            this.buildThread = Executors.newSingleThreadExecutor();
            buildThread.submit(() -> {
                StructureFromCode.buildStructureFromCode(code, setBlockData -> {
                    LOGGER.info("setBlock(x={}, y={}, z={}, blockName={})",
                            setBlockData.x, setBlockData.y, setBlockData.z, setBlockData.blockName);
                    ResourceLocation id = new ResourceLocation("minecraft", setBlockData.blockName);
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
            statusMsgTimerGame.reset();
            // TODO Auto-generated method stub

        }

        @Override
        protected void onStop(Task var1) {
            // TODO Auto-generated method stub

        }

        @Override
        protected Task onTick() {
            if (statusMsgTimerGame.elapsed() && started) {
                statusMsgTimerGame.reset();
                mod.updateCommandstatus("Still actually building");
            }
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean isFinished() {
            return result.isPresent();
        }

        @Override
        protected String toDebugString() {
            return String.format("Currently building the structure from description (%s)", description);
        }
    }

    public BuildStructureTask(String description, AltoClefController mod) {
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
        mod.updateCommandstatus(
                "Starting to think about how to build. Let the user know that you are starting to plan/think about how to build, and that you are thinking/planning, not actually building yet, and that this may take a while.");
        statusMsgTimerGame.reset();
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
                                "The code was executed, but got error \n(%s)\nTry again and generate code with the same description:\n(%s)",
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