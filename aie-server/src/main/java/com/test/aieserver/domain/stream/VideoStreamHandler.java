package com.test.aieserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.kurento.client.*;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class VideoStreamHandler extends TextWebSocketHandler {
    private ObjectMapper objectMapper = new ObjectMapper();
    private KurentoClient kurento;
    private Map<String, MediaPipeline> pipelines = new ConcurrentHashMap<>();
    private Map<String, WebRtcEndpoint> webRtcEndpoints = new ConcurrentHashMap<>();
    private Map<String, RecorderEndpoint> recorderEndpoints = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (kurento == null) {
            kurento = KurentoClient.create();
            log.info("Kurento client created");
        }
        MediaPipeline pipeline = kurento.createMediaPipeline();
        WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();


        setupWebRtcEventListeners(webRtcEndpoint);
        String sessionId = session.getId();
        pipelines.put(sessionId, pipeline);
        webRtcEndpoints.put(sessionId, webRtcEndpoint);

        session.getAttributes().put("webRtcEndpoint", webRtcEndpoint);
        log.info("Media pipeline and WebRTC endpoint created for session ID: {}", sessionId);
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
        webRtcEndpoint.getStats().forEach(
                (s, stats) -> {
                    log.info(s);
                    log.info(stats.toString());
                }
        );

    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map msg = objectMapper.readValue(message.getPayload(), Map.class);
        String sessionId = session.getId();
        WebRtcEndpoint webRtcEndpoint = webRtcEndpoints.get(sessionId);
        RecorderEndpoint recorderEndpoint = recorderEndpoints.get(sessionId);
        String command = (String) msg.get("command");
        String type = (String) msg.get("type");
        if ("offer".equals(type)) {
            String sdpOffer = (String) msg.get("sdp");
            String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);
            String json = String.format("{\"type\":\"answer\", \"sdpAnswer\":\"%s\"}", sdpAnswer.replace("\n", "\\n").replace("\r", "\\r"));
            session.sendMessage(new TextMessage(json));
            webRtcEndpoint.gatherCandidates();
            log.info("Processed offer and gathered candidates for session ID: {}", sessionId);
        } else if ("candidate".equals(type)) {
            String candidate = (String) msg.get("candidate");
            int sdpMLineIndex = (Integer) msg.get("sdpMLineIndex");
            String sdpMid = (String) msg.get("sdpMid");
            IceCandidate iceCandidate = new IceCandidate(candidate, sdpMid, sdpMLineIndex);
            webRtcEndpoint.addIceCandidate(iceCandidate);
            log.info("ICE candidate added for session ID: {}", sessionId);
        } else if ("start".equals(command)) {
            startRecording(sessionId);
        } else if ("stop".equals(command)) {
            stopRecording(sessionId);
        } else if ("chat".equals(command))  {

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

        recorderEndpoint.addErrorListener(event -> {
            log.error("Recorder error: {}", event.getDescription());
        });


        webRtcEndpoint.connect(recorderEndpoint,MediaType.VIDEO);
        webRtcEndpoint.connect(recorderEndpoint,MediaType.AUDIO);

        recorderEndpoints.put(sessionId, recorderEndpoint);

        if (recorderEndpoint != null) {
            recorderEndpoint.record();
            log.info("Recording started for session ID: {}", sessionId);
        } else {
            log.warn("WebRTC or Recorder Endpoint is not initialized for session ID: {}", sessionId);
        }
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
    public void sendMsg(String msg) {

    }
}
