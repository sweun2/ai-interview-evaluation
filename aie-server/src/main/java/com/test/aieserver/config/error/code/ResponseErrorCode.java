package com.test.aieserver.config.error.code;

import com.test.aieserver.config.error.ExplainError;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.util.Objects;

@Getter
@AllArgsConstructor
public enum ResponseErrorCode {

    @ExplainError("이미지 파일이 형식 에러,이미지 파일 형식을 확인해 주세요. jpg, png 만 가능합니다. ")
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR.value(), "IMAGE_003","잘못된 image 파일 형식입니다.");
    ;
    private final int status;
    private final String code;
    private final String reason;

    public ErrorReason getErrorReason() {
        return ErrorReason.builder().reason(reason).code(code).status(status).build();
    }
    public String getExplainError() {
        try {
            Field field = this.getClass().getDeclaredField(this.name());
            field.setAccessible(true);  // 필드에 접근할 수 있도록 설정
            ExplainError annotation = field.getAnnotation(ExplainError.class);
            return Objects.nonNull(annotation) ? annotation.value() : this.getReason();
        } catch (NoSuchFieldException | SecurityException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }
}
