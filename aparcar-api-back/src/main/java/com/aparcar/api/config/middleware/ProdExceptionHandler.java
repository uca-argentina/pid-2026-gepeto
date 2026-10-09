package com.aparcar.api.config.middleware;

import com.aparcar.api.dto.ErrorResponseDto;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static com.aparcar.api.config.ApplicationConstants.NOT_DEV_ENV;

@ControllerAdvice
@Profile(NOT_DEV_ENV)
public class ProdExceptionHandler {
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponseDto> handleOptimisticLockingFailureException() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponseDto(
                HttpStatus.CONFLICT.value(),
                "El registro cambio mientras operabas. Actualiza la pantalla y volve a intentarlo.", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleException(Exception e) {
        return ResponseEntity.internalServerError().body(
                new ErrorResponseDto(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "An unexpected error has occurred. Please try again later.",
                        null
                )
        );
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNotFoundException(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponseDto(
                        HttpStatus.NOT_FOUND.value(),
                        e.getMessage(),
                        null
                )
        );
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(ValidationException e) {
        return ResponseEntity.badRequest().body(
                new ErrorResponseDto(
                        HttpStatus.BAD_REQUEST.value(),
                        e.getMessage(),
                        e.getErrors()
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .toList();

        return ResponseEntity.badRequest().body(
                new ErrorResponseDto(
                        HttpStatus.BAD_REQUEST.value(),
                        "Validation failed",
                        errors
                ));
    }

    /**
     * JSON mal formado o con un valor que no se puede convertir (por ejemplo
     * un tipo de cochera que no existe como clave de un mapa). Es un error de
     * quien llama, asi que va 400 y no el 500 generico. Mismo mensaje que en
     * {@link DevExceptionHandler}.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(
                new ErrorResponseDto(
                        HttpStatus.BAD_REQUEST.value(),
                        "The request body is missing or malformed",
                        null
                )
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDeniedException(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                new ErrorResponseDto(
                        HttpStatus.FORBIDDEN.value(),
                        e.getMessage(),
                        null
                )
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDto> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        List<String> errors = e.getAllErrors().stream()
                .map(org.springframework.context.MessageSourceResolvable::getDefaultMessage)
                .distinct()
                .toList();

        return ResponseEntity.badRequest().body(
                new ErrorResponseDto(
                        HttpStatus.BAD_REQUEST.value(),
                        "Validation failed",
                        errors
                ));
    }
}
