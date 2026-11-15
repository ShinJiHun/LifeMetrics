package com.lifemetrics.backend.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GeminiEmbeddingClientTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void 응답의_values_를_읽는다() throws Exception {
        assertThat(GeminiEmbeddingClient.values(OM.readTree("{\"embedding\":{\"values\":[0.1,0.2]}}"))).containsExactly(0.1f, 0.2f);
        assertThat(GeminiEmbeddingClient.values(OM.readTree("{\"embeddings\":[{\"values\":[1,2,3]}]}"))).containsExactly(1f, 2f, 3f);
        assertThatThrownBy(() -> GeminiEmbeddingClient.values(OM.readTree("{}"))).hasMessageContaining("values");
    }

    @Test
    void 차원을_줄인_벡터는_길이_1로_정규화한다() {
        float[] n = GeminiEmbeddingClient.normalize(new float[]{3, 4});
        assertThat(n[0]).isCloseTo(0.6f, within(1e-6f));
        assertThat(n[1]).isCloseTo(0.8f, within(1e-6f));
        assertThat(GeminiEmbeddingClient.normalize(new float[]{0, 0})).containsExactly(0f, 0f);
    }

    @Test
    void 키가_없으면_설정되지_않은_상태다() {
        var client = new GeminiEmbeddingClient("", "gemini-embedding-001", 768, OM);
        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.embed("x", GeminiEmbeddingClient.Task.RETRIEVAL_QUERY)).hasMessageContaining("GEMINI_API_KEY");
    }
}
