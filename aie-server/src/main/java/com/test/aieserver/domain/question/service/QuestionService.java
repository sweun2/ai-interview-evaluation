package com.test.aieserver.domain.question.service;

import com.test.aieserver.domain.question.Question;
import com.test.aieserver.domain.question.repository.QuestionRepository;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class QuestionService {
    private final QuestionRepository questionRepository;
    public Question constructRandQuestion(String sessionId) {
        //randum한 값 반환 예정
        @NonNull Optional<Question> question = questionRepository.findById(1);
        log.info("session Id : {}",sessionId);
        return question.get();
    }
}
