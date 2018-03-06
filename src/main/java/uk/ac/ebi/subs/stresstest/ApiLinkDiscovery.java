package uk.ac.ebi.subs.stresstest;

import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.client.Traverson;
import org.springframework.web.client.RestOperations;

import java.net.URI;

public class ApiLinkDiscovery {


    public Link discoverNamedLink(RestOperations restOperations, URI uri, String... linkNames){
        Traverson traverson = new Traverson(uri, MediaTypes.HAL_JSON);
        traverson.setRestOperations(restOperations);
        Traverson.TraversalBuilder builder = traverson.follow(linkNames);
        return builder.asLink();
    }
}
