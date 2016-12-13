package uk.ac.ebi.subs.stresstest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.ac.ebi.subs.data.FullSubmission;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.submittable.*;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

@Service
public class StressTestServiceImpl implements StressTestService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${host:localhost}")
    String host;

    @Value("${port:8080}")
    Integer port;

    @Value("${basePath:api/}")
    String basePath;

    @Value("${protocol:http}")
    String protocol;

    @Value("${suffix:json}")
    String suffix;

    @Autowired
    RestTemplate restTemplate;

    ObjectMapper mapper = new ObjectMapper();

    int submissionCounter = 0;

    @Override
    public void submitJsonInDir(Path path) {
        pathStream(path)
                .map(loadSubmission)
                .forEachOrdered(submitSubmission)
        ;
        logger.info("Submission count: {}", submissionCounter);
    }

    //TODO you should be getting this from the REST API itself
    public Map<Class, String> itemSubmissionUri() {
        Map<Class, String> itemClassToSubmissionUri = new HashMap<>();

        Stream.of(
                Pair.of(Submission.class, "submissions"),
                Pair.of(Analysis.class, "analyses"),
                Pair.of(Assay.class, "assays"),
                Pair.of(AssayData.class, "assayData"),
                Pair.of(EgaDac.class, "egaDacs"),
                Pair.of(EgaDacPolicy.class, "egaDacPolicies"),
                Pair.of(EgaDataset.class, "egaDatasets"),
                Pair.of(Project.class, "projects"),
                Pair.of(Protocol.class, "protocols"),
                Pair.of(Sample.class, "samples"),
                Pair.of(SampleGroup.class, "sampleGroups"),
                Pair.of(Study.class, "studies")
        ).forEach(
                pair -> {
                    String urlPath = pair.getSecond();
                    Class domainType = pair.getFirst();

                    itemClassToSubmissionUri.put(domainType, protocol + "://" + host + ":" + port + "/" + basePath + urlPath);
                }
        );

        return itemClassToSubmissionUri;
    }


    Stream<Path> pathStream(Path searchDir) {
        try {
            return Files.walk(searchDir)
                    .filter(Files::isReadable)
                    .filter(Files::isRegularFile)
                    .filter(p -> FilenameUtils.getExtension(p.toString()).equals(this.suffix))
                    .filter(p -> {
                        String[] parts = p.toFile().getName().toString().split("\\.");
                        return parts.length == 3 && parts[1].matches("^\\d+$");
                    })
                    .map(pathToPathTimeCode)
                    .sorted((p1, p2) -> Long.compare(p1.timecode, p2.timecode))
                    .map(pathTimecodeToPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    Consumer<FullSubmission> submitSubmission = new Consumer<FullSubmission>() {
        @Override
        public void accept(FullSubmission fullSubmission) {

            logger.info("Submitting for domain {} with {} submittables ",
                    fullSubmission.getDomain().getName(),
                    fullSubmission.allSubmissionItems().size()
            );

            fullSubmission.setStatus("Draft");

            Map<Class, String> domainTypeToSubmissionPath = itemSubmissionUri();
            Submission minimalSubmission = new Submission(fullSubmission);

            String minimalSubmissionJson = null;
            try {
                minimalSubmissionJson = mapper.writeValueAsString(minimalSubmission);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }


            minimalSubmission.setSubmissionDate(null);
            String submissionUri = domainTypeToSubmissionPath.get(minimalSubmission.getClass());

            URI submissionLocation = restTemplate.postForLocation(submissionUri, minimalSubmission);

            final String submissionId = minimalSubmission.getId();

            fullSubmission.allSubmissionItemsStream().forEach(
                    item -> {
                        item.setSubmissionId(submissionId);
                        item.setStatus("Draft");

                        String itemUri = domainTypeToSubmissionPath.get(item.getClass());

                        String itemJson;
                        try {
                            itemJson = mapper.writeValueAsString(item);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }

                        if (itemUri == null) {
                            throw new NullPointerException("no submission URI for " + item);
                        }
                        logger.debug("posting to {}, {}", itemUri, item);
                        URI location = restTemplate.postForLocation(itemUri, item);

                        logger.debug("created {}", location);
                    }
            );


            submissionCounter++;


        }
    };

    Function<Path, FullSubmission> loadSubmission = new Function<Path, FullSubmission>() {
        public FullSubmission apply(Path p) {
            logger.info("Loading Submission JSON from {}", p);

            try {
                byte[] encoded = Files.readAllBytes(p);
                String json = new String(encoded, StandardCharsets.UTF_8);

                logger.debug("got string: {}", json);

                return mapper.readValue(json, FullSubmission.class);
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
