package uk.ac.ebi.subs.stresstest;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.hal.Jackson2HalModule;
import org.springframework.hateoas.mvc.TypeConstrainedMappingJackson2HttpMessageConverter;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
/**
 * Rest template must be configured to use connection pooling, else it will leave too many open connections
 * and the server dies
 */
public class StressTesterConfig {

    @Value("${aap.username}")
    String aapUsername;

    @Value("${aap.password}")
    String aapPassword;

    @Value("${aap.url}")
    String aapURL;

    private static final int TOTAL = 1000;
    private static final int PER_ROUTE = 1000;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        final RestTemplate build = restTemplateBuilder.basicAuthorization(aapUsername, aapPassword).build();
        final String jwtToken = build.getForObject(aapURL, String.class);
        List<Header> headerList = new ArrayList<>();
        headerList.add(new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        headerList.add(new BasicHeader(HttpHeaders.AUTHORIZATION,"Bearer " + jwtToken));
        RestTemplate restTemplate = new RestTemplate();

        HttpClient httpClient = HttpClientBuilder.create()
                .setDefaultHeaders(headerList)
                .setMaxConnTotal(TOTAL)
                .setMaxConnPerRoute(PER_ROUTE)
                .build();
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));
        List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
        converters.add(0,getHalMessageConverter());
        return restTemplate;
    }

    @Bean(name = "aapRestOperations")
    RestOperations rest(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder.basicAuthorization(aapUsername, aapPassword).build();
    }

    private HttpMessageConverter getHalMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new Jackson2HalModule());
        MappingJackson2HttpMessageConverter halConverter = new TypeConstrainedMappingJackson2HttpMessageConverter(Resource.class);
        halConverter.setSupportedMediaTypes(Arrays.asList(MediaTypes.HAL_JSON));
        halConverter.setObjectMapper(objectMapper);
        return halConverter;
    }

}
