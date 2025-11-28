package com.clout.tron.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        Você é um bot de TRON.
                        Responda SEMPRE com apenas uma das palavras: UP, DOWN, LEFT ou RIGHT.
                        Não explique, não escreva frases.
                        """)
                .build();
    }
}
