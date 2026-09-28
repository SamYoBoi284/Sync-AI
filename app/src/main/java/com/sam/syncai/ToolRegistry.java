package com.sam.syncai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolRegistry {
    private final Map<String, SyncTool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        register(new CalculatorTool());
    }

    public void register(SyncTool tool) {
        tools.put(tool.getName(), tool);
    }

    public SyncTool get(String name) {
        return tools.get(name);
    }

    public List<SyncTool> all() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }
}
