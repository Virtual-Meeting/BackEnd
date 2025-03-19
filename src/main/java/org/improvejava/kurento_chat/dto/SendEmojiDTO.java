package org.improvejava.kurento_chat.dto;

import lombok.Getter;

@Getter
public class SendEmojiDTO {
    private final String senderId;

    private final String receiverId;

    private final String emoji;

    public SendEmojiDTO(String senderId, String receiverId, String emoji) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.emoji = emoji;
    }
}
