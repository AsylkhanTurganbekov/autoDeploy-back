package kz.zeroops.agent;
import java.util.function.Consumer;
interface DeploymentExecutor { ExecutionResult execute(AgentBoundary.Manifest manifest, Consumer<String> log); record ExecutionResult(boolean success,String message,String failureReason){} }
