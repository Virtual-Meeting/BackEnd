package org.improvejava.kurento_chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.kurento.client.Continuation;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Room implements Closeable {
  private final Logger log = LoggerFactory.getLogger(Room.class);

  private final ConcurrentMap<String, UserSession> participants = new ConcurrentHashMap<>();
  private final MediaPipeline pipeline;
  private final String roomId;
  private Participant roomCreator;

  public Room(String roomId, MediaPipeline pipeline) {
    this.roomId = roomId;
    this.pipeline = pipeline;
    log.info("ROOM {} has been created", roomId);
  }

  public String getRoomId() {
    return roomId;
  }

  @PreDestroy
  private void shutdown() {
    this.close();
  }

  public UserSession joinRoom(String userName, String userId, WebSocketSession session) throws IOException {
    log.info("ROOM {}: adding participant {} / {}", this.roomId, userName, userId);
    final UserSession participant = new UserSession(userName, this.roomId, userId, session, this.pipeline);
    addNewUserParticipantsList(participant);
    participants.put(participant.getUserId(), participant);
    sendParticipantNames(participant);
    return participant;
  }

  public UserSession createRoom(String userName, String userId, WebSocketSession session, Participant roomCreator) throws IOException {
    log.info("Participant {} / {} created room {}", userName, userId, this.roomId);
    final UserSession participant = new UserSession(userName, this.roomId, userId, session, this.pipeline);
    addNewUserParticipantsList(participant);
    participants.put(participant.getUserId(), participant);
    this.roomCreator = roomCreator;

    JsonObject createRoomMsg = new JsonObject();
    createRoomMsg.addProperty("action", "roomCreated");
    createRoomMsg.addProperty("userId", participant.getUserId());
    createRoomMsg.addProperty("userName", participant.getUserName());
    createRoomMsg.addProperty("roomId", this.roomId);
    createRoomMsg.addProperty("creator", roomCreator.toString());

    participant.sendMessage(createRoomMsg);
    return participant;
  }

  public void leave(UserSession user) throws IOException {
    log.debug("PARTICIPANT {} / {}: Leaving room {}", user.getUserName(), user.getUserId(), this.roomId);
    this.removeParticipant(user.getUserId());
    user.close();
  }

  // 기존의 사용자에게 새 사용자의 방 합류 알림
  private Collection<String> addNewUserParticipantsList(UserSession newParticipant) throws IOException {
    final JsonObject newParticipantMsg = new JsonObject();
    newParticipantMsg.addProperty("action", "sendExistingUsers");
    newParticipantMsg.addProperty("userId", newParticipant.getUserId());
    newParticipantMsg.addProperty("userName", newParticipant.getUserName());

    final List<String> participantsList = new ArrayList<>(participants.values().size());
    log.debug("ROOM {}: notifying other participants of new participant {} / {}", roomId,
        newParticipant.getUserName(), newParticipant.getUserId());

    for (final UserSession participant : participants.values()) {
      try {
        participant.sendMessage(newParticipantMsg);
      } catch (final IOException e) {
        log.debug("ROOM {}: participant {} / {} could not be notified", roomId, participant.getUserName(), participant.getUserId(), e);
      }
      participantsList.add(participant.getUserId());
    }

    return participantsList;
  }

  // 방에 새로 참가하고자 하는 사용자에게 기존의 참가자 리스트 전송
  public void sendParticipantNames(UserSession user) throws IOException {
    final JsonArray participantsArray = new JsonArray();

    for (final UserSession participant : this.getParticipants()) {
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
    existingParticipantsMsg.addProperty("creator", this.roomCreator.toString());

    log.debug("PARTICIPANT {} / {} : sending a list of {} participants", user.getUserName(), user.getUserId(),
            participantsArray.size());

    user.sendMessage(existingParticipantsMsg);
  }

  // 방장이 나갔을 경우 방에 있는 새로운 유저에게 방장 할당 수정
  private void removeParticipant(String userId) throws IOException {
    String userName = participants.get(userId).getUserName();
    participants.remove(userId);

    log.debug("ROOM {}: notifying all users that {} / {} is leaving the room", this.roomId, userName, userId);

    final List<String> unnotifiedParticipants = new ArrayList<>();
    final JsonObject participantLeftJson = new JsonObject();
    participantLeftJson.addProperty("action", "exitRoom");
    participantLeftJson.addProperty("userId", userId);
    participantLeftJson.addProperty("userName", userName);
    for (final UserSession participant : participants.values()) {
      try {
        participant.cancelVideoFrom(userId);
        participant.sendMessage(participantLeftJson);
      } catch (final IOException e) {
        unnotifiedParticipants.add(participant.getUserId());
      }
    }

    if (!unnotifiedParticipants.isEmpty()) {
      log.debug("ROOM {}: The users {} could not be notified that {} / {} left the room", this.roomId,
          unnotifiedParticipants, userName, userId);
    }

  }

  public Collection<UserSession> getParticipants() {
    return participants.values();
  }

  public UserSession getParticipant(String userId) {
    return participants.get(userId);
  }

  @Override
  public void close() {
    for (final UserSession user : participants.values()) {
      try {
        user.close();
        final JsonObject participantLeftJson = new JsonObject();
        participantLeftJson.addProperty("action", "exitRoom");
        user.sendMessage(participantLeftJson);
      } catch (IOException e) {
        log.debug("ROOM {}: Could not invoke close on participant {} / {}", this.roomId, user.getUserName(), user.getUserId(),
            e);
      }
    }

    participants.clear();

    pipeline.release(new Continuation<Void>() {

      @Override
      public void onSuccess(Void result) throws Exception {
        log.trace("ROOM {}: Released Pipeline", Room.this.roomId);
      }

      @Override
      public void onError(Throwable cause) throws Exception {
        log.warn("ROOM {}: Could not release Pipeline", Room.this.roomId);
      }
    });

    log.debug("ROOM {} closed", this.roomId);
  }

}
