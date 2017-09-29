package uk.ac.ebi.subs.stresstest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.client.*;
import uk.ac.ebi.subs.data.component.Attribute;
import uk.ac.ebi.subs.data.status.SubmissionStatus;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class StressTestServiceImpl implements StressTestService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ApiLinkDiscovery apiLinkDiscovery = new ApiLinkDiscovery();

    @Value("${host:localhost}")
    String host;
    @Value("${port:8080}")
    Integer port;
    @Value("${basePath:api}")
    String basePath;
    @Value("${protocol:http}")
    String protocol;
    @Value("${suffix:json}")
    String suffix;
    @Value("${aap.url}")
    String aapURL;
    @Value("${aap.username}")
    String aapUsername;
    @Value("${aap.password}")
    String appPassword;
    @Value("${submitted:true}")
    boolean submitted;

    @Autowired
    RestTemplate restTemplate;

    ObjectMapper mapper = new ObjectMapper();

    ParameterizedTypeReference<Resource<Submission>> submissionResourceTypeRef =
            new ParameterizedTypeReference<Resource<Submission>>() {
            };

    int submissionCounter = 0;

    @Override
    public void submitJsonInDir(Path path) {
        //httpHeaders = createHeaders();
        pathStream(path)
                .parallel()
                .map(loadSubmission)
                .map(fixSampleRelease)
                .forEach(submitSubmission)
        ;
        logger.info("Submission count: {}", submissionCounter);
    }

    public Map<Class, String> itemSubmissionUri(String submissionUri) {
        Map<Class, String> itemClassToSubmissionUri = new HashMap<>();
        URI rootUri = URI.create(submissionUri + "/contents/");

        Stream.of(
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
                    Class type = pair.getFirst();
                    String name = pair.getSecond() + ":create";

                    Link link = apiLinkDiscovery.discoverNamedLink(restTemplate,rootUri, name);

                    itemClassToSubmissionUri.put(type, link.getHref());
                }
        );

        return itemClassToSubmissionUri;
    }

    public String itemSubmissionCreateUri(String teamName) {
        return discoverNamedLink(teamName,"submissions:create");
    }

    public String discoverNamedLink(String teamName, String linkName) {
        return apiLinkDiscovery.discoverNamedLink(restTemplate, URI.create(protocol + "://" + host + ":" + port + "/" + basePath + "/teams/" + teamName + "/"), linkName).getHref();
    }

    Stream<Path> pathStream(Path searchDir) {
        try {
            return Files.walk(searchDir)
                    .filter(Files::isReadable)
                    .filter(Files::isRegularFile)
                    .filter(p -> FilenameUtils.getExtension(p.toString()).equals(this.suffix))
                    .collect(Collectors.toList())
                    .stream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Consumer<ClientCompleteSubmission> submitSubmission = new Consumer<ClientCompleteSubmission>() {
        @Override
        public void accept(ClientCompleteSubmission submission) {

            logger.info("Submitting for team {} with {} submittables ",
                    submission.getTeam().getName(),
                    submission.allSubmissionItems().size()
            );

            long submissionCreateStartDateTime = System.currentTimeMillis();
            final String submissionCreateUri = itemSubmissionCreateUri(submission.getTeam().getName());
            Submission minimalSubmission = new Submission(submission);
            URI submissionLocation = restTemplate.postForLocation(submissionCreateUri, minimalSubmission);
            long submissionCreateEndDateTime = System.currentTimeMillis();
            final long between = submissionCreateEndDateTime - submissionCreateStartDateTime;
            logger.info("Submitted minimalSubmission " + minimalSubmission.getId() + " in " + between + "ms");

            final Map<Class, String> typeToSubmissionPath = itemSubmissionUri(submissionLocation.toString());

            submission.allSubmissionItemsStream().parallel().forEach(
                    item -> {
                        ((PartOfSubmission) item).setSubmission(submissionLocation.toASCIIString());

                        String itemUri = typeToSubmissionPath.get(item.getClass());

                        if (itemUri == null) {
                            throw new NullPointerException("no submission URI for " + item);
                        }
                        logger.debug("posting to {}, {}", itemUri, item);
                        try {
                            long resourceCreateStartDateTime = System.currentTimeMillis();
                            ResponseEntity<Resource> responseEntity = restTemplate.postForEntity(itemUri, item, Resource.class);
                            long resourceCreateEndDateTime = System.currentTimeMillis();
                            final long resourceBetween = resourceCreateEndDateTime - resourceCreateStartDateTime;
                            logger.info("Submitted minimalSubmission " + item.getAlias() + " in " + resourceBetween + "ms");
                            if (responseEntity.getStatusCodeValue() != 201) {
                                logger.error("Unexpected status code {} when posting {} to {}; response body is",
                                        responseEntity.getStatusCodeValue(),
                                        item,
                                        itemUri,
                                        responseEntity.getBody().toString()
                                );
                                throw new RuntimeException("Server error " + responseEntity.toString());
                            }
                            URI location = responseEntity.getHeaders().getLocation();

                            logger.debug("created {}", location);
                        } catch (HttpClientErrorException e) {
                            logger.error("HTTP error when posting item");
                            logger.error(item.toString());
                            logger.error(e.getResponseBodyAsString());
                            logger.error(e.toString());
                            throw e;
                        }

                    }
            );

            if (submitted) updateSubmissionStatus(submissionLocation);

            submissionCounter++;
        }
    };

    private void updateSubmissionStatus(URI submissionLocation) {
        Link submissionStatusLink = apiLinkDiscovery.discoverNamedLink(restTemplate,submissionLocation,  "submissionStatus", "self");
        String submissionStatusLocation = submissionStatusLink.getHref();

        logger.info("Submission {} status {}", submissionLocation, submissionStatusLocation);

        Link availableStatusesLink = null;
        long startTime = System.nanoTime();
        int attemptCounter = 0;

        while (availableStatusesLink == null && timeElapsedInSeconds(startTime) < 30 && attemptCounter < 60) {
            ++attemptCounter;
            logger.info("Searching for availableStatuses for {}, attempt {}, {}", submissionLocation, attemptCounter, timeElapsedInSeconds(startTime));
            try {
                availableStatusesLink = apiLinkDiscovery.discoverNamedLink(restTemplate,submissionLocation, "submissionStatus", "self", "availableStatuses");
            } catch (IllegalStateException e) {
                logger.info("available status link is not present, may retry");
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (availableStatusesLink == null) {
            logger.info("availableStatuses not found for {} after {} attempts", submissionLocation, attemptCounter);
            return;
        }

        HttpEntity<StatusUpdate> patchStatusEntity = new HttpEntity<>(new StatusUpdate("Submitted"));

        try {
            restTemplate.exchange(
                    submissionStatusLocation,
                    HttpMethod.PATCH,
                    patchStatusEntity,
                    new ParameterizedTypeReference<Resource<SubmissionStatus>>() {
                    }
            );
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error when patching submission status");
            logger.error("Submission {} status {}", submissionLocation, submissionStatusLocation);
            logger.error(e.getResponseBodyAsString());
            logger.error(e.toString());
            //throw e;
        }
    }

    private double timeElapsedInSeconds(long referencePointInNanoSeconds) {
        long diffInNanos = System.nanoTime() - referencePointInNanoSeconds;
        double diffInSeconds = diffInNanos / 1000000000;
        logger.info("logging {} {}", diffInNanos, diffInSeconds);
        return diffInSeconds;
    }

    private class StatusUpdate {
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public StatusUpdate(String status) {
            this.status = status;
        }
    }

    Function<Path, ClientCompleteSubmission> loadSubmission = new Function<Path, ClientCompleteSubmission>() {
        public ClientCompleteSubmission apply(Path p) {
            logger.info("Loading Submission JSON from {}", p);

            try {
                byte[] encoded = Files.readAllBytes(p);
                String json = new String(encoded, StandardCharsets.UTF_8);

                logger.debug("got string: {}", json);

                return mapper.readValue(json, ClientCompleteSubmission.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    Function<ClientCompleteSubmission, ClientCompleteSubmission> fixSampleRelease = new Function<ClientCompleteSubmission, ClientCompleteSubmission>() {
        public ClientCompleteSubmission apply(ClientCompleteSubmission submission) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
            Date now = new Date();
            Attribute releaseDate = new Attribute();
            releaseDate.setName("release");
            releaseDate.setValue(simpleDateFormat.format(now));

            submission.getSamples().stream().forEach(sample -> sample.getAttributes().add(releaseDate));
            return submission;
        }
    };




}
