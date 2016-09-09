package uk.ac.ebi.subs.stresstest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uk.ac.ebi.subs.data.submittable.Submission;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

@Component
public class StressTestServiceImpl implements StressTestService {

    @Value("${host:localhost}")
    String host;

    @Value("${port:8080}")
    Integer port;

    @Value("${urlPath:submit}")
    String urlPath;

    @Value("${protocol:http}")
    String protocol;

    @Value("${suffix:json}")
    String suffix;

    RestTemplate restTemplate = new RestTemplate();

    ObjectMapper mapper = new ObjectMapper();

    @Override
    public void submitJsonInDir(Path path) {
        pathStream(path)
                .map(loadSubmission)
                .forEachOrdered(submitSubmission)
        ;


    }


    Stream<Path> pathStream(Path searchDir) {
        try {
            return Files.walk(searchDir)
                    .filter(Files::isReadable)
                    .filter(Files::isRegularFile)
                    .filter(p -> FilenameUtils.getExtension(p.toString()).equals(this.suffix))
                    .map(pathToPathTimeCode)
                    .sorted((p1, p2) -> Long.compare(p1.timecode, p2.timecode))
                    .map(pathTimecodeToPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    Consumer<Submission> submitSubmission = new Consumer<Submission>() {
        @Override
        public void accept(Submission submission) {
            restTemplate.put(protocol + "://" + host + ":" + port + "/" + urlPath, submission);
        }
    };

    Function<Path, Submission> loadSubmission = new Function<Path, Submission>() {
        public Submission apply(Path p) {

            try {
                byte[] encoded = Files.readAllBytes(p);
                String json = new String(encoded, StandardCharsets.UTF_8);

                return mapper.readValue(json, Submission.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };


    void submit(String submission) {

    }


    private class PathTimecode {
        Path path;
        Long timecode;

        PathTimecode(Path p, Long timecode) {
            this.path = p;
            this.timecode = timecode;
        }
    }

    Function<Path, PathTimecode> pathToPathTimeCode
            = new Function<Path, PathTimecode>() {

        public PathTimecode apply(Path p) {
            String[] parts = p.getFileName().toString().split("\\.");

            if (parts.length < 3) {
                throw new IllegalArgumentException(
                        "File name should have three parts <name>.<number>.json instead of " + p.getFileName()
                );
            }

            int penultimatePartIndex = parts.length - 2;

            Long timeCode = Long.decode(parts[penultimatePartIndex]);

            return new PathTimecode(p, timeCode);
        }
    };

    Function<PathTimecode, Path> pathTimecodeToPath = new Function<PathTimecode, Path>() {
        @Override
        public Path apply(PathTimecode pathTimecode) {
            return pathTimecode.path;
        }
    };

}
