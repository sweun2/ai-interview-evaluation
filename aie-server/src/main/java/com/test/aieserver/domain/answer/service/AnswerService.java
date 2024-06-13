package com.test.aieserver.domain.answer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.aieserver.domain.answer.Answer;
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
    private final UserRepository userRepository;
    private final UserQuestionRepository userQuestionRepository;

    @Value("file:${openai.api.key}")
    private Resource apiKey;

    public String getApiKey() throws IOException {
        return new String(Files.readAllBytes(apiKey.getFile().toPath())).trim();
    }

    public String requestWithVideoFile(String sessionId) throws IOException {
        File videoFile = new File("./video/" + sessionId + ".webm");
        String transcribeText = speechToTextService.transcribe(videoFile);
        log.info("transcribed text : {}", transcribeText);

        return requestToOpenAI(transcribeText, sessionId);
    }

    public String requestWithText(String text, String sessionId) throws IOException {
        return requestToOpenAI(text, sessionId);
    }

    public String requestToOpenAI(String reqMsg, String sessionId) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        log.info("apiKey : {}", getApiKey());

        String url = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + getApiKey());

        User user = userRepository.findByNickname(sessionId)
                .orElseThrow(
                        () -> new RuntimeException("User not found")
                );
        UserQuestion userQuestion = userQuestionRepository.findByHighestCnt(user.getId())
                .orElseThrow(
                        () -> new RuntimeException("UserQuestion not found")
                );
        HttpEntity<String> entity = getStringHttpEntity(reqMsg, userQuestion, headers);

        ResponseEntity<OpenAIResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, OpenAIResponse.class);
        OpenAIResponse openAIResponse = response.getBody();
        String result = openAIResponse != null && !openAIResponse.getChoices().isEmpty()
                ? openAIResponse.getChoices().get(0).getMessage().getContent()
                : "No content received";
        log.info("result:{}", result);

        return result;
    }

    private static HttpEntity<String> getStringHttpEntity(String reqMsg, UserQuestion userQuestion, HttpHeaders headers) throws IOException {
        Question question = userQuestion.getQuestion();
        log.info("reqMsg : {}", reqMsg);
        Answer answer = userQuestion.getQuestion().getAnswer();

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", "gpt-4o-2024-05-13");

        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are a helpful assistant.");

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", String.format(
                """
                        질문: '%s'
                        응답: '%s'
                        
                        당신은 컴퓨터 엔지니어 면접 평가관입니다.
                        당신은 이 질문에 대한 답변을 평가해야 합니다.
                        다음은 질문에 대한 응답을 평가하기 위한 기준입니다:
                        *** 제일 중요 *** 음성이 곧바로 인식되어 텍스트로 전환된 상태이기 때문에 응답을 의역을 하고 의역한 응답을 평가하여 결과를 도출하고, 평가 내역에는 의역 전 응답을 절대 포함시키지 마세요.

                        1. **정확성**
                        - 답변이 질문에 정확하게 답하고 있는지 확인하세요. 제공된 정보가 사실에 근거하고 있으며 오류가 없는지 평가하세요.
                        - 구체적인 데이터, 예시, 또는 출처가 포함되어 있는지 확인하세요.
                        - 답변이 질문의 본질을 이해하고 있는지, 핵심 내용을 제대로 전달하고 있는지 평가하세요.
                        - 답변의 정보가 최신인지, 과학적 또는 학문적으로 인정받은 사실에 기반하고 있는지 검토하세요.
                        - 논리적인 일관성을 유지하고 있는지 확인하세요. 답변이 모순되지 않고 일관된 논리적 흐름을 가지고 있는지 평가하세요.

                        2. **관련성**
                        - 답변이 질문과 직접적으로 관련되어 있는지 평가하세요. 답변이 질문의 맥락과 주제를 벗어나지 않고 적절히 대응하고 있는지 확인하세요.
                        - 불필요한 정보가 포함되어 있지 않은지, 질문에 맞춘 내용인지 확인하세요.
                        - 답변이 질문자의 의도와 요구를 정확히 반영하고 있는지 평가하세요. 질문자의 기대를 충족시키는지 확인하세요.
                        - 주어진 질문에 대해 명확하게 답변하고 있는지, 핵심 주제에 집중하고 있는지 평가하세요.
                        - 관련된 배경 지식이나 추가 정보를 제공하여 질문에 대한 이해를 돕고 있는지 확인하세요.

                        3. **완전성**
                        - 답변이 질문에 대해 충분히 설명하고 있는지 평가하세요. 중요한 세부 사항을 빠뜨리지 않고 포괄적으로 설명하는지 확인하세요.
                        - 답변이 명확하고 이해하기 쉽게 구성되어 있는지, 질문의 모든 측면을 다루고 있는지 평가하세요.
                        - 답변이 논리적인 구조를 가지고 있으며, 각 부분이 서로 잘 연결되어 있는지 확인하세요.
                        - 질문에 대한 답변이 깊이 있고, 표면적인 정보에 그치지 않고 심층적인 설명을 제공하고 있는지 평가하세요.
                        - 질문에 대한 다양한 관점을 제공하여, 답변이 균형 잡히고 종합적인 시각을 제공하고 있는지 확인하세요.

                        응답을 평가할 때, 다음을 염두에 두세요:
                        - **문맥 변환**:  답변이 음성을 텍스트로 직역한 것이므로, 문맥상 적절하게 변환해 주세요.
                        - **30초 내의 답변**: 답변이 30초 내에 생성되었음을 고려하여 평가하세요. 짧은 시간 내에 제공된 정보의 질과 양을 평가하세요.
                        - **응답의 명확성**: 답변이 명확하고 간결하며 이해하기 쉽게 작성되어 있는지 평가하세요. 복잡한 개념이나 아이디어가 쉽게 전달되고 있는지 확인하세요.
                        - **적절한 예시 사용**: 답변에 적절한 예시나 비유가 포함되어 있어, 질문자가 쉽게 이해할 수 있도록 돕고 있는지 평가하세요.

                        각 기준에 대해 100점 만점 기준으로 점수를 매기고, 평가 이유를 상세히 설명해 주세요. 답변이 정상적이지 않다면 '답변이 불충분합니다'라고 출력하세요.

                        평가 형식:
                        점수: [점수]
                        이유:
                        1) 정확성: [설명]
                        2) 관련성: [설명]
                        3) 완전성: [설명]""",
                question.getQuestionContent(), reqMsg.strip()
        ));

        requestBodyMap.put("messages", new Object[]{systemMessage, userMessage});

        String requestBody = objectMapper.writeValueAsString(requestBodyMap);

        return new HttpEntity<>(requestBody, headers);
    }
}
