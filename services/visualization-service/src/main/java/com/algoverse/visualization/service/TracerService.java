package com.algoverse.visualization.service;

import com.algoverse.visualization.domain.TraceRequest;
import com.algoverse.visualization.domain.TraceResponse;
import com.algoverse.visualization.domain.TraceStep;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TracerService {

    @Value("${visualization.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${visualization.max-steps:500}")
    private int maxSteps;

    @Value("${visualization.python-executable:python3}")
    private String pythonExecutable;

    @Value("${visualization.node-executable:node}")
    private String nodeExecutable;

    private Path pythonTracerPath;
    private Path jsTracerPath;

    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    void extractTracers() throws IOException {
        pythonTracerPath = extractResource("tracers/python_tracer.py", "python_tracer", ".py");
        jsTracerPath     = extractResource("tracers/js_tracer.js",     "js_tracer",     ".js");
        log.info("Tracers ready: python={} js={}", pythonTracerPath, jsTracerPath);
    }

    private Path extractResource(String resource, String prefix, String suffix) throws IOException {
        Path tmp = Files.createTempFile(prefix + "_", suffix);
        tmp.toFile().deleteOnExit();
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        tmp.toFile().setExecutable(false);
        tmp.toFile().setWritable(false);
        return tmp;
    }

    @Cacheable(value = "traces", key = "#req.language() + ':' + #req.code().hashCode() + ':' + #req.input().hashCode()")
    public TraceResponse trace(TraceRequest req) {
        return switch (req.language()) {
            case "python"     -> runTracer(pythonExecutable, pythonTracerPath, req);
            case "javascript" -> runTracer(nodeExecutable,   jsTracerPath,     req);
            default -> error(req.language(), "Unsupported language: " + req.language());
        };
    }

    private TraceResponse runTracer(String executable, Path tracerPath, TraceRequest req) {
        String payload = req.code() + "\n---INPUT---\n" + req.input();

        ProcessBuilder pb = new ProcessBuilder(
                executable, tracerPath.toString(), String.valueOf(maxSteps)
        );
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();

            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            boolean finished = process.waitFor(timeoutSeconds + 2L, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return error(req.language(), "Tracer process timed out");
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            if (stdout.isBlank()) {
                log.warn("Tracer produced no output. stderr={}", stderr);
                return error(req.language(), "Tracer produced no output: " + stderr);
            }

            return parseOutput(stdout);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Tracer execution failed", e);
            return error(req.language(), "Tracer execution failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private TraceResponse parseOutput(String json) throws IOException {
        Map<String, Object> raw = mapper.readValue(json, new TypeReference<>() {});
        String language    = (String) raw.get("language");
        String finalOutput = (String) raw.getOrDefault("finalOutput", "");
        String errorMsg    = (String) raw.get("error");
        boolean truncated  = Boolean.TRUE.equals(raw.get("truncated"));

        List<TraceStep> steps = mapper.convertValue(
                raw.get("steps"),
                new TypeReference<>() {}
        );
        return new TraceResponse(language, steps, finalOutput, errorMsg, truncated);
    }

    private TraceResponse error(String language, String message) {
        TraceStep errStep = new TraceStep(1, "exception", 0, "<global>", Map.of(), "", null, message);
        return new TraceResponse(language, List.of(errStep), "", message, false);
    }
}
