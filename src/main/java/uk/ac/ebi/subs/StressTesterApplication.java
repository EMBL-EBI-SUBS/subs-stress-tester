package uk.ac.ebi.subs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import uk.ac.ebi.subs.stresstest.StressTestService;

import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class StressTesterApplication implements CommandLineRunner {

    @Autowired
    StressTestService stressTestService;

    @Value("${searchDir:/Users/davidr/IdeaProjects/subs/subs-stress-tester/src/test/resources/}")
    String searchDir;

    @Override
    public void run(String... args) {
        Path searchDirPath = Paths.get(searchDir);

        this.stressTestService.submitJsonInDir(searchDirPath);
    }

    public static void main(String[] args) {
        SpringApplication.run(StressTesterApplication.class, args);
    }
}
