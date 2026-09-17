package com.edgar.portfolio.exception;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception) {
		Map<String, String> fields = new TreeMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
				fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(new ApiError(400, "Invalid request", fields));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> unreadableRequest() {
		return error(HttpStatus.BAD_REQUEST,
				"Invalid JSON or field value.");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> invalidPath() {
		return error(HttpStatus.BAD_REQUEST, "Invalid path parameter; portfolio ID must be an integer.");
	}

	@ExceptionHandler(InsufficientSharesException.class)
	public ResponseEntity<ApiError> insufficientShares(InsufficientSharesException exception) {
		return error(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> statusException(ResponseStatusException exception) {
		return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(
				exception.getStatusCode().value(), exception.getReason(), Map.of()));
	}

    @ExceptionHandler(InvalidAllocationException.class)
    public ResponseEntity<ApiError> invalidAllocation(InvalidAllocationException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({PortfolioStateConflictException.class, RegistrationConflictException.class})
    public ResponseEntity<ApiError> stateConflict(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(PortfolioNotFoundException.class)
    public ResponseEntity<ApiError> missingPortfolio(PortfolioNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> accessDenied() {
        return error(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        // Preserve framework statuses (405, 415, etc.) without exposing internal details.
        if (exception instanceof org.springframework.web.ErrorResponse response
                && response.getStatusCode().is4xxClientError()) {
            int status = response.getStatusCode().value();
            return ResponseEntity.status(status).body(new ApiError(status,
                    HttpStatus.valueOf(status).getReasonPhrase(), Map.of()));
        }
        log.error("Unexpected API failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

	private ResponseEntity<ApiError> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(new ApiError(status.value(), message, Map.of()));
	}
}
