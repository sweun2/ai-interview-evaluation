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
    private UUID id;
    @OneToOne
    private User user;
    @OneToOne
    private Question question;
}
