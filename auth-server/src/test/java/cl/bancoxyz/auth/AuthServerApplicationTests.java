package cl.bancoxyz.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthServerApplicationTests {

    @Autowired
    TestRestTemplate rest;

    @Test
    @SuppressWarnings("rawtypes")
    void emiteTokenParaCajeroConClientCredentials() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("cajero-atm", "atm-secret");
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", "canal.atm");

        ResponseEntity<Map> resp = rest.postForEntity("/oauth2/token", new HttpEntity<>(form, headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("access_token");
        assertThat(resp.getBody().get("scope")).isEqualTo("canal.atm");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void rechazaScopeNoAutorizado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("cajero-atm", "atm-secret");
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", "banco.api");

        ResponseEntity<Map> resp = rest.postForEntity("/oauth2/token", new HttpEntity<>(form, headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
