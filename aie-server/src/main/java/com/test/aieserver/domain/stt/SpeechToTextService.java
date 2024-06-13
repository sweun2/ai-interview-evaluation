package com.test.aieserver.domain.stt;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1p1beta1.*;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.protobuf.ByteString;
import com.test.aieserver.domain.question.Question;
import com.test.aieserver.domain.user.User;
import com.test.aieserver.domain.user.repository.UserRepository;
import com.test.aieserver.domain.userquestion.UserQuestion;
import com.test.aieserver.domain.userquestion.repository.UserQuestionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional
public class SpeechToTextService {
    private final UserQuestionRepository userQuestionRepository;
    private final UserRepository userRepository;
    @Value("${google-credential-json}")
    private String CREDENTIALS_PATH;

    private static final String GCS_BUCKET_NAME = "aie-bucket";

    public String transcribe(File audioFile,String sessionId) throws IOException {
        User user = userRepository.findByNickname(sessionId).orElseThrow(
                () -> new IllegalArgumentException("User not found with nickname: " + sessionId));
        UserQuestion userQuestion = userQuestionRepository.findByUserAndNowAnswering(user, true).orElseThrow(
                () -> new IllegalArgumentException("No question found for user with nickname: " + sessionId));
        Question question = userQuestion.getQuestion();

        if (!audioFile.exists()) {
            throw new IOException("Audio file does not exist at the specified path.");
        }

        byte[] audioBytes = Files.readAllBytes(audioFile.toPath());

        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(CREDENTIALS_PATH))
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));

        try (SpeechClient speechClient = SpeechClient.create(SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build())) {

            ByteString audioData = ByteString.copyFrom(audioBytes);
            RecognitionAudio recognitionAudio = RecognitionAudio.newBuilder()
                    .setContent(audioData)
                    .build();
            List<String> phrases = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(question.getTitle()+".txt"))) {
                String phrase;
                while ((phrase = br.readLine()) != null) {
                    phrases.add(phrase);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.WEBM_OPUS)
                    .setSampleRateHertz(48000)
                    .setLanguageCode("ko-KR")
                    .addAlternativeLanguageCodes("en-US")
                    .setModel("latest_long")
                    .setEnableAutomaticPunctuation(true)
                    .addSpeechContexts(SpeechContext.newBuilder().addAllPhrases(phrases).build())
                    .build();

            Storage storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService();
            BlobInfo blobInfo = BlobInfo.newBuilder(GCS_BUCKET_NAME, audioFile.getName()).build();
            Blob blob = storage.create(blobInfo, Files.readAllBytes(audioFile.toPath()));
            String audioUrl = "gs://" + GCS_BUCKET_NAME + "/" + audioFile.getName();
            log.info("Audio file uploaded to GCS at URL: {}", audioUrl);

            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setUri(audioUrl)
                    .build();

            OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> response =
                    speechClient.longRunningRecognizeAsync(recognitionConfig, audio);

            LongRunningRecognizeResponse reply = response.get();
            if (response.isDone()) {
                StringBuilder transcription = new StringBuilder();
                List<SpeechRecognitionResult> results = reply.getResultsList();
                for (SpeechRecognitionResult result : results) {
                    for (SpeechRecognitionAlternative alternative : result.getAlternativesList()) {
                        transcription.append(alternative.getTranscript()).append("\n");
                    }
                }
                if (!transcription.isEmpty()) {
                    log.info(transcription.toString().trim());
                    return transcription.toString().trim();
                } else {
                    log.info("No transcription result found");
                    return "";
                }
            } else {
                log.info("Transcription not completed yet");
                return "Processing...";
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
