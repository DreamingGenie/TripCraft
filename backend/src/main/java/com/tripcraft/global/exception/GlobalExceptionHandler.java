package com.tripcraft.global.exception;

import com.tripcraft.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        log.warn("ResponseStatusException: {} {}", ex.getStatusCode(), ex.getReason());
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.fail(message, ex.getStatusCode().toString()));
    }

    /** DB UNIQUE 등 무결성 위반 → 500 대신 의미 있는 409 메시지(중복 추가 등). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String root = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        log.warn("DataIntegrityViolation: {}", root);
        String message = "이미 존재하는 항목이에요.";
        if (root != null) {
            if (root.contains("uq_candidate_place")) message = "이미 보관함에 있는 장소예요.";
            else if (root.contains("uq_member_place")) message = "이미 등록한 내 장소예요.";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(message, "CONFLICT"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail("서버 오류가 발생했습니다.", "INTERNAL_SERVER_ERROR"));
    }
}
