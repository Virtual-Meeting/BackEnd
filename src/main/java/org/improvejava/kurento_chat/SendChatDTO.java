package org.improvejava.kurento_chat;

import lombok.Getter;

@Getter
public class SendChatDTO {
    private final String senderId;

    private final String receiverId;

    private final String message;

    SendChatDTO(String senderId, String receiverId, String message) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.message = message;
    }
}
