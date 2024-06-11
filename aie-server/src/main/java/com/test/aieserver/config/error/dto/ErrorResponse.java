package com.test.aieserver.config.error.dto;

import com.test.aieserver.config.error.code.ErrorReason;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ErrorResponse {

    private final int status;
    private final String code;
    private final String reason;
    private final LocalDateTime timeStamp;


    public ErrorResponse(ErrorReason errorReason) {
        this.status = errorReason.getStatus();
        this.code = errorReason.getCode();
        this.reason = errorReason.getReason();
        this.timeStamp = LocalDateTime.now();
    }

    public ErrorResponse(int status, String code, String reason) {
        this.status = status;
        this.code = code;
        this.reason = reason;
        this.timeStamp = LocalDateTime.now();
    }
}
