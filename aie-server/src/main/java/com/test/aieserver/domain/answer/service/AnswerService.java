package com.test.aieserver.domain.answer.service;

import com.test.aieserver.domain.answer.dto.OpenAIResponse;
import com.test.aieserver.domain.question.repository.QuestionRepository;
import com.test.aieserver.domain.stream.VideoStreamHandler;
import com.test.aieserver.domain.stt.SpeechToTextService;
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

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class AnswerService {
    private final SpeechToTextService speechToTextService;
    private final QuestionRepository questionRepository;

    @Value("file:${openai.api.key}")
    private Resource apiKey;
    public String getApiKey() throws IOException {
        return new String(Files.readAllBytes(apiKey.getFile().toPath())).trim();
    }

    public String requestWithAnswer(String sessionId) throws IOException {
        File videoFile = new File("./video/"+sessionId+".webm");
        String transcribeText = speechToTextService.transcribe(videoFile);
        log.info("transcribed text : {}",transcribeText);

        return requestToOpenAI(transcribeText);
    }
    public String requestToOpenAI(String reqMsg) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        log.info("apiKey ; {}",getApiKey());

        String url = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + getApiKey());

        String question = questionRepository.findById(1).get().getQuestionContent();
        String requestBody = String.format("""
            {
                "model":"gpt-3.5-turbo",
                "messages":
                [
                     {
                         "role": "user",
                         "content": "질문이 '%s' 이고 답변은 '%s' 일 때, 답변을 잘한건지 평가해줘."
                     }
                ]
            }
            """, question, reqMsg);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<OpenAIResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, OpenAIResponse.class);
        OpenAIResponse openAIResponse = response.getBody();
        String result =  openAIResponse != null && !openAIResponse.getChoices().isEmpty()
                ? openAIResponse.getChoices().get(0).getMessage().getContent()
                : "No content received";
        log.info(result);

        return result;
    }
}
