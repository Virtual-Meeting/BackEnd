package org.improvejava.kurento_chat.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.improvejava.kurento_chat.parsing.MessageParser;
import org.improvejava.kurento_chat.dto.SendChatDTO;
import org.improvejava.kurento_chat.dto.SendEmojiDTO;
import org.improvejava.kurento_chat.dto.CreateRoomDTO;
import org.improvejava.kurento_chat.dto.JoinRoomDTO;
import org.improvejava.kurento_chat.room.Room;
import org.improvejava.kurento_chat.room.RoomManager;
import org.improvejava.kurento_chat.user.UserRegistry;
import org.improvejava.kurento_chat.user.UserSession;
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
import java.util.List;

/**
 * This class is a handler used for WebSocket connections.
 * It is responsible for managing WebSocket communication and processing messages.
 *
 * <p>
 * {@code CallHandler} handles incoming WebSocket messages, performs any necessary processing,
 * and responds back to the client through WebSocket.
 * </p><br>
 *
 * <p><b>한국어:</b>
 * 이 클래스는 웹 소켓 연결 시 사용하는 핸들러입니다. 웹 소켓 연결과 메시지 처리를 관리합니다.</p>
 *
 * <p>{@code CallHandler}는 수신한 웹소켓 메시지를 처리한 후, 클라이언트에게 응답을 보냅니다.</p>
 */
