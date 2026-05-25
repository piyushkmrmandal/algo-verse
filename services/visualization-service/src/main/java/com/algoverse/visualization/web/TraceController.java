package com.algoverse.visualization.web;

import com.algoverse.visualization.domain.TraceRequest;
import com.algoverse.visualization.domain.TraceResponse;
import com.algoverse.visualization.service.TracerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/visualize")
@RequiredArgsConstructor
@Tag(name = "Visualization", description = "Step-by-step execution tracing for Python and JavaScript")
public class TraceController {

    private final TracerService tracerService;

    @PostMapping("/trace")
    @Operation(summary = "Trace code execution", description = "Returns step-by-step execution trace with local variable snapshots")
    public ResponseEntity<TraceResponse> trace(@Valid @RequestBody TraceRequest request) {
        TraceResponse response = tracerService.trace(request);
        return ResponseEntity.ok(response);
    }
}
