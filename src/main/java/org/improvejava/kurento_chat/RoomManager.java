package org.improvejava.kurento_chat;

import org.kurento.client.KurentoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RoomManager {

  private final Logger log = LoggerFactory.getLogger(RoomManager.class);

  @Autowired
  private KurentoClient kurento;

  private final ConcurrentMap<String, Room> roomsByRoomId = new ConcurrentHashMap<>();

  public Room getRoom(String roomId) {
    log.debug("Searching for room {}", roomId);
    Room room = roomsByRoomId.get(roomId);

    if (room == null) {
      throw new IllegalArgumentException("Room with id " + roomId + " not found");
    }

    log.debug("Room {} found", roomId);
    return room;
  }

  public Room createRoom() {
    String roomId = RoomIdGenerator.generateRoomId();
    log.debug("Room {} is created", roomId);

    Room room = new Room(roomId, kurento.createMediaPipeline());
    roomsByRoomId.put(roomId, room);
    return room;
  }

  public void removeRoom(Room room) {
    try {
      this.roomsByRoomId.remove(room.getRoomId());
    } catch (NullPointerException e) {
      log.warn("Room {} not found", room.getRoomId());
    }
    log.info("Room {} removed and closed", room.getRoomId());
  }

}
