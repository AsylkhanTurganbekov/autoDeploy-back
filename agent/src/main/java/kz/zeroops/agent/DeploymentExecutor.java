package kz.zeroops.agent;
import java.util.List;
import java.util.function.Consumer;
interface DeploymentExecutor { ExecutionResult execute(AgentBoundary.Manifest manifest, Consumer<String> log); record ServicePlan(String key,String path,String runtime,int internalPort,boolean publicCandidate,boolean hasDockerfile,List<String> evidence){} record ExecutionResult(boolean success,String message,String failureReason,Integer applicationPort,Integer publicPort,String runtime,String healthPath,List<ServicePlan> services){} }
