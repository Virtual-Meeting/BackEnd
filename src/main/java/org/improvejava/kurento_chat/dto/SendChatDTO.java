package org.improvejava.kurento_chat.dto;

import lombok.Getter;

@Getter
public class SendChatDTO {
    private final String senderId;

    private final String receiverId;

    private final String message;

    public SendChatDTO(String senderId, String receiverId, String message) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.message = message;
    }
}
