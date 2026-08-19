package kz.zeroops.agent;
import org.springframework.boot.*; import org.springframework.boot.autoconfigure.*; import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication @EnableScheduling public class AutoDeployAgentApplication { public static void main(String[] args){SpringApplication.run(AutoDeployAgentApplication.class,args);} }
