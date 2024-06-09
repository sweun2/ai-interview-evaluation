package com.test.aieserver.domain.user;


import com.test.aieserver.domain.userquestion.UserQuestion;
import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @Column(unique = true)
    private String nickname;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserQuestion> userQuestionSet;
}
