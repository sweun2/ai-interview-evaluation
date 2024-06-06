package com.test.aieserver.domain.question.repository;

import com.test.aieserver.domain.question.Question;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question,Integer> {
    @NonNull
    Optional<Question> findById(@NonNull Integer id);

    @Query(value = "SELECT * FROM Question ORDER BY RAND() LIMIT 1", nativeQuery = true)
    Optional<Question> findRandomQuestion();
}
