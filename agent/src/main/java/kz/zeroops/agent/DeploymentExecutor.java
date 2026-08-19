package kz.zeroops.agent;
interface DeploymentExecutor { ExecutionResult execute(AgentBoundary.Manifest manifest); record ExecutionResult(boolean success,String message,String failureReason){} }
