package uk.ac.ebi.subs.stresstest;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Configuration
/**
 * Rest template must be configured to use connection pooling, else it will leave too many open connections
 * and the server dies
 */
public class StressTesterConfig {

    private static final int TOTAL = 200;
    private static final int PER_ROUTE = 200;

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        HttpClient httpClient = HttpClientBuilder.create()
                .setMaxConnTotal(TOTAL)
                .setMaxConnPerRoute(PER_ROUTE)
                .build();
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));

        return restTemplate;
    }

}
