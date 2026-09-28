package com.sam.syncai;

import java.util.Map;

public final class WorkspaceReadTool implements SyncTool {
    private final WorkspaceManager workspace;

    public WorkspaceReadTool(WorkspaceManager workspace) {
        this.workspace = workspace;
    }

    @Override public String getName() { return "read_workspace_file"; }

    @Override public String getDescription() {
        return "Read a UTF-8 text file from Sync//AI's private workspace.";
    }

    @Override public String getInputSchema() {
        return "{\"name\":\"notes.txt\"}";
    }

    @Override public String execute(Map<String, String> args) throws Exception {
        String name = args.get("name");
        String content = workspace.read(name);
        if (content.length() > 12000) content = content.substring(0, 12000) + "\n[truncated]";
        return "WORKSPACE_FILE " + name + ":\n" + content;
    }
}
