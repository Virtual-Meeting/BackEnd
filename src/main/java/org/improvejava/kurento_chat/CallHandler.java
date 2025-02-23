package org.improvejava.kurento_chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.kurento.client.IceCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.UUID;

@Component
public class CallHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(CallHandler.class);

  private static final Gson gson = new GsonBuilder().create();

  private final RoomManager roomManager;

  private final UserRegistry registry;

  @Autowired
  public CallHandler(RoomManager roomManager, UserRegistry registry) {
    this.roomManager = roomManager;
    this.registry = registry;
  }

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    final JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);

    final UserSession user = registry.getBySession(session);

    if (user != null) {
      log.debug("Incoming message from user '{}': {}", user.getUserName(), jsonMessage);
    } else {
      log.debug("Incoming message from new user: {}", jsonMessage);
    }

    switch (jsonMessage.get("eventId").getAsString()) {
      case "joinRoom":
        joinRoom(jsonMessage, session);
        break;

      case "createRoom":
        createRoom(jsonMessage, session);
        break;

      case "onIceCandidate":
        JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();

        if (user != null) {
          IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(),
                  candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
          user.addCandidate(cand, jsonMessage.get("userId").getAsString());
        }
        break;

      case "receiveVideoFrom":
        final String senderId = jsonMessage.get("userId").getAsString();
        final UserSession sender = registry.getByUserId(senderId);
        final String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
        user.receiveVideoFrom(sender, sdpOffer);
        break;

      case "exitRoom":
          if (user != null) {
            exitRoom(user);
          }
        break;

      default:
        break;
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    UserSession user = registry.removeBySession(session);
    if (user == null) {
      throw new IllegalArgumentException("방 생성이 제대로 되지 않았습니다.");
    }
    roomManager.getRoom(user.getRoomId()).leave(user);
  }

  private void joinRoom(JsonObject params, WebSocketSession session) throws IOException {
    final String roomId = params.get("roomId").getAsString();
    final String userName = params.get("userName").getAsString();
    final String userId = UUID.randomUUID().toString();
    log.info("PARTICIPANT {} / {} : trying to join room {}", userName, userId, roomId);

    Room room = roomManager.getRoom(roomId);
    final UserSession user = room.joinRoom(userName, userId, session);
    registry.register(user);
  }

  private void createRoom(JsonObject params, WebSocketSession session) throws IOException {
    final String userName = params.get("userName").getAsString();
    final String userId = UUID.randomUUID().toString();
    log.info("PARTICIPANT {} / {} : trying to make room", userName, userId);

    Room room = roomManager.createRoom();
    final UserSession user = room.createRoom(userName, userId, session);
    Participant participant = new Participant(userId, userName);
    room.setRoomCreator(participant);
    registry.register(user);
  }

  private void exitRoom(UserSession user) throws IOException {
    final Room room = roomManager.getRoom(user.getRoomId());
    room.leave(user);
    if (room.getParticipants().isEmpty()) {
      roomManager.removeRoom(room);
    }
  }
}
