package org.improvejava.kurento_chat;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

@Component
public class RoomIdGenerator {
    private static final Set<String> generatedIds = new HashSet<>();
    private static final Random random = new Random();

    public static synchronized String generateRoomId() {
        String roomId;
        do {
            roomId = String.format("%06d", random.nextInt(1_000_000)); // 000000 ~ 999999
        } while (generatedIds.contains(roomId)); // 중복 체크

        generatedIds.add(roomId);
        return roomId;
    }
}

