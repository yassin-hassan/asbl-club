package club.asbl.asbl_club.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error raised in a JSON controller becomes a Problem Details response (RFC 9457,
 * application/problem+json): status, title, detail and the request path, in one shape the frontend can rely on.
 * Spring's base class already does this for its own exceptions and for ResponseStatusException.
 *
 * <p>Scoped to @RestController: the Thymeleaf pages keep their HTML error page.
 */
@RestControllerAdvice(annotations = RestController.class)
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

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
}
