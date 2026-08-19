package kz.zeroops.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AutoDeployWorkerApplication {
  public static void main(String[] args) { SpringApplication.run(AutoDeployWorkerApplication.class, args); }
}
