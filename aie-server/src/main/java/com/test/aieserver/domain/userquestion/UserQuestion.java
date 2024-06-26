package com.test.aieserver.domain.userquestion;


import com.test.aieserver.domain.question.Question;
import com.test.aieserver.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class UserQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @ManyToOne
    private User user;
    @ManyToOne
    private Question question;
    @Column
    private Integer cnt;
    @Column
    private Boolean nowAnswering;
}
