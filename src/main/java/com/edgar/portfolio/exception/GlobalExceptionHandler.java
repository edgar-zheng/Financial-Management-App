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

	public record ApiError(int status, String message, Map<String, String> fieldErrors) {}

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
				"Invalid JSON or field value. Transaction type must be BUY or SELL.");
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

	private ResponseEntity<ApiError> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(new ApiError(status.value(), message, Map.of()));
	}
}
