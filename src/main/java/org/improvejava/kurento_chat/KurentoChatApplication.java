package org.improvejava.kurento_chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

@SpringBootApplication
@EnableWebSocket
public class KurentoChatApplication {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(KurentoChatApplication.class, args);
    }
}
