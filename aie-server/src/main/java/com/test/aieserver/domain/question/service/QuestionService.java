package com.test.aieserver.domain.question.service;

import com.test.aieserver.domain.question.Question;
import com.test.aieserver.domain.question.repository.QuestionRepository;
import com.test.aieserver.domain.user.User;
import com.test.aieserver.domain.user.service.UserService;
import com.test.aieserver.domain.userquestion.UserQuestion;
import com.test.aieserver.domain.userquestion.repository.UserQuestionRepository;
import jakarta.transaction.Transactional;
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
    private final UserQuestionRepository userQuestionRepository;
    private final UserService userService;
    public Question constructRandQuestion(String sessionId) {
        User user  = userService.makeUserIfNotPresent(sessionId);
        // 랜덤한 질문을 가져옴
        Optional<Question> optionalQuestion = questionRepository.findRandomQuestion();
        log.info("session Id : {}", sessionId);

        if (optionalQuestion.isEmpty()) {
            throw new RuntimeException("no question");
        }
        Optional<UserQuestion> userQuestionOptional = userQuestionRepository.findByUserAndQuestion(user, optionalQuestion.get());
        if (userQuestionOptional.isPresent()) {
            userQuestionOptional.get().setQuestion(optionalQuestion.get());
            userQuestionRepository.save(userQuestionOptional.get());
        } else {
            userQuestionRepository.save(UserQuestion.builder()
                    .user(user)
                    .question(optionalQuestion.get())
                    .build());
        }

        return optionalQuestion.get();
    }
}

