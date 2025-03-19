package org.improvejava.kurento_chat.dto;

import lombok.Getter;

import java.util.UUID;

@Getter
public class JoinRoomDTO {
    private final String userId;

    private final String userName;

    private final String roomId;

    public JoinRoomDTO(String userName, String roomId) {
        this.userId = UUID.randomUUID().toString();
        this.userName = userName;
        this.roomId = roomId;
    }
}
