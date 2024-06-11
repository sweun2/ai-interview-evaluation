package com.test.aieserver.config.error;


import com.test.aieserver.config.error.code.ResponseErrorCode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodeExample {
    ResponseErrorCode value();
}