package com.test.aieserver.domain.question.service;

import com.test.aieserver.domain.question.Question;
import com.test.aieserver.domain.question.repository.QuestionRepository;
import com.test.aieserver.domain.user.User;
import com.test.aieserver.domain.user.repository.UserRepository;
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
    private final UserRepository userRepository;
    public Question constructRandQuestion(String sessionId) {
        User user = userService.makeUserIfNotPresent(sessionId);
        Optional<Question> optionalQuestion;
        log.info("session Id : {}", sessionId);

        while (true) {
            optionalQuestion = questionRepository.findRandomQuestion();

            if (optionalQuestion.isEmpty()) {
                throw new RuntimeException("no question");
            }

            Optional<UserQuestion> userQuestionOptional = userQuestionRepository.findByUserAndQuestion(user, optionalQuestion.get());
            if (userQuestionOptional.isEmpty()) {
                break;
            }
        }
        if(!userQuestionRepository.findByUser(user).isEmpty()){
            userQuestionRepository.findByUser(user).forEach(userQuestion -> {
                userQuestion.setNowAnswering(false);
                userQuestionRepository.save(userQuestion);
            });
        }
        UserQuestion userQuestion = UserQuestion.builder()
                .user(user)
                .question(optionalQuestion.get())
                .cnt(userQuestionRepository.findByHighestCnt(user.getId())
                        .map(UserQuestion::getCnt)
                        .orElse(0) + 1)
                .nowAnswering(true)
                .build();

        user.getUserQuestionSet().add(userQuestion);
        userRepository.save(user);

        return optionalQuestion.get();
    }
}

