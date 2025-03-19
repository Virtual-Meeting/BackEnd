package org.improvejava.kurento_chat.room;

import com.google.gson.JsonObject;
import org.improvejava.kurento_chat.user.Participant;
import org.improvejava.kurento_chat.user.UserSession;
import org.kurento.client.Continuation;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

public class Room implements Closeable {
  private final Logger log = LoggerFactory.getLogger(Room.class);

  private final ConcurrentMap<String, UserSession> participants = new ConcurrentHashMap<>();
  private final MediaPipeline pipeline;
  private final String roomId;
  private Participant roomCreator;

  public Room(String roomId, MediaPipeline pipeline) {
    this.roomId = roomId;
    this.pipeline = pipeline;
    log.info("ROOM {} has been created", this.roomId);
  }

  public String getRoomId() {
    return roomId;
  }

  public MediaPipeline getPipeline() {
    return pipeline;
  }

  public Collection<UserSession> getParticipants() {
    return participants.values();
  }

  public UserSession getRandomParticipant() {
    List<String> participantKeys = new ArrayList<>(participants.keySet());
    String keyForRandomParticipant = participantKeys.get(ThreadLocalRandom.current().nextInt(participants.size()));
    return participants.get(keyForRandomParticipant);
  }

  public Participant getRoomCreator() {
    return roomCreator;
  }

  public void addParticipant(UserSession participant) {
    participants.put(participant.getUserId(), participant);
  }

  public void removeParticipant(String userId) {
    participants.remove(userId);
  }

  public void changeRoomCreator(Participant newCreator) {
    roomCreator = newCreator;
  }

  @PreDestroy
  private void shutdown() {
    this.close();
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
