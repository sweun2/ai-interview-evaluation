package com.test.aieserver.domain.answer.service;

import com.test.aieserver.domain.answer.dto.OpenAIResponse;
import com.test.aieserver.domain.question.Question;
import com.test.aieserver.domain.question.repository.QuestionRepository;
import com.test.aieserver.domain.stream.VideoStreamHandler;
import com.test.aieserver.domain.stt.SpeechToTextService;
import com.test.aieserver.domain.user.User;
import com.test.aieserver.domain.user.repository.UserRepository;
import com.test.aieserver.domain.userquestion.UserQuestion;
import com.test.aieserver.domain.userquestion.repository.UserQuestionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class AnswerService {
    private final SpeechToTextService speechToTextService;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final UserQuestionRepository userQuestionRepository;

    @Value("file:${openai.api.key}")
    private Resource apiKey;
    public String getApiKey() throws IOException {
        return new String(Files.readAllBytes(apiKey.getFile().toPath())).trim();
    }

    public String requestWithAnswer(String sessionId) throws IOException {
        File videoFile = new File("./video/"+sessionId+".webm");
        String transcribeText = speechToTextService.transcribe(videoFile);
        log.info("transcribed text : {}",transcribeText);

        return requestToOpenAI(transcribeText,sessionId);
    }
    public String requestToOpenAI(String reqMsg,String sessionId) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        log.info("apiKey ; {}",getApiKey());

        String url = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + getApiKey());

        User user = userRepository.findByNickname(sessionId)
                .orElseThrow(
                        () -> new RuntimeException("User not found")
                );
        UserQuestion userQuestion = userQuestionRepository.findByUser(user)
                .orElseThrow(
                        () -> new RuntimeException("UserQuestion not found")
                );
        HttpEntity<String> entity = getStringHttpEntity(reqMsg, userQuestion, headers);

        ResponseEntity<OpenAIResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, OpenAIResponse.class);
        OpenAIResponse openAIResponse = response.getBody();
        String result =  openAIResponse != null && !openAIResponse.getChoices().isEmpty()
                ? openAIResponse.getChoices().get(0).getMessage().getContent()
                : "No content received";
        log.info(result);

        userQuestionRepository.delete(userQuestion);
        return result;
    }

    private static HttpEntity<String> getStringHttpEntity(String reqMsg, UserQuestion userQuestion, HttpHeaders headers) {
        Question question = userQuestion.getQuestion();
        String requestBody = String.format("{\"model\":\"gpt-3.5-turbo\",\"messages\":[{\"role\":\"user\",\"content\":\"질문이 '%s' 이고 답변은 '%s' 일 때, 답변을 잘한건지 평가해줘. 답변이 100점 만점 기준으로 몇 점인지 정확하게 표현해주고, 그 이유도 설명해줘. 내가 한 답변 - 점수, 이유 순으로 적어줘. 만약 답변이 정상적이지 않다면, '답변이 불충분합니다' 라고 출력해줘.\"}]}", question.getQuestionContent(), reqMsg);

        return new HttpEntity<>(requestBody, headers);
    }
}
