package br.com.ada.portal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class PortalExceptionHandler {
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<PortalError> handleUpstreamResponse(RestClientResponseException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(new PortalError("A chamada ao microserviço falhou com HTTP " + status.value()));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<PortalError> handleUpstreamUnavailable(ResourceAccessException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new PortalError("Um microserviço está indisponível no momento."));
    }

    public record PortalError(String message) {
    }
}
