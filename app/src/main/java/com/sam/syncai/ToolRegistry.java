package com.sam.syncai;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolRegistry {
    private final Map<String, SyncTool> tools = new LinkedHashMap<>();

    public ToolRegistry(Context context) {
        register(new CalculatorTool());
        WorkspaceManager workspace = new WorkspaceManager(context);

        register(new FlashlightTool(context));
        register(new AlarmTool(context));
        register(new TimerTool(context));
        register(new OpenSettingsTool(context));
        register(new OpenAppTool(context));
        register(new WorkspaceReadTool(workspace));
        register(new WorkspaceWriteTool(workspace));
        register(new CanvasReadTool(context));
        register(new CanvasWriteTool(context));
        register(new CanvasReplaceTool(context));
        register(new OpenCanvasTool(context));
    }

    public void register(SyncTool tool) {
        if (tool == null || tool.getName() == null || tool.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tool must have a name.");
        }
        tools.put(tool.getName(), tool);
    }

    public SyncTool get(String name) {
        return name == null ? null : tools.get(name);
    }

    public List<SyncTool> all() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    public String systemPrompt() {
        StringBuilder out = new StringBuilder();
        out.append("You are Sync AI, a local Android assistant. ");
        out.append("You can answer normally and you can use the device tools listed below. ");
        out.append("Keep ordinary conversational answers concise unless the user asks for detail. ");
        out.append("When a user request requires a tool, output exactly one tool call and nothing else using this format: ");
        out.append("<tool_call>{\"name\":\"TOOL_NAME\",\"arguments\":{...}}</tool_call>. ");
        out.append("Use one tool call at a time. Do not claim an action succeeded until a tool result is provided. ");
        out.append("For normal conversation, do not output tool_call tags. ");
        out.append("FLASHLIGHT RULE: for requests to turn the flashlight or torch on/off, use the exact tool name flashlight with {\"enabled\":true} or {\"enabled\":false}. Never use open_app for the flashlight. ");
        out.append("For app launching, use open_app with the app name the user means; tolerate aliases like FB, YT, Insta, and natural phrases like \"open the Discord app\". ");
        out.append("Use open_app for Bluetooth and Wi-Fi settings. Use open_app for ALL file-related requests such as Downloads, files, folders, storage, APKs, archives, and documents; these must open ZArchiver, never the system Files app. ");
        out.append("If open_app cannot find the exact requested app, it will return a candidate that requires user confirmation; never pretend the candidate was opened until the confirmation result is provided. ");
        out.append("You can read and write only inside Sync AI's private workspace using read_workspace_file and write_workspace_file. ");
        out.append("Canvas is an AI-owned working surface, not a second chat box. For substantial planning, drafting, outlining, organizing, or revision work, prefer using read_canvas/write_canvas/replace_canvas_text so the work lives on Canvas instead of cluttering the chat. The user should not need to type the work into Canvas manually. Use open_canvas after writing when it is useful for the user to inspect the workspace. Canvas is a document workspace, not a drawing surface.\n\nTOOLS:\n");
        for (SyncTool tool : tools.values()) {
            String description = tool.getDescription();
            if (description.length() > 180) description = description.substring(0, 180);
            out.append("- ").append(tool.getName()).append(": ")
                    .append(description).append(" args=")
                    .append(tool.getInputSchema()).append("\n");
        }
        return out.toString();
    }
}
