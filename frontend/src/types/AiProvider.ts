// src/types/AiProvider.ts
export type AiProvider = "openai" | "claude" | "gemini";

export const AI_PROVIDER_LABEL: Record<AiProvider, string> = {
    openai: "GPT",
    claude: "Claude",
    gemini: "Gemini",
};
