package com.mudhut.nudge.utils.exceptions

import com.mailersend.sdk.exceptions.MailerSendException
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mail.MailAuthenticationException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    companion object {
        private const val ERROR_CODE_VALIDATION = "VALIDATION_ERROR"
        private const val ERROR_CODE_AUTHENTICATION = "AUTHENTICATION_ERROR"
        private const val ERROR_CODE_AUTHORIZATION = "AUTHORIZATION_ERROR"
        private const val ERROR_CODE_NOT_FOUND = "NOT_FOUND_ERROR"
        private const val ERROR_CODE_MAIL = "MAIL_ERROR"
        private const val ERROR_CODE_INTERNAL = "INTERNAL_ERROR"
        private const val ERROR_CODE_USER = "USER_ERROR"
        private const val ERROR_CODE_REQUEST = "REQUEST_ERROR"
    }

    @ExceptionHandler(UserAlreadyExistsException::class)
    fun handleUserAlreadyExistsException(ex: UserAlreadyExistsException): ResponseEntity<ErrorResponse> {
        logger.warn("User already exists: {}", ex.message)
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_USER, ex.message ?: "User already exists"),
            HttpStatus.CONFLICT
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        logger.warn("Constraint violation: {}", ex.message)
        val violations = ex.constraintViolations
        if (violations.isNullOrEmpty()) {
            return ResponseEntity(
                ErrorResponse(ERROR_CODE_VALIDATION, ex.message ?: "Validation failed"),
                HttpStatus.BAD_REQUEST
            )
        }
        val errors = mutableMapOf<String, String>()
        for (violation in violations) {
            val propertyPath = violation.propertyPath?.toString() ?: "unknown"
            val field = propertyPath.substringAfterLast('.').ifEmpty { propertyPath }
            errors[field] = violation.message ?: "Invalid value"
        }
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_VALIDATION, errors),
            HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        logger.warn("Method argument validation failed: {}", ex.message)
        val errors = mutableMapOf<String, String?>()
        ex.bindingResult.allErrors.forEach { error ->
            val fieldName = (error as FieldError).field
            val errorMessage = error.defaultMessage
            errors[fieldName] = errorMessage
        }
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_VALIDATION, errors),
            HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("Illegal argument: {}", ex.message)
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_VALIDATION, ex.message ?: "Invalid argument"),
            HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFoundException(ex: UserNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("User not found: {}", ex.message)
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_NOT_FOUND, ex.message ?: "User not found"),
            HttpStatus.NOT_FOUND
        )
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentialsException(ex: BadCredentialsException): ResponseEntity<ErrorResponse> {
        logger.warn("Authentication failed: Bad credentials")
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_AUTHENTICATION, "Invalid username or password"),
            HttpStatus.UNAUTHORIZED
        )
    }

    @ExceptionHandler(DisabledException::class)
    fun handleDisabledException(ex: DisabledException): ResponseEntity<ErrorResponse> {
        logger.warn("Authentication failed: Account disabled")
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_AUTHENTICATION, "Account is disabled"),
            HttpStatus.UNAUTHORIZED
        )
    }

    @ExceptionHandler(LockedException::class)
    fun handleLockedException(ex: LockedException): ResponseEntity<ErrorResponse> {
        logger.warn("Authentication failed: Account locked")
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_AUTHENTICATION, "Account is locked"),
            HttpStatus.UNAUTHORIZED
        )
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        logger.warn("Authorization failed: Access denied")
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_AUTHORIZATION, "You don't have permission to access this resource"),
            HttpStatus.FORBIDDEN
        )
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: AuthenticationException): ResponseEntity<ErrorResponse> {
        logger.warn("Authentication failed: {}", ex.message)
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_AUTHENTICATION, "Authentication failed"),
            HttpStatus.UNAUTHORIZED
        )
    }

    @ExceptionHandler(MailAuthenticationException::class)
    fun handleMailException(ex: MailAuthenticationException): ResponseEntity<ErrorResponse> {
        logger.error("Mail authentication error: {}", ex.message)
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_MAIL, "Failed to authenticate with mail server"),
            HttpStatus.INTERNAL_SERVER_ERROR
        )
    }

    @ExceptionHandler(MailerSendException::class)
    fun handleMailException(ex: MailerSendException): ResponseEntity<ErrorResponse> {
        logger.error("MailerSend error: {}", ex.message)
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_MAIL, "Failed to send email"),
            HttpStatus.INTERNAL_SERVER_ERROR
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleRequestBodyException(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        logger.warn("Request body error: {}", ex.message)
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_REQUEST, "Required request body is missing or malformed"),
            HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(BusinessNotFoundException::class)
    fun handleBusinessNotFoundException(ex: BusinessNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("Business not found: {}", ex.message)
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_NOT_FOUND, ex.message ?: "Business not found"),
            HttpStatus.NOT_FOUND
        )
    }

    @ExceptionHandler(BusinessAccessDeniedException::class)
    fun handleBusinessAccessDeniedException(ex: BusinessAccessDeniedException): ResponseEntity<ErrorResponse> {
        logger.warn("Business access denied: {}", ex.message)
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_AUTHORIZATION, ex.message ?: "Access denied"),
            HttpStatus.FORBIDDEN
        )
    }

    @ExceptionHandler(InvitationException::class)
    fun handleInvitationException(ex: InvitationException): ResponseEntity<ErrorResponse> {
        logger.warn("Invitation error: {}", ex.message)
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_VALIDATION, ex.message ?: "Invitation error"),
            HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled exception occurred", ex)
        return ResponseEntity(
            ErrorResponse(ERROR_CODE_INTERNAL, "An unexpected error occurred"),
            HttpStatus.INTERNAL_SERVER_ERROR
        )
    }
}
