package com.test.aieserver.domain.answer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class OpenAIResponse {
    private String id;
    private String object;
    private List<Choice> choices;


    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    public static class Choice {
        private Message message;

        @Getter
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Message {
            private String role;
            private String content;

        }
    }
}