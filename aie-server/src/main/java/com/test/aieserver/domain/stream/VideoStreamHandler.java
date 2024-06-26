package com.test.aieserver.domain.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.aieserver.domain.answer.service.AnswerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kurento.client.*;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
@Slf4j
@RequiredArgsConstructor
public class VideoStreamHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private KurentoClient kurento;
    private final Map<String, MediaPipeline> pipelines = new HashMap<>();
    private final Map<String, WebRtcEndpoint> webRtcEndpoints = new HashMap<>();
    private final Map<String, RecorderEndpoint> recorderEndpoints = new HashMap<>();
    private final AnswerService answerService;

    // 메시지 ID를 저장할 맵
    private final Map<String, Boolean> ackMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (kurento == null) {
            kurento = KurentoClient.create();
            log.info("Kurento client created");
        }
        String sessionId = session.getId();
        createNewPipelineAndEndpoint(sessionId);
        log.info("Media pipeline and WebRTC endpoint created for session ID: {}", sessionId);
    }

    private void createNewPipelineAndEndpoint(String sessionId) {
        MediaPipeline pipeline = kurento.createMediaPipeline();
        WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
        setupWebRtcEventListeners(webRtcEndpoint);
        pipelines.put(sessionId, pipeline);
        webRtcEndpoints.put(sessionId, webRtcEndpoint);
    }

    private void setupWebRtcEventListeners(WebRtcEndpoint webRtcEndpoint) {
        webRtcEndpoint.addIceCandidateFoundListener(event -> {
            IceCandidate candidate = event.getCandidate();
            log.info("New ICE candidate found: {}", candidate.getCandidate());
        });

        webRtcEndpoint.addIceGatheringDoneListener(event -> {
            log.info("ICE gathering done");
        });

        webRtcEndpoint.addMediaStateChangedListener(event -> {
            log.info("Media state changed: {}", event.getNewState());
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map msg = objectMapper.readValue(message.getPayload(), Map.class);
        String sessionId = session.getId();
        String command = (String) msg.get("command");
        String type = (String) msg.get("type");

        if ("offer".equals(type)) {
            handleOffer(session, sessionId, msg);
        } else if ("candidate".equals(type)) {
            handleCandidate(sessionId, msg);
        } else if ("start".equals(command)) {
            startRecording(sessionId);
        } else if ("stop".equals(command)) {
            stopRecording(sessionId);
            String answer = answerService.requestWithVideoFile(sessionId);
            sendMsg(session, answer);
            log.info("Stop msg response");
        } else if ("chat".equals(type)) {
            String messageText = (String) msg.get("message");
            String messageId = (String) msg.get("messageId");
            log.info("Chat message received: {}", messageText);
            String answer = answerService.requestWithText(messageText, sessionId);
            sendMsg(session, answer);

            // Send chat acknowledgment
            String ackMsg = String.format("{\"type\":\"chatAck\", \"messageId\":\"%s\"}", messageId);
            session.sendMessage(new TextMessage(ackMsg));
            log.info("Chat msg response");
        } else if ("chatAck".equals(type)) {
            String messageId = (String) msg.get("messageId");
            ackMap.put(messageId, true);
            log.info("ACK received for message ID: {}", messageId);
        }
    }

    private void handleOffer(WebSocketSession session, String sessionId, Map msg) throws IOException {
        WebRtcEndpoint webRtcEndpoint = webRtcEndpoints.get(sessionId);
        if (webRtcEndpoint == null) {
            createNewPipelineAndEndpoint(sessionId);
            webRtcEndpoint = webRtcEndpoints.get(sessionId);
        } else {
            webRtcEndpoint.release(); // Release the existing WebRtcEndpoint
            webRtcEndpoint = new WebRtcEndpoint.Builder(pipelines.get(sessionId)).build();
            setupWebRtcEventListeners(webRtcEndpoint);
            webRtcEndpoints.put(sessionId, webRtcEndpoint);
        }
        String sdpOffer = (String) msg.get("sdp");
        String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);
        String json = String.format("{\"type\":\"answer\", \"sdpAnswer\":\"%s\", \"sessionId\":\"%s\"}", sdpAnswer.replace("\n", "\\n").replace("\r", "\\r"), sessionId);
        session.sendMessage(new TextMessage(json));
        webRtcEndpoint.gatherCandidates();
        log.info("Processed offer and gathered candidates for session ID: {}", sessionId);
    }

    private void handleCandidate(String sessionId, Map msg) {
        WebRtcEndpoint webRtcEndpoint = webRtcEndpoints.get(sessionId);
        if (webRtcEndpoint != null) {
            String candidate = (String) msg.get("candidate");
            int sdpMLineIndex = (Integer) msg.get("sdpMLineIndex");
            String sdpMid = (String) msg.get("sdpMid");
            IceCandidate iceCandidate = new IceCandidate(candidate, sdpMid, sdpMLineIndex);
            webRtcEndpoint.addIceCandidate(iceCandidate);
            log.info("ICE candidate added for session ID: {}", sessionId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        MediaPipeline pipeline = pipelines.remove(sessionId);
        WebRtcEndpoint webRtcEndpoint = webRtcEndpoints.remove(sessionId);
        RecorderEndpoint recorderEndpoint = recorderEndpoints.remove(sessionId);
        if (recorderEndpoint != null) {
            recorderEndpoint.stop();
            recorderEndpoint.release();
            log.info("Recorder endpoint released for session ID: {}", sessionId);
        }
        if (webRtcEndpoint != null) {
            webRtcEndpoint.release();
            log.info("WebRTC endpoint released for session ID: {}", sessionId);
        }
        if (pipeline != null) {
            pipeline.release();
            log.info("Media pipeline released for session ID: {}", sessionId);
        }
    }

    public void startRecording(String sessionId) {
        MediaPipeline pipeline = pipelines.get(sessionId);
        WebRtcEndpoint webRtcEndpoint = webRtcEndpoints.get(sessionId);

        String outputFile = "file:///var/lib/kurento/" + sessionId + ".webm"; // 저장될 파일 경로
        RecorderEndpoint recorderEndpoint = new RecorderEndpoint.Builder(pipeline, outputFile)
                .withMediaProfile(MediaProfileSpecType.WEBM).build();

        recorderEndpoint.addErrorListener(event -> log.error("Recorder error: {}", event.getDescription()));

        webRtcEndpoint.connect(recorderEndpoint, MediaType.VIDEO);
        webRtcEndpoint.connect(recorderEndpoint, MediaType.AUDIO);

        recorderEndpoints.put(sessionId, recorderEndpoint);

        recorderEndpoint.record();
        log.info("Recording started for session ID: {}", sessionId);
    }

    public void stopRecording(String sessionId) {
        RecorderEndpoint recorderEndpoint = recorderEndpoints.get(sessionId);
        if (recorderEndpoint != null) {
            recorderEndpoint.stop();
            log.info("Recording stopped for session ID: {}", sessionId);
        } else {
            log.warn("Recorder Endpoint is not initialized for session ID: {}", sessionId);
        }
    }

    public void sendMsg(WebSocketSession session, String msg) {
        try {
            String messageId = UUID.randomUUID().toString();
            ackMap.put(messageId, false);
            String json = String.format("{\"type\":\"chat\", \"message\":\"%s\", \"messageId\":\"%s\"}", msg.replace("\n", "\\n").replace("\r", "\\r"), messageId);
            log.info(json);
            session.sendMessage(new TextMessage(json));

            // 일정 시간 후 ACK를 확인하는 작업을 예약
            scheduleAckCheck(session, messageId, msg, 5000); // 5초 후 ACK를 확인
        } catch (Exception e) {
            log.error("메시지 전송 오류: {}", e.getMessage());
        }
    }

    private void scheduleAckCheck(WebSocketSession session, String messageId, String msg, long delay) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            // ACK를 받지 못한 경우 메시지를 다시 전송
            if (!ackReceived(messageId)) {
                log.info("메시지 재전송: {}", messageId);
                sendMsg(session, msg);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private boolean ackReceived(String messageId) {
        return ackMap.getOrDefault(messageId, false);
    }
}
