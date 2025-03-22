package org.improvejava.kurento_chat.user;

import com.google.gson.JsonObject;
import lombok.ToString;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ToString
public class UserSession implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(UserSession.class);

  private final String userName;
  private final String userId;
  private final WebSocketSession session;

  private final MediaPipeline pipeline;

  private final String roomId;
  private final WebRtcEndpoint outgoingMedia;
  private final ConcurrentMap<String, WebRtcEndpoint> incomingMediaByUserId = new ConcurrentHashMap<>();

  public UserSession(final String userName, String roomId, String userId, final WebSocketSession session,
                     MediaPipeline pipeline) {

    this.pipeline = pipeline;
    this.userName = userName;
    this.userId = userId;
    this.session = session;
    this.roomId = roomId;
    this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();

    this.outgoingMedia.addIceCandidateFoundListener(event -> {
        JsonObject response = new JsonObject();
        response.addProperty("action", "onIceCandidate");
        response.addProperty("userId", userId);
        response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(response.toString()));
            }
        } catch (IOException e) {
            log.debug(e.getMessage());
        }
    });
  }

  public WebRtcEndpoint getOutgoingWebRtcPeer() {
    return outgoingMedia;
  }

  public String getUserName() {
    return userName;
  }

  public String getUserId() {
    return userId;
  }

  public WebSocketSession getSession() {
    return session;
  }

  public String getRoomId() {
    return this.roomId;
  }

  public void receiveVideoFrom(UserSession sender, String sdpOffer) throws IOException {
    log.info("USER {} / {}: connecting with {} in room {}", this.userName, this.userId, sender.getUserName(), this.roomId);

    log.trace("USER {} / {}: SdpOffer for {} is {}", this.userName, this.userId, sender.getUserName(), sdpOffer);

    final String ipSdpAnswer = this.getEndpointForUser(sender).processOffer(sdpOffer);
    final JsonObject scParams = new JsonObject();
    scParams.addProperty("action", "receiveVideoFrom");
    scParams.addProperty("userId", sender.getUserId());
    scParams.addProperty("userName", sender.getUserName());
    scParams.addProperty("sdpAnswer", ipSdpAnswer);

    log.trace("USER {} / {}: SdpAnswer for {} is {}", this.userName, this.userId, sender.getUserName(), ipSdpAnswer);
    this.sendMessage(scParams);
    log.debug("gather candidates");
    this.getEndpointForUser(sender).gatherCandidates();
  }

  private WebRtcEndpoint getEndpointForUser(final UserSession sender) {
    if (sender.getUserId().equals(userId)) {
      log.debug("PARTICIPANT {} / {}: configuring loopback", this.userName, this.userId);
      return outgoingMedia;
    }

    log.debug("PARTICIPANT {} / {}: receiving video from {} / {}", this.userName, this.userId, sender.getUserName(), sender.getUserId());

    WebRtcEndpoint incoming = incomingMediaByUserId.get(sender.getUserId());
    if (incoming == null) {
      log.debug("PARTICIPANT {} / {}: creating new endpoint for {} / {}", this.userName, this.userId, sender.getUserName(), sender.getUserId());
      incoming = new WebRtcEndpoint.Builder(pipeline).build();

      incoming.addIceCandidateFoundListener(event -> {
          JsonObject response = new JsonObject();
          response.addProperty("action", "onIceCandidate");
          response.addProperty("userName", sender.getUserName());
          response.addProperty("userId", sender.getUserId());
          response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
          try {
              synchronized (session) {
                  session.sendMessage(new TextMessage(response.toString()));
              }
          } catch (IOException e) {
              log.debug(e.getMessage());
          }
      });

      incomingMediaByUserId.put(sender.getUserId(), incoming);
    }

    log.debug("PARTICIPANT {} / {}: obtained endpoint for {} / {}", this.userName, this.userId, sender.getUserName(), sender.getUserId());
    sender.getOutgoingWebRtcPeer().connect(incoming);

    return incoming;
  }

  public void cancelVideoFrom(final String senderId) {
    log.debug("PARTICIPANT {} / {} : canceling video reception from {}", this.userName, this.userId, senderId);
    final WebRtcEndpoint incoming = incomingMediaByUserId.remove(senderId);

    log.debug("PARTICIPANT {} / {}: removing endpoint for {}", this.userName, this.userId, senderId);
    incoming.release(new Continuation<Void>() {
      @Override
      public void onSuccess(Void result) throws Exception {
        log.trace("PARTICIPANT {} / {}: Released successfully incoming EP for {}",
            UserSession.this.userName, UserSession.this.userId, senderId);
      }

      @Override
      public void onError(Throwable cause) throws Exception {
        log.warn("PARTICIPANT {} / {}: Could not release incoming EP for {}", UserSession.this.userName,
                UserSession.this.userId, senderId);
      }
    });
  }

  @Override
  public void close() throws IOException {
    for (final String remoteParticipantUserId : incomingMediaByUserId.keySet()) {

      log.trace("PARTICIPANT {} / {}: Released incoming EP for {}", this.userName, this.userId, remoteParticipantUserId);

      final WebRtcEndpoint ep = this.incomingMediaByUserId.get(remoteParticipantUserId);

      ep.release(new Continuation<Void>() {

        @Override
        public void onSuccess(Void result) throws Exception {
          log.trace("PARTICIPANT {} / {}: Released successfully incoming EP for {}",
              UserSession.this.userName, UserSession.this.userId, remoteParticipantUserId);
        }

        @Override
        public void onError(Throwable cause) throws Exception {
          log.warn("PARTICIPANT {} / {}: Could not release incoming EP for {}", UserSession.this.userName,
                  UserSession.this.userId, remoteParticipantUserId);
        }
      });
    }

    outgoingMedia.release(new Continuation<Void>() {

      @Override
      public void onSuccess(Void result) throws Exception {
        log.trace("PARTICIPANT {} / {} : Released outgoing EP", UserSession.this.userName, UserSession.this.userId);
      }

      @Override
      public void onError(Throwable cause) throws Exception {
        log.warn("USER {} / {}: Could not release outgoing EP", UserSession.this.userName, UserSession.this.userId);
      }
    });
  }

  // 해당 유저 세션을 가진 사용자에게 메시지 보낼 때 이용하는 메서드
  public void sendMessage(JsonObject message) throws IOException {
    log.debug("USER {} / {}: Sending message {}", userName, userId, message);
    synchronized (session) {
      session.sendMessage(new TextMessage(message.toString()));
    }
  }

  // 같은 방에 있는 사용자끼리만 보낼 수 있게 설정 추가 필요
  public void sendMessage(UserSession sender, String message) throws IOException {
    JsonObject messageToSend = new JsonObject();

    log.debug("USER {} / {}: Sending message {} to USER {} / {}", sender.userName, sender.userId, message, userName, userId);

    messageToSend.addProperty("action", "sendChat");
    messageToSend.addProperty("senderId", sender.userId);
    messageToSend.addProperty("senderName", sender.userName);
    messageToSend.addProperty("receiverId", userId);
    messageToSend.addProperty("receiverName", userName);
    messageToSend.addProperty("message", message);
    messageToSend.addProperty("isSendToAll", false);

    synchronized (sender.session) {
      sender.session.sendMessage(new TextMessage(messageToSend.toString()));
    }

    synchronized (session) {
      session.sendMessage(new TextMessage(messageToSend.toString()));
    }
  }

  static public void sendMessageToAll(UserSession sender, List<UserSession> recieverList, String message) throws IOException {
    JsonObject messageToReceiver = new JsonObject();
    List<Participant> receivedMembers = new ArrayList<Participant>();

    for (UserSession reciever : recieverList) {
      if (reciever.getUserId().equals(sender.userId)) {continue;}
      log.debug("USER {} / {}: Sending message {} to USER {} / {}", sender.userName, sender.userId, message, reciever.getUserName(), reciever.getUserId());

      messageToReceiver.addProperty("action", "sendChat");
      messageToReceiver.addProperty("senderId", sender.userId);
      messageToReceiver.addProperty("senderName", sender.userName);
      messageToReceiver.addProperty("receiverId", reciever.getUserId());
      messageToReceiver.addProperty("receiverName", reciever.getUserName());
      messageToReceiver.addProperty("message", message);
      messageToReceiver.addProperty("isSendToAll", true);

      Participant participant = new Participant(reciever.getUserId(), reciever.getUserName());
      receivedMembers.add(participant);

      synchronized (reciever.getSession()) {
        reciever.getSession().sendMessage(new TextMessage(messageToReceiver.toString()));
      }
    }

    JsonObject messageToSender = new JsonObject();
    messageToSender.addProperty("action", "sendChat");
    messageToSender.addProperty("senderId", sender.userId);
    messageToSender.addProperty("senderName", sender.userName);
    messageToSender.addProperty("message", message);
    messageToSender.addProperty("isSendToAll", true);
    messageToSender.addProperty("receiver", receivedMembers.toString());

    synchronized (sender.session) {
      sender.session.sendMessage(new TextMessage(messageToSender.toString()));
    }
  }

  // 같은 방에 있는 사용자끼리만 보낼 수 있게 설정 추가 필요
  public void sendEmoji(UserSession sender, String selectedEmoji) throws IOException {
    JsonObject emojiToSend = new JsonObject();

    log.debug("USER {} / {}: Sending emoji {} to USER {} / {}", sender.userName, sender.userId, selectedEmoji, userName, userId);

    emojiToSend.addProperty("action", "sendEmoji");
    emojiToSend.addProperty("senderId", sender.userId);
    emojiToSend.addProperty("senderName", sender.userName);
    emojiToSend.addProperty("receiverId", userId);
    emojiToSend.addProperty("receiverName", userName);
    emojiToSend.addProperty("emoji", selectedEmoji);
    emojiToSend.addProperty("isSendToAll", false);

    synchronized (sender.session) {
      sender.session.sendMessage(new TextMessage(emojiToSend.toString()));
    }

    synchronized (session) {
      session.sendMessage(new TextMessage(emojiToSend.toString()));
    }
  }

  // 같은 방에 있는 사용자끼리만 보낼 수 있게 설정 추가 필요
  public void sendEmojiToAll(UserSession sender, List<UserSession> recieverList, String selectedEmoji) throws IOException {
    JsonObject emojiToSend = new JsonObject();
    List<Participant> receivedMembers = new ArrayList<Participant>();

    for (UserSession reciever : recieverList) {
      if (reciever.getUserId().equals(sender.userId)) {continue;}
      log.debug("USER {} / {}: Sending message {} to USER {} / {}", sender.userName, sender.userId, selectedEmoji, reciever.getUserName(), reciever.getUserId());

      emojiToSend.addProperty("action", "sendEmoji");
      emojiToSend.addProperty("senderId", sender.userId);
      emojiToSend.addProperty("senderName", sender.userName);
      emojiToSend.addProperty("receiverId", reciever.getUserId());
      emojiToSend.addProperty("receiverName", reciever.getUserName());
      emojiToSend.addProperty("emoji", selectedEmoji);
      emojiToSend.addProperty("isSendToAll", true);

      Participant participant = new Participant(reciever.getUserId(), reciever.getUserName());
      receivedMembers.add(participant);

      synchronized (reciever.getSession()) {
        reciever.getSession().sendMessage(new TextMessage(emojiToSend.toString()));
      }
    }

    JsonObject messageToSender = new JsonObject();
    messageToSender.addProperty("action", "sendEmoji");
    messageToSender.addProperty("senderId", sender.userId);
    messageToSender.addProperty("senderName", sender.userName);
    messageToSender.addProperty("emoji", selectedEmoji);
    messageToSender.addProperty("isSendToAll", true);
    messageToSender.addProperty("receiver", receivedMembers.toString());

    synchronized (sender.session) {
      sender.session.sendMessage(new TextMessage(messageToSender.toString()));
    }
  }

  public void addCandidate(IceCandidate candidate, String userId) throws IOException {
    if (this.userId.compareTo(userId) == 0) {
      outgoingMedia.addIceCandidate(candidate);
    } else {
      WebRtcEndpoint webRtc = incomingMediaByUserId.get(userId);
      if (webRtc != null) {
        webRtc.addIceCandidate(candidate);
      }
    }
  }

  @Override
  public boolean equals(Object obj) {

    if (this == obj) {
      return true;
    }
    if (obj == null || !(obj instanceof UserSession)) {
      return false;
    }
    UserSession other = (UserSession) obj;
    boolean eq = userId.equals(other.userId);
    eq &= roomId.equals(other.roomId);
    return eq;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + userId.hashCode();
    result = 31 * result + roomId.hashCode();
    return result;
  }
}
