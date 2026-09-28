package com.sam.syncai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolCall {
    public final String name;
    public final Map<String, String> arguments;

    public ToolCall(String name, Map<String, String> arguments) {
        this.name = name;
        this.arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
