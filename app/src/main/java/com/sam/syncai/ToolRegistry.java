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
        out.append("You are Sync//AI, a local Android assistant. ");
        out.append("You can answer normally and you can use the device tools listed below. ");
        out.append("When a user request requires a tool, output exactly one tool call and nothing else using this format: ");
        out.append("<tool_call>{\"name\":\"TOOL_NAME\",\"arguments\":{...}}</tool_call>. ");
        out.append("Use one tool call at a time. Do not claim an action succeeded until a tool result is provided. ");
        out.append("For normal conversation, do not output tool_call tags. ");
        out.append("For app launching, use open_app with the app name the user means; tolerate aliases like FB, YT, Insta, and natural phrases like \"open the Discord app\". ");
        out.append("Use open_app for Bluetooth and Wi-Fi settings. Use open_app for ALL file-related requests such as Downloads, files, folders, storage, APKs, archives, and documents; these must open ZArchiver, never the system Files app. ");
        out.append("If open_app cannot find the exact requested app, it will return a candidate that requires user confirmation; never pretend the candidate was opened until the confirmation result is provided. ");
        out.append("You can read and write only inside Sync//AI's private workspace using read_workspace_file and write_workspace_file. ");
        out.append("Use open_canvas when the user asks for a drawing canvas or wants to sketch.\n\nTOOLS:\n");
        for (SyncTool tool : tools.values()) {
            out.append("- ").append(tool.getName()).append(": ")
                    .append(tool.getDescription()).append(" Arguments: ")
                    .append(tool.getInputSchema()).append("\n");
        }
        return out.toString();
    }
}
