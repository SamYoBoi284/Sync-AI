package com.sam.syncai;

import java.util.Locale;
import java.util.UUID;

public final class RequestDiagnostics {
    public final String requestId = UUID.randomUUID().toString().substring(0, 8);
    public final String route;
    public final long startedAtMs = System.currentTimeMillis();

    private final long startNanos = System.nanoTime();
    private long toolNanos;
    private long llmNanos;
    private long routingNanos;
    private String nativeInfo = "";
    private String error = "";
    private String errorStage = "";
    private String stage = "routing";
    private String model = "none";
    private boolean finished;

    public RequestDiagnostics(String route) {
        this.route = route;
    }

    public void addRouting(long nanos) { routingNanos += Math.max(0L, nanos); }
    public void addTool(long nanos) { toolNanos += Math.max(0L, nanos); }
    public void addLlm(long nanos) { llmNanos += Math.max(0L, nanos); }
    public void setNativeInfo(String value) { nativeInfo = value == null ? "" : value; }
    public void setStage(String value) { stage = value == null || value.trim().isEmpty() ? "unknown" : value; }
    public void setModel(String value) { model = value == null || value.trim().isEmpty() ? "none" : value; }
    public void setError(String value) {
        error = value == null ? "" : value;
        if (errorStage.isEmpty()) errorStage = stage;
    }
    public void setError(String stageValue, String value) {
        errorStage = stageValue == null ? "" : stageValue;
        error = value == null ? "" : value;
    }

    public void finish() { finished = true; }

    public String summary() {
        double totalMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        return summaryWithTotal(totalMs);
    }

    private String summaryWithTotal(double totalMs) {
        double routingMs = routingNanos / 1_000_000.0;
        double toolMs = toolNanos / 1_000_000.0;
        double llmMs = llmNanos / 1_000_000.0;

        StringBuilder out = new StringBuilder();
        out.append("request=").append(requestId).append("\n");
        out.append("route=").append(route).append("\n");
        out.append("stage=").append(stage).append("\n");
        out.append("model=").append(model).append("\n");
        out.append(String.format(Locale.US, "total=%.1f ms\n", totalMs));
        out.append(String.format(Locale.US, "routing=%.1f ms\n", routingMs));
        out.append(String.format(Locale.US, "tools=%.1f ms\n", toolMs));
        out.append(String.format(Locale.US, "llm=%.1f ms\n", llmMs));
        if (!nativeInfo.isEmpty()) out.append(nativeInfo).append("\n");
        if (!error.isEmpty()) {
            if (!errorStage.isEmpty()) out.append("error_stage=").append(errorStage).append("\n");
            out.append("error=").append(error).append("\n");
        }
        return out.toString().trim();
    }

    public String finalSummary() {
        finished = true;
        return summary();
    }
}
