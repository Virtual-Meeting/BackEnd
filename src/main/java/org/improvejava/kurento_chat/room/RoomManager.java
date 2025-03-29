package org.improvejava.kurento_chat.room;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.improvejava.kurento_chat.user.Participant;
import org.improvejava.kurento_chat.user.UserSession;
import org.improvejava.kurento_chat.utils.RoomIdGenerator;
import org.kurento.client.KurentoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

  public UserSession joinRoom(String userName, String userId, String roomId, WebSocketSession session) throws IOException {
    Room room = getRoom(roomId);

    log.info("ROOM {}: adding participant {} / {}", roomId, userName, userId);

    final UserSession participant = new UserSession(userName, roomId, userId, session, room.getPipeline());
    announceNewParticipantEnter(participant);
    room.addParticipant(participant);
    noticeParticipantsList(participant);
    return participant;
  }

  public void removeRoom(Room room) {
    try {
      this.roomsByRoomId.remove(room.getRoomId());
    } catch (NullPointerException e) {
      log.warn("Room {} not found", room.getRoomId());
    }
    log.info("Room {} removed and closed", room.getRoomId());
  }

  public UserSession createRoom(String userName, String userId, WebSocketSession session, Participant roomCreator) throws IOException {
    String roomId = RoomIdGenerator.generateRoomId();
    log.debug("Room {} is created", roomId);

    Room room = new Room(roomId, kurento.createMediaPipeline());
    roomsByRoomId.put(roomId, room);

    log.info("Participant {} / {} created room {}", userName, userId, room.getRoomId());
    final UserSession participant = new UserSession(userName, room.getRoomId(), userId, session, room.getPipeline());
    announceNewParticipantEnter(participant);
    room.addParticipant(participant);
    room.changeRoomCreator(roomCreator);

    JsonObject createRoomMsg = new JsonObject();
    createRoomMsg.addProperty("action", "roomCreated");
    createRoomMsg.addProperty("userId", participant.getUserId());
    createRoomMsg.addProperty("userName", participant.getUserName());
    createRoomMsg.addProperty("roomId", room.getRoomId());
    createRoomMsg.addProperty("creator", roomCreator.toString());

    participant.sendChat(createRoomMsg);
    return participant;
  }

  public void leave(UserSession userSession) throws IOException {
    Room room = getRoom(userSession.getRoomId());
    if (room != null) {
      log.debug("PARTICIPANT {} / {}: Leaving room {}", userSession.getUserName(), userSession.getUserId(), userSession.getRoomId());
      this.removeParticipant(userSession);
      userSession.close();
    }

    if (room.getParticipants().isEmpty()) {
      removeRoom(room);
    }
  }

  // 기존의 사용자에게 새 사용자의 방 합류 알림
  private Collection<String> announceNewParticipantEnter(UserSession newParticipant) throws IOException {
    final JsonObject newParticipantMsg = new JsonObject();
    newParticipantMsg.addProperty("action", "sendExistingUsers");
    newParticipantMsg.addProperty("userId", newParticipant.getUserId());
    newParticipantMsg.addProperty("userName", newParticipant.getUserName());

    Room room = getRoom(newParticipant.getRoomId());

    final List<String> participantsList = new ArrayList<>(room.getParticipants().size());
    log.debug("ROOM {}: notifying other participants of new participant {} / {}", newParticipant.getRoomId(),
            newParticipant.getUserName(), newParticipant.getUserId());

    for (final UserSession participant : room.getParticipants()) {
      try {
        participant.sendChat(newParticipantMsg);
      } catch (final IOException e) {
        log.debug("ROOM {}: participant {} / {} could not be notified", newParticipant.getRoomId(), participant.getUserName(), participant.getUserId());
      }
      participantsList.add(participant.getUserId());
    }

    return participantsList;
  }

  // 방에 새로 참가하고자 하는 사용자에게 기존의 참가자 리스트 전송
  public void noticeParticipantsList(UserSession user) throws IOException {
    final JsonArray participantsArray = new JsonArray();

    Room room = getRoom(user.getRoomId());

    for (final UserSession participant : room.getParticipants()) {
      if (!participant.equals(user)) {
        final Participant participantDTO = new Participant();
        participantDTO.setUserId(participant.getUserId());
        participantDTO.setUserName(participant.getUserName());

        final JsonElement jsonParticipant = new JsonPrimitive(participantDTO.toString());

        participantsArray.add(jsonParticipant);
      }
    }

    final JsonObject existingParticipantsMsg = new JsonObject();
    existingParticipantsMsg.addProperty("action", "newUserJoined");
    existingParticipantsMsg.addProperty("userId", user.getUserId());
    existingParticipantsMsg.addProperty("userName", user.getUserName());
    existingParticipantsMsg.add("participants", participantsArray);
    existingParticipantsMsg.addProperty("creator", room.getRoomCreator().toString());

    log.debug("PARTICIPANT {} / {} : sending a list of {} participants", user.getUserName(), user.getUserId(),
            participantsArray.size());

    user.sendChat(existingParticipantsMsg);
  }

  private void removeParticipant(UserSession userSession) throws IOException {
    System.out.println("romove Participant UserId : " + userSession.getUserId());

    Room room = getRoom(userSession.getRoomId());
    room.removeParticipant(userSession.getUserId());

    log.debug("ROOM {}: notifying all users that {} / {} is leaving the room", room.getRoomId(), userSession.getUserName(), userSession.getUserId());

    final List<String> unnotifiedParticipants = new ArrayList<>();
    final JsonObject participantLeftJson = new JsonObject();
    participantLeftJson.addProperty("action", "exitRoom");
    participantLeftJson.addProperty("userId", userSession.getUserId());
    participantLeftJson.addProperty("userName", userSession.getUserName());

    for (final UserSession participant : room.getParticipants()) {
      try {
        participant.cancelVideoFrom(userSession.getUserId());
        participant.sendChat(participantLeftJson);
      } catch (final IOException e) {
        unnotifiedParticipants.add(participant.getUserId());
      }
    }

    if (!unnotifiedParticipants.isEmpty()) {
      log.debug("ROOM {}: The users {} could not be notified that {} / {} left the room", room.getRoomId(),
              unnotifiedParticipants, userSession.getUserName(), userSession.getUserId());
    }
    unnotifiedParticipants.clear();

    if (userSession.getUserId().equals(room.getRoomCreator().getUserId()) && !room.getParticipants().isEmpty()) {
      UserSession newRoomLeader = room.getRandomParticipant();
      Participant roomLeader = new Participant(newRoomLeader.getUserId(), newRoomLeader.getUserName());
      room.changeRoomCreator(roomLeader);

      JsonObject roomLeaderChangeMessage = new JsonObject();
      roomLeaderChangeMessage.addProperty("action", "creatorChanged");
      roomLeaderChangeMessage.addProperty("creator", room.getRoomCreator().toString());

      for (final UserSession participant : room.getParticipants()) {
        try {
          participant.sendChat(roomLeaderChangeMessage);
        } catch (final IOException e) {
          unnotifiedParticipants.add(participant.getUserId());
        }
      }

      if (!unnotifiedParticipants.isEmpty()) {
        log.debug("ROOM {}: The users {} could not be notified that {} / {} left the room", room.getRoomId(),
                unnotifiedParticipants, userSession.getUserName(), userSession.getUserId());
      }
    }
  }
}
