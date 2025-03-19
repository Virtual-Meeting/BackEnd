package org.improvejava.kurento_chat;

import lombok.Getter;

@Getter
public class SendEmojiDTO {
    private final String senderId;

    private final String receiverId;

    private final String emoji;

    SendEmojiDTO(String senderId, String receiverId, String emoji) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.emoji = emoji;
    }
}
