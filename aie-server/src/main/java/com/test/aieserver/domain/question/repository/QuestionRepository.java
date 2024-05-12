package com.test.aieserver.domain.question.repository;

import com.test.aieserver.domain.question.Question;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question,Integer> {
    Optional<Question> findByUid(String uid);
    @NonNull
    Optional<Question> findById(@NonNull Integer id);
}
