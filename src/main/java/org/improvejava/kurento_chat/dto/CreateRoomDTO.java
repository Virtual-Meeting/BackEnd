package org.improvejava.kurento_chat.dto;

import lombok.Getter;
import org.improvejava.kurento_chat.utils.RoomIdGenerator;

import java.util.UUID;

@Getter
public class CreateRoomDTO {
    private final String userId;

    private final String userName;

    private final String roomId;

    CreateRoomDTO(String userName) {
        this.userId = UUID.randomUUID().toString();
        this.userName = userName;
        this.roomId = RoomIdGenerator.generateRoomId();
    }
}
