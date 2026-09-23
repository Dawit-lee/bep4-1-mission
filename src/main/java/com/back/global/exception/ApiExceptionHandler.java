package com.back.global.exception;

import com.back.global.rsData.RsData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<RsData<Void>> handle(DomainException exception) {
        int status = Integer.parseInt(exception.getResultCode().split("-", 2)[0]);
        return ResponseEntity.status(status)
                .body(new RsData<>(exception.getResultCode(), exception.getMsg()));
    }
}
