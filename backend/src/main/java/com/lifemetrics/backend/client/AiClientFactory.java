package com.lifemetrics.backend.client;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 클라이언트 팩토리
 */
@Component
public class AiClientFactory {
    
    private final Map<AiProvider, AiClient> clients;
    
    public AiClientFactory(List<AiClient> clientList) {
        this.clients = clientList.stream()
                .collect(Collectors.toMap(AiClient::getProvider, Function.identity()));
    }
    
    /**
     * 제공자에 맞는 AI 클라이언트 반환
     */
    public AiClient get(AiProvider provider) {
        AiClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("지원하지 않는 AI 제공자: " + provider);
        }
        return client;
    }
}