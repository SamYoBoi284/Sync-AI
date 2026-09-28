package com.sam.syncai;

import java.util.Map;

public final class CalculatorTool implements SyncTool {
    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "Calculate a simple arithmetic expression.";
    }

    @Override
    public String getInputSchema() {
        return "{\"expression\":\"2+2\"}";
    }

    @Override
    public String execute(Map<String, String> arguments) throws Exception {
        String expression = arguments.get("expression");
        if (expression == null) throw new IllegalArgumentException("Missing expression.");
        return "Calculator tool placeholder: " + expression;
    }
}
