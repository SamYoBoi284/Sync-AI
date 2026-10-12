package com.sam.syncai;

public final class GenerationConfig {
    // Keep ordinary replies bounded; artifact requests can raise this to 1024.
    public int maxTokens = 768;
    public float temperature = 0.7f;
    public float topP = 0.9f;
}
