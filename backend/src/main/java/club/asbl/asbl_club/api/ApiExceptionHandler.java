package club.asbl.asbl_club.api;

import club.asbl.asbl_club.config.RequestIdFilter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error raised in a JSON controller becomes a Problem Details response (RFC 9457,
 * application/problem+json): status, title, detail and the request path, in one shape the frontend can rely on.
 * Spring's base class already does this for its own exceptions and for ResponseStatusException.
 */
@RestControllerAdvice(annotations = RestController.class)
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // Validation failures also list which field is wrong, so a form can show the message next to it:
    // "errors": { "password": "must not be blank" }
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = ex.getBody();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // Anything else is a bug: logged here, with its stack trace, while the request ID is still in the logging
    // context (past the filters, Tomcat would log it without). The answer says nothing about the cause, only the
    // request ID to quote when reporting it. Spring's own exceptions keep their handlers above (more specific).
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error. Quote the request ID when reporting it.");
        problem.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return problem;
    }
}
