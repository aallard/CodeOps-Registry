package com.codeops.registry.config;

import com.codeops.registry.dto.response.ErrorResponse;
import com.codeops.registry.exception.AuthorizationException;
import com.codeops.registry.exception.CodeOpsRegistryException;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFound_returns404WithMessage() {
        NotFoundException ex = new NotFoundException("Service", UUID.randomUUID());
        ResponseEntity<ErrorResponse> response = handler.handleCodeOpsNotFound(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).contains("not found");
    }

    @Test
    void handleNotFound_withFieldLookup_returns404() {
        NotFoundException ex = new NotFoundException("Service", "slug", "my-service");
        ResponseEntity<ErrorResponse> response = handler.handleCodeOpsNotFound(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().message()).contains("slug");
    }

    @Test
    void handleValidation_returns400WithMessage() {
        ValidationException ex = new ValidationException("Port already allocated");
        ResponseEntity<ErrorResponse> response = handler.handleCodeOpsValidation(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Port already allocated");
    }

    @Test
    void handleAuthorization_returns403WithMessage() {
        AuthorizationException ex = new AuthorizationException("Not a team member");
        ResponseEntity<ErrorResponse> response = handler.handleCodeOpsAuth(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(403);
        assertThat(response.getBody().message()).isEqualTo("Not a team member");
    }

    @Test
    void handleGenericException_returns500WithSanitizedMessage() {
        Exception ex = new RuntimeException("Some internal detail");
        ResponseEntity<ErrorResponse> response = handler.handleGeneral(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An internal error occurred");
        assertThat(response.getBody().message()).doesNotContain("internal detail");
    }

    @Test
    void handleCodeOpsRegistryException_returns500() {
        CodeOpsRegistryException ex = new CodeOpsRegistryException("Internal app error");
        ResponseEntity<ErrorResponse> response = handler.handleCodeOps(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("An internal error occurred");
    }

    @Test
    void handleMethodArgumentNotValid_returns400WithFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));
        bindingResult.addError(new FieldError("request", "port", "must be positive"));

        Method method = this.getClass().getDeclaredMethod("setUp");
        MethodParameter param = new MethodParameter(method, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).contains("name: must not be blank");
        assertThat(response.getBody().message()).contains("port: must be positive");
    }

    @Test
    void handleMessageNotReadable_returns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error", new MockHttpInputMessage(new byte[0]));
        ResponseEntity<ErrorResponse> response = handler.handleMessageNotReadable(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Malformed request body");
    }

    @Test
    void handleIllegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid UUID");
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Invalid request");
    }
}
