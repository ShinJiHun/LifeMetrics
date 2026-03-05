package com.lifemetrics.backend.client;

/**
 * AI 제공자 타입
 */
public enum AiProvider {
    OPENAI("OpenAI GPT"),
    ANTHROPIC("Anthropic Claude"),
    GEMINI("Google Gemini");
    
    private final String displayName;
    
    AiProvider(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}