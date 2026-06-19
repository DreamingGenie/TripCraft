package com.tripcraft.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 멀티턴 대화를 위한 ChatClient 구성.
 * ChatMemory(InMemory)는 Spring AI 자동 구성 빈을 사용하며,
 * 대화 식별은 호출 시 ChatMemory.CONVERSATION_ID 파라미터로 지정한다.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient attractionChatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
