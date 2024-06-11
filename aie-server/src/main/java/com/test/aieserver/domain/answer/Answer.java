package com.test.aieserver.domain.answer;

import com.test.aieserver.domain.question.Question;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Answer {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @Column(nullable = false)
    private String answerContent;
    @OneToOne
    private Question question;
}
