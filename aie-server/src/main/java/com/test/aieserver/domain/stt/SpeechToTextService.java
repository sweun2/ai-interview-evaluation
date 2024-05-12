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
import org.springframework.web.multipart.MultipartFile;

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
                    .setModel("latest_short")
                    .build();
            Storage storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService();
            BlobInfo blobInfo = BlobInfo.newBuilder("aie-bucket", audioFile.getName()).build();

            Blob blob = storage.create(blobInfo, Files.readAllBytes(audioFile.toPath()));
            String audioUrl = "gs://aie-bucket/" + audioFile.getName();  // GCS URL 형식
            log.info("Audio file uploaded to GCS at URL: {}", audioUrl);

            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.WEBM_OPUS)
                    .setSampleRateHertz(48000)
                    .setLanguageCode("ko-KR")
                    .setModel("latest_short")
                    .build();

            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setUri(audioUrl)
                    .build();

            OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> response =
                    speechClient.longRunningRecognizeAsync(config, audio);

            LongRunningRecognizeResponse reply = response.get(); // 여기를 수정
            if (response.isDone()) {
                List<SpeechRecognitionResult> results = reply.getResultsList();
                if (!results.isEmpty()) {
                    SpeechRecognitionResult result = results.get(0);
                    return result.getAlternatives(0).getTranscript();
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
