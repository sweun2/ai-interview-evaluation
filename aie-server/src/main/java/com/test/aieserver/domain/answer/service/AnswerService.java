package com.test.aieserver.domain.answer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.aieserver.domain.answer.dto.OpenAIResponse;
import com.test.aieserver.domain.question.Question;
import com.test.aieserver.domain.question.repository.QuestionRepository;
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
import java.util.HashMap;
import java.util.Map;

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

    public String requestWithVideoFile(String sessionId) throws IOException {
        File videoFile = new File("./video/"+sessionId+".webm");
        String transcribeText = speechToTextService.transcribe(videoFile);
        log.info("transcribed text : {}",transcribeText);

        return requestToOpenAI(transcribeText,sessionId);
    }
    public String requestWithText(String text,String sessionId) throws IOException {
        return requestToOpenAI(text,sessionId);
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

    private static HttpEntity<String> getStringHttpEntity(String reqMsg, UserQuestion userQuestion, HttpHeaders headers) throws IOException {
        Question question = userQuestion.getQuestion();

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", "gpt-3.5-turbo");

        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are a highly knowledgeable interview evaluator. Your task is to evaluate interview answers based on their quality, relevance, and completeness.");

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", String.format("질문: '%s'.\n응답: '%s'.\n\n이 응답이 질문에 얼마나 잘 대답했는지 평가해줘. 다음 기준에 따라 100점 만점 기준으로 점수를 매겨줘: 1) 답변의 정확성, 2) 답변의 관련성, 3) 답변의 완전성. 점수를 매긴 후, 각 기준에 대한 평가 이유를 설명해줘.\n\n형식:\n점수: [점수]\n이유:\n1) 정확성: [설명]\n2) 관련성: [설명]\n3) 완전성: [설명]\n만약 답변이 정상적이지 않다면, '답변이 불충분합니다' 라고 출력해줘.", question.getQuestionContent(), reqMsg));

        requestBodyMap.put("messages", new Object[]{systemMessage, userMessage});

        String requestBody = objectMapper.writeValueAsString(requestBodyMap);

        return new HttpEntity<>(requestBody, headers);
    }


}
