package com.sam.syncai;

import java.util.Map;

public interface SyncTool {
    String getName();
    String getDescription();
    String getInputSchema();
    String execute(Map<String, String> arguments) throws Exception;
}
