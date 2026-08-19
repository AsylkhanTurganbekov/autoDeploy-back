package kz.zeroops.agent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name="agent.execution-mode",havingValue="dry-run",matchIfMissing=true) class DryRunDeploymentExecutor implements DeploymentExecutor { public ExecutionResult execute(AgentBoundary.Manifest m){return new ExecutionResult(true,"Manifest verified; dry-run executor completed without Docker access.",null);} }