@Component
public class CallHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(CallHandler.class);

  private static final Gson gson = new GsonBuilder().create();

  private final RoomManager roomManager;

  private final UserRegistry userRegistry;

  private final MessageParser messageParser;

  /**
   * Creates a new {@code CallHandler} instance with the specified {@code roomManager} and {@code userRegistry}.
   *
   * <p><b>한국어:</b>
   * {@code roomManager}와 {@code userRegistry}를 이용하여 새로운 {@code CallHandler} 객체를 생성합니다. </p><br>
   *
   * @param roomManager The object responsible for managing rooms.
   * @param userRegistry The object that handles user registration and management.
   * @param messageParser
   */
  @Autowired
  public CallHandler(RoomManager roomManager, UserRegistry userRegistry, MessageParser messageParser) {
    this.roomManager = roomManager;
    this.userRegistry = userRegistry;
    this.messageParser = messageParser;
  }

  /**
   * 웹 소켓 메시지를 받아서 해당 {@code eventId}에 따라 주어진 동작을 하고 메시지를 응답함
   * @param session 웹 소켓 연결 세션
   * @param message 웹 소켓으로 받은 요청 메시지
   * @throws Exception 에러 처리가 되지 않은 에러 던짐?
   *
   * 하는 일
   * - 메시지 받기
   * - 사용자가 처음 왔는지 아닌지 확인 후 디버그 메시지로 받은거 출력
   * - 각 메시지 아이디에 해당하는 동작하게 함 + 여기서 메시지 파싱해서 파리미터 전달까지
   *
   * 추후 구현?
   * 처음 온 사용자 & 아닌 사용자 구분해서 처리할 수 있게 뭔가 해야 할 듯 여기서 에러처리 필요하지 않을까?
   */
  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    final JsonObject receivedMessage = gson.fromJson(message.getPayload(), JsonObject.class);

    final UserSession user = userRegistry.getBySession(session);

    if (user != null) {
      log.debug("Incoming message from user '{}': {}", user.getUserName(), receivedMessage);
    } else {
      log.debug("Incoming message from new user: {}", receivedMessage);
    }

    switch (receivedMessage.get("eventId").getAsString()) {
      case "joinRoom":
        joinRoom(receivedMessage, session);
        break;

      case "createRoom":
        createRoom(receivedMessage, session);
        break;

      case "onIceCandidate":
        JsonObject candidateInfo = receivedMessage.get("candidate").getAsJsonObject();

        if (user != null) {
          IceCandidate candidate = new IceCandidate(candidateInfo.get("candidate").getAsString(),
                  candidateInfo.get("sdpMid").getAsString(), candidateInfo.get("sdpMLineIndex").getAsInt());
          user.addCandidate(candidate, receivedMessage.get("userId").getAsString());
        }
        break;

      case "receiveVideoFrom":
        final String videoSenderId = receivedMessage.get("userId").getAsString();
        final UserSession sender = userRegistry.getByUserId(videoSenderId);
        final String sdpOffer = receivedMessage.get("sdpOffer").getAsString();
        user.receiveVideoFrom(sender, sdpOffer);
        break;

      case "exitRoom":
        if (user != null) {
          exitRoom(user);
        }
        break;

      case "sendChat":
        final boolean isSendChatToAll = receivedMessage.get("isSendToAll").getAsBoolean();
        if (isSendChatToAll) {
          sendChatToAll(receivedMessage);
        } else {
          sendChat(receivedMessage);
        }
        break;

      case "sendEmoji":
        final boolean isSendEmojiToAll = receivedMessage.get("isSendToAll").getAsBoolean();
        if (isSendEmojiToAll) {
          sendEmojiToAll(receivedMessage);
        } else {
          sendEmoji(receivedMessage);
        }
        break;

      case "changeName":
        String userId = receivedMessage.get("userId").getAsString();
        String newName = receivedMessage.get("newName").getAsString();
        changeName(userId, newName);
        break;

      case "audioStateChange":
        userId = receivedMessage.get("userId").getAsString();
        Boolean turnAudioOn = Boolean.parseBoolean(receivedMessage.get("audioOn").getAsString());
        changeAudioState(userId, turnAudioOn);
        break;

      case "videoStateChange":
        userId = receivedMessage.get("userId").getAsString();
        Boolean turnVideoOn = Boolean.parseBoolean(receivedMessage.get("videoOn").getAsString());
        changeVideoState(userId, turnVideoOn);
        break;

      default:
        break;
    }
  }

  /**
   * 커넥션이 끊길 경우 수행하는 함수
   *
   * 하는 일
   * - 해당 메서드를 동작하는 이유 / 즉 연결 끊긴 원인 출력
   * - userSession이 제대로 닫히지 않았다면.. 아마 이거 맞을껄??? registry에 해당 요소가 저장되어 있어서 아직
   *     userSession 사용처가 있어서 gc가 날리지 않았다면 날리게 함
   * - roomManager에서 user가 아직 남아있다면 해당 user를 룸에서 떠나게 함 & userSession 객체 close
   */
  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    log.info(String.valueOf(status));
    UserSession userSession = userRegistry.removeBySession(session);
    if (userSession == null) {
      return;
    }

    log.info("User {} / {} WebSocket didn't close well", userSession.getUserName(), userSession.getUserId());
    roomManager.leaveRoom(userSession);
  }

  /** 방 참가
   */
  private void joinRoom(JsonObject receivedMessage, WebSocketSession session) throws IOException {
    JoinRoomDTO joinRoomDTO = messageParser.parseForJoinRoom(receivedMessage);

    log.info("PARTICIPANT {} / {} : trying to join room {}", joinRoomDTO.getUserName(), joinRoomDTO.getUserId(), joinRoomDTO.getRoomId());

    final UserSession user  = roomManager.joinRoom(joinRoomDTO.getUserName(), joinRoomDTO.getUserId(), joinRoomDTO.getRoomId(), joinRoomDTO.getIsAudioOn(), joinRoomDTO.getIsVideoOn(), session);

    userRegistry.register(user);
  }

  private void createRoom(JsonObject receivedMessage, WebSocketSession session) throws IOException {
    CreateRoomDTO createRoomDTO = messageParser.parseForCreateRoom(receivedMessage);

    log.info("PARTICIPANT {} / {} : trying to make room", createRoomDTO.getUserName(), createRoomDTO.getUserId());

    // 추후 프론트와 협의 후 삭제
    final UserSession user = roomManager.createRoom(createRoomDTO.getUserName(), createRoomDTO.getUserId(),
            createRoomDTO.getIsAudioOn(), createRoomDTO.getIsVideoOn(), session);
    userRegistry.register(user);
  }

  private void exitRoom(UserSession user) throws IOException {
    roomManager.leaveRoom(user);
    userRegistry.removeBySession(user.getSession());
  }

  private void sendChat(JsonObject receivedMessage) throws IOException {
    SendChatDTO sendChatDTO = messageParser.parseForSendChat(receivedMessage);
    UserSession messageSender = userRegistry.getByUserId(sendChatDTO.getSenderId());
    UserSession messageReceiver = userRegistry.getByUserId(sendChatDTO.getReceiverId());

    messageReceiver.sendChat(messageSender, sendChatDTO.getMessage());
  }

  private void sendChatToAll(JsonObject receivedMessage) throws IOException {
    SendChatDTO sendChatDTO = messageParser.parseForSendChat(receivedMessage);
    UserSession messageSender = userRegistry.getByUserId(sendChatDTO.getSenderId());
    String roomId = messageSender.getRoomId();

    List<UserSession> receiverList = roomManager.getRoom(roomId).getParticipants().stream().toList();
    UserSession.sendChatToAll(messageSender, receiverList, sendChatDTO.getMessage());
  }

  private void sendEmoji(JsonObject receivedMessage) throws IOException {
    SendEmojiDTO sendEmojiDTO = messageParser.parseForSendEmoji(receivedMessage);

    UserSession emojiReceiver = userRegistry.getByUserId(sendEmojiDTO.getReceiverId());
    UserSession emojiSender = userRegistry.getByUserId(sendEmojiDTO.getSenderId());
    emojiReceiver.sendEmoji(emojiSender, sendEmojiDTO.getEmoji());
  }

  private void sendEmojiToAll(JsonObject receivedMessage) throws IOException {
    SendEmojiDTO sendEmojiDTO = messageParser.parseForSendEmoji(receivedMessage);
    UserSession emojiSender = userRegistry.getByUserId(sendEmojiDTO.getSenderId());
    String roomId = emojiSender.getRoomId();

    List<UserSession> receiverList = roomManager.getRoom(roomId).getParticipants().stream().toList();
    UserSession.sendEmojiToAll(emojiSender, receiverList, sendEmojiDTO.getEmoji());
  }

  private void changeName(String userId, String newName) throws IOException {
    UserSession userSession = userRegistry.getByUserId(userId);
    Room room = roomManager.getRoom(userSession.getRoomId());
    userSession.changeName(newName, room);
  }

  private void changeAudioState(String userId, Boolean turnAudioOn) throws IOException {
    UserSession userSession = userRegistry.getByUserId(userId);
    List<UserSession> receiverList = roomManager.getRoom(userSession.getRoomId()).getParticipants().stream().toList();
    userSession.changeAudioState(receiverList, turnAudioOn);
  }

  private void changeVideoState(String userId, Boolean turnVideoOn) throws IOException {
    UserSession userSession = userRegistry.getByUserId(userId);
    List<UserSession> receiverList = roomManager.getRoom(userSession.getRoomId()).getParticipants().stream().toList();
    userSession.changeVideoState(receiverList, turnVideoOn);
  }
}
