package com.codepilot.sse;

import cn.hutool.json.JSONUtil;
import com.codepilot.review.AnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseEmitterService {

    private final Map<String, Sinks.Many<String>> emitters = new ConcurrentHashMap<>();

    public void createEmitter(String analysisId) {
        Sinks.Many<String> sink = Sinks.many().multicast().directBestEffort();
        emitters.put(analysisId, sink);
        log.debug("Created SSE emitter for analysis: {}", analysisId);
    }

    public Sinks.Many<String> getEmitter(String analysisId) {
        return emitters.get(analysisId);
    }

    public void emit(String analysisId, String status, String message) {
        Sinks.Many<String> sink = emitters.get(analysisId);
        if (sink != null) {
            var event = Map.of(
                    "type", "status",
                    "status", status,
                    "message", message,
                    "timestamp", System.currentTimeMillis()
            );
            sink.tryEmitNext("data:" + JSONUtil.toJsonStr(event) + "\n\n");
        }
    }

    public void emitToken(String analysisId, String token) {
        Sinks.Many<String> sink = emitters.get(analysisId);
        if (sink != null) {
            var event = Map.of(
                    "type", "token",
                    "content", token
            );
            sink.tryEmitNext("data:" + JSONUtil.toJsonStr(event) + "\n\n");
        }
    }

    public void complete(String analysisId, AnalysisResult result) {
        Sinks.Many<String> sink = emitters.get(analysisId);
        if (sink != null) {
            var event = Map.of(
                    "type", "complete",
                    "data", (Object) result
            );
            sink.tryEmitNext("data:" + JSONUtil.toJsonStr(event) + "\n\n");
            sink.tryEmitComplete();
        }
    }

    public void error(String analysisId, String error) {
        Sinks.Many<String> sink = emitters.get(analysisId);
        if (sink != null) {
            var event = Map.of(
                    "type", "error",
                    "message", error
            );
            sink.tryEmitNext("data:" + JSONUtil.toJsonStr(event) + "\n\n");
            sink.tryEmitComplete();
        }
    }

    public void removeEmitter(String analysisId) {
        Sinks.Many<String> sink = emitters.remove(analysisId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    public boolean hasEmitter(String analysisId) {
        return emitters.containsKey(analysisId);
    }
}
