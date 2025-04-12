package org.improvejava.kurento_chat.dto;

import lombok.Getter;

import java.util.UUID;

@Getter
public class JoinRoomDTO {
    private final String userId;

    private final String userName;

    private final String roomId;

    private final Boolean isAudioOn;

    private final Boolean isVideoOn;

    public JoinRoomDTO(String userName, String roomId, Boolean isAudioOn, Boolean isVideoOn) {
        this.userId = UUID.randomUUID().toString();
        this.userName = userName;
        this.roomId = roomId;
        this.isAudioOn = isAudioOn;
        this.isVideoOn = isVideoOn;
    }
}
