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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional
public class SpeechToTextService {
    @Value("${google-credential-json}")
    private String CREDENTIALS_PATH;

    private static final String GCS_BUCKET_NAME = "aie-bucket";

    public String transcribe(File audioFile) throws IOException {
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

            RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.WEBM_OPUS)
                    .setSampleRateHertz(48000)
                    .setLanguageCode("ko-KR")
                    .setModel("telephony")
                    .addSpeechContexts(SpeechContext.newBuilder().addPhrases("특수 용어")) // 사용자 지정 사전
                    .build();

            Storage storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService();
            BlobInfo blobInfo = BlobInfo.newBuilder(GCS_BUCKET_NAME, audioFile.getName()).build();
            Blob blob = storage.create(blobInfo, Files.readAllBytes(audioFile.toPath()));
            String audioUrl = "gs://"+ GCS_BUCKET_NAME + "/" + audioFile.getName();
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
