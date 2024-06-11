package com.test.aieserver.config.error.handler;

import com.test.aieserver.config.error.code.ResponseErrorCode;
import com.test.aieserver.config.error.dto.ErrorResponse;
import com.test.aieserver.config.error.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ResponseErrorCode responseErrorCode = e.getResponseErrorCode();
        ErrorResponse response = new ErrorResponse(responseErrorCode.getErrorReason());
        return new ResponseEntity<>(response, HttpStatus.valueOf(responseErrorCode.getErrorReason().getStatus()));
    }
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        ResponseErrorCode responseErrorCode = ResponseErrorCode.INTERNAL_SERVER_ERROR;
        ErrorResponse response = new ErrorResponse(responseErrorCode.getErrorReason());
        return new ResponseEntity<>(response, HttpStatus.valueOf(responseErrorCode.getErrorReason().getStatus()));
    }
}
