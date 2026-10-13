package com.sam.syncai;

import java.util.Map;

public final class WorkspaceWriteTool implements SyncTool {
    private final WorkspaceManager workspace;

    public WorkspaceWriteTool(WorkspaceManager workspace) {
        this.workspace = workspace;
    }

    @Override public String getName() { return "write_workspace_file"; }

    @Override public String getDescription() {
        return "Create or replace a UTF-8 text file inside Sync//AI's private workspace.";
    }

    @Override public String getInputSchema() {
        return "{\"name\":\"notes.txt\",\"content\":\"text\"}";
    }

    @Override public String execute(Map<String, String> args) throws Exception {
        String name = args.get("name");
        String content = args.get("content");
        workspace.write(name, content);
        return "SUCCESS: Wrote " + name + " to Sync//AI's private workspace.";
    }
}
