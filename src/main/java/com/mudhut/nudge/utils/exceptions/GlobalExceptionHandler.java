package com.mudhut.nudge.utils.exceptions;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.mailersend.sdk.exceptions.MailerSendException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<?> handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
        return new ResponseEntity<>(
                new ErrorResponse("USER_ERROR", ex.getMessage()),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            String field = propertyPath.substring(propertyPath.lastIndexOf('.') + 1);
            errors.put(field, violation.getMessage());
        }
        return new ResponseEntity<>(
                new ErrorResponse("VALIDATION_ERROR", errors),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(
                new ErrorResponse("VALIDATION_ERROR", errors),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex) {
        return new ResponseEntity<>(
                new ErrorResponse("VALIDATION_ERROR", ex.getMessage()),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(UserNotFoundException ex) {
        return new ResponseEntity<>(
                new ErrorResponse("NOT_FOUND_ERROR", ex.getMessage()),
                HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MailAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleMailException(MailAuthenticationException ex) {
        return new ResponseEntity<>(
                new ErrorResponse("MAIL_ERROR", ex.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MailerSendException.class)
    public ResponseEntity<ErrorResponse> handleMailException(MailerSendException ex) {
        return new ResponseEntity<>(
                new ErrorResponse("MAILERSEND_EXCEPTION", ex.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleRequestBodyException(HttpMessageNotReadableException ex) {
        return new ResponseEntity<>(
                new ErrorResponse("REQUEST_BODY_ERROR", "Required request body is missing"),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex) {
        return new ResponseEntity<>(
                new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
