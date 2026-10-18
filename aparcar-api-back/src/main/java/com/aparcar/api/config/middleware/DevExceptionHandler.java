package com.aparcar.api.config.middleware;

import com.aparcar.api.dto.ErrorResponseDto;
import com.aparcar.api.exception.ArchivoDemasiadoGrandeException;
import com.aparcar.api.exception.ConflictoException;
import com.aparcar.api.exception.TipoArchivoNoPermitidoException;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;
import java.util.List;

import static com.aparcar.api.config.ApplicationConstants.DEV_ENV;

@ControllerAdvice
@Profile(DEV_ENV)
public class DevExceptionHandler {
    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorResponseDto> handleConflictoException(ConflictoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ErrorResponseDto(HttpStatus.CONFLICT.value(), e.getMessage(), null));
    }

    @ExceptionHandler(ArchivoDemasiadoGrandeException.class)
    public ResponseEntity<ErrorResponseDto> handleArchivoDemasiadoGrandeException(ArchivoDemasiadoGrandeException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(
                new ErrorResponseDto(HttpStatus.CONTENT_TOO_LARGE.value(), e.getMessage(), null));
    }

    /** El archivo supero el limite de multipart antes de llegar al controller. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDto> handleMaxUploadSizeExceededException() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(new ErrorResponseDto(
                HttpStatus.CONTENT_TOO_LARGE.value(), "El archivo supera el tamaño permitido.", null));
    }

    @ExceptionHandler(TipoArchivoNoPermitidoException.class)
    public ResponseEntity<ErrorResponseDto> handleTipoArchivoNoPermitidoException(TipoArchivoNoPermitidoException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(
                new ErrorResponseDto(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), e.getMessage(), null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponseDto> handleHttpMediaTypeNotSupportedException() {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new ErrorResponseDto(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "El formato del pedido no es el esperado.", null));
    }

    /** Un multipart sin el archivo, o que no se pudo leer. */
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ErrorResponseDto> handleMultipartException() {
        return ResponseEntity.badRequest().body(new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(), "Falta el archivo o no se pudo leer.", null));
    }

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
                        e.getMessage(), // Dev only
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

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleConstraintViolationException(ConstraintViolationException e) {
        List<String> errors = e.getConstraintViolations().stream()
                .map(this::getExceptionMessage)
                .distinct()
                .toList();

        return ResponseEntity.badRequest().body(
                new ErrorResponseDto(
                        HttpStatus.BAD_REQUEST.value(),
                        "Validation failed",
                        errors
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        List<String> errors = List.of(e.getPropertyName() + ": " + "Value must be of type " +
                        (e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "UNKNOWN") +
                        ".",
                "NOTE: the expected date format is yyyy-MM-dd.");
        return ResponseEntity.badRequest().body(
                new ErrorResponseDto(
                        HttpStatus.BAD_REQUEST.value(),
                        "Validation failed",
                        errors
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNoResourceFoundException(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponseDto(
                        HttpStatus.NOT_FOUND.value(),
                        "The requested route does not exist or is missing a value",
                        List.of(e.getResourcePath())
                )
        );
    }

    @ExceptionHandler({MissingPathVariableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponseDto> handleMissingPathOrParameterException(Exception ignored) {
        return ResponseEntity.badRequest().body(
                new ErrorResponseDto(
                        HttpStatus.BAD_REQUEST.value(),
                        "The requested route is missing one or more values",
                        null
                )
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDto> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
                new ErrorResponseDto(
                        HttpStatus.METHOD_NOT_ALLOWED.value(),
                        "The requested route does not support the method " + e.getMethod(),
                        null
                )
        );
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponseDto> handleHttpMessageNotReadableException(Exception ignored) {
        return ResponseEntity.badRequest().body(
                new ErrorResponseDto(
                        HttpStatus.BAD_REQUEST.value(),
                        "The request body is missing or malformed",
                        null
                )
        );
    }

    private String getExceptionMessage(ConstraintViolation<?> ex) {
        return Arrays.stream(ex.getPropertyPath().toString().split("\\.")).toList().getLast() + ": " + ex.getMessage();
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
