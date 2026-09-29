package com.sam.syncai;

import org.json.JSONObject;

import java.util.UUID;

public final class DiagnosticRecord {
    public final String requestId;
    public final long startedAt;
    public final long totalMs;
    public final String route;
    public final String tool;
    public final long toolMs;
    public final String model;
    public final String nativeDetails;
    public final String errorStage;
    public final String errorMessage;

    public DiagnosticRecord(
            long startedAt,
            long totalMs,
            String route,
            String tool,
            long toolMs,
            String model,
            String nativeDetails,
            String errorStage,
            String errorMessage
    ) {
        this.requestId = UUID.randomUUID().toString().substring(0, 8);
        this.startedAt = startedAt;
        this.totalMs = totalMs;
        this.route = route == null ? "unknown" : route;
        this.tool = tool == null ? "" : tool;
        this.toolMs = toolMs;
        this.model = model == null ? "" : model;
        this.nativeDetails = nativeDetails == null ? "" : nativeDetails;
        this.errorStage = errorStage == null ? "" : errorStage;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public String format() {
        StringBuilder b = new StringBuilder();
        b.append("REQUEST ").append(requestId).append("\n");
        b.append("Route: ").append(route).append("\n");
        b.append("Total: ").append(totalMs).append(" ms\n");
        if (!tool.isEmpty()) {
            b.append("Tool: ").append(tool).append(" (").append(toolMs).append(" ms)\n");
        }
        if (!model.isEmpty()) b.append("Model: ").append(model).append("\n");
        if (!nativeDetails.isEmpty()) b.append("\n").append(nativeDetails);
        if (!errorStage.isEmpty()) {
            b.append("\n\nERROR\nStage: ").append(errorStage);
            if (!errorMessage.isEmpty()) b.append("\nMessage: ").append(errorMessage);
        }
        return b.toString();
    }

    public String toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("requestId", requestId);
            o.put("startedAt", startedAt);
            o.put("totalMs", totalMs);
            o.put("route", route);
            o.put("tool", tool);
            o.put("toolMs", toolMs);
            o.put("model", model);
            o.put("nativeDetails", nativeDetails);
            o.put("errorStage", errorStage);
            o.put("errorMessage", errorMessage);
        } catch (Exception ignored) {
        }
        return o.toString();
    }
}
