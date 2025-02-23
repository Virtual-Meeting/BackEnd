package org.improvejava.kurento_chat;

import org.kurento.client.KurentoClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KurentoConfig {

    @Bean
    public KurentoClient kurentoClient() {
        return KurentoClient.create();
    }
}