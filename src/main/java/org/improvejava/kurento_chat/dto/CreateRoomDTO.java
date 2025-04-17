package org.improvejava.kurento_chat.dto;

import lombok.Getter;
import org.improvejava.kurento_chat.utils.RoomIdGenerator;

import java.util.UUID;

@Getter
public class CreateRoomDTO {
    private final String userId;

    private final String userName;

    private final String roomId;

    private final Boolean isAudioOn;

    private final Boolean isVideoOn;

    public CreateRoomDTO(String userName, Boolean isAudioOn, Boolean isVideoOn) {
        this.userId = UUID.randomUUID().toString();
        this.userName = userName;
        this.roomId = RoomIdGenerator.generateRoomId();
        this.isAudioOn = isAudioOn;
        this.isVideoOn = isVideoOn;
    }
}
