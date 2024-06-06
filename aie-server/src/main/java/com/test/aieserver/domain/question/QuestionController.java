package com.test.aieserver.domain.question;

import com.test.aieserver.domain.question.dto.QuestionResponseDto;
import com.test.aieserver.domain.question.service.QuestionService;
import com.test.aieserver.domain.stream.VideoStreamHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/question")
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionService questionService;
    @PostMapping("/rand/{sessionId}")
    public ResponseEntity<QuestionResponseDto.QuestionInfo> getNewQuestion(@PathVariable String sessionId) {
        return ResponseEntity.ok(QuestionResponseDto.QuestionInfo.of(questionService.constructRandQuestion(sessionId)));
    }
}
