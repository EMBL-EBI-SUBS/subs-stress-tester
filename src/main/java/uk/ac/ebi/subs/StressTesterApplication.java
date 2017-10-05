package uk.ac.ebi.subs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import uk.ac.ebi.subs.stresstest.StressTestService;

import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude={MongoRepositoriesAutoConfiguration.class,MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class) })
public class StressTesterApplication implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(StressTesterApplication.class);

    @Autowired
    StressTestService stressTestService;

    @Value("${searchDir:.}")
    String searchDir;

    @Override
    public void run(String... args) {

        Path searchDirPath = Paths.get(searchDir);

        logger.info("Searching for json under: "+searchDirPath.toAbsolutePath());

        this.stressTestService.submitJsonInDir(searchDirPath);
    }

    public static void main(String[] args) {
        SpringApplication.run(StressTesterApplication.class, args);
    }
}
