package com.test.aieserver.domain.question.dto;

import com.test.aieserver.domain.question.Question;
import lombok.*;

public class QuestionResponseDto {
    @Builder
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionInfo {
        private String question;
        private String type;

        public static QuestionInfo of(Question question) {
            return QuestionInfo.builder()
                    .question(question.getQuestionContent())
                    .type(question.getQuestionType().getType())
                    .build();
        }
    }
}
