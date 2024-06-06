package com.test.aieserver.domain.userquestion.repository;

import com.test.aieserver.domain.question.Question;
import com.test.aieserver.domain.user.User;
import com.test.aieserver.domain.userquestion.UserQuestion;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserQuestionRepository extends JpaRepository<UserQuestion,Integer> {
    Optional<UserQuestion> findByUserAndQuestion(@NonNull User user, @NonNull Question question);
    Optional<UserQuestion> findByUser(@NonNull User user);
}
