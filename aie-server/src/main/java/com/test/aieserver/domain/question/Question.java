package com.test.aieserver.domain.question;

import jakarta.persistence.*;
import lombok.*;
import org.checkerframework.checker.units.qual.C;

import java.util.UUID;

@Entity
@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Question {
    @Getter
    public enum QuestionType {
        VOICE("음성"), SUBJECTIVE("주관식");

        private final String type;

        QuestionType(String type) {
            this.type = type;
        }
    }
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @Column(nullable = false)
    private String questionContent;
    @Column(nullable = false)
    private QuestionType questionType;

}
