package com.test.aieserver.config.error.exception;

import com.test.aieserver.config.error.code.ResponseErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ResponseErrorCode responseErrorCode;

    public BusinessException(ResponseErrorCode responseErrorCode) {
        super(responseErrorCode.getErrorReason().getReason());
        this.responseErrorCode = responseErrorCode;
    }
}
