package com.banyazavi.forwarder;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
public class ForwarderController {

  @Value("${access.logging.enabled}")
  private boolean loggingEnabled;

  @Value("${forwarder.target-host:}")
  private String forwardHost;

  private final WebClient webClient = WebClient.create();

  @PostConstruct
  public void validateConfiguration() {
    if (forwardHost == null || forwardHost.isBlank()) {
      throw new IllegalStateException("Missing required configuration: forwarder.target-host");
    }
  }

  @RequestMapping(value = "/**")
  public Mono<ResponseEntity<Void>> forwardRequest(ServerHttpRequest request,
      @RequestBody(required = false) String body) {

    HttpMethod method = request.getMethod();
    String uri = request.getURI().getRawPath();
    String query = request.getURI().getRawQuery();
    String fullUri = uri + (query != null ? "?" + query : "");
    String fullTarget = forwardHost + fullUri;

    int contentLength = Optional.ofNullable(body)
        .map(b -> b.getBytes(StandardCharsets.UTF_8).length).orElse(0);

    String clientIp = extractClientIp(request);

    if (loggingEnabled) {
      log.info("{} {}, Content {} bytes, from {}", method, fullUri, contentLength, clientIp);
    }

    WebClient.RequestBodySpec spec = webClient.method(method).uri(fullTarget).headers(headers -> {
      request.getHeaders().forEach((key, values) -> {
        for (String value : values) {
          headers.add(key, value);
        }
      });
      headers.set("X-Original-IP", clientIp);
    });

    return spec.bodyValue(body != null ? body : "").retrieve().toBodilessEntity()
        .onErrorResume(ex -> {
          log.error("Failed to forward request to {}: {}", fullTarget, ex.getMessage());
          return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).build());
        });
  }

  private String extractClientIp(ServerHttpRequest request) {
    String ip;
    List<String> header = request.getHeaders().get("X-Forwarded-For");

    if (header != null && !header.isEmpty()) {
      ip = header.get(0).split(",")[0].trim();
    } else if (request.getRemoteAddress() != null) {
      ip = request.getRemoteAddress().getAddress().getHostAddress();
    } else {
      ip = "unknown";
    }

    return ip;
  }
}
