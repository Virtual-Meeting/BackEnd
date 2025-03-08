package org.improvejava.kurento_chat;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Component
public class UserRegistry {
  Logger logger = Logger.getLogger(UserRegistry.class.getName());

  private final ConcurrentHashMap<String, UserSession> userSessionByUserId = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, UserSession> userSessionBySessionId = new ConcurrentHashMap<>();

  public void register(UserSession user) {
    userSessionByUserId.put(user.getUserId(), user);
    userSessionBySessionId.put(user.getSession().getId(), user);
  }

  public UserSession getByUserId(String userId) {
    return userSessionByUserId.get(userId);
  }

  public UserSession getBySession(WebSocketSession session) {
    return userSessionBySessionId.get(session.getId());
  }

  public List<UserSession> getUserSessionsBy(String roomId) {
    return userSessionByUserId.values().stream()
            .filter(userSession -> userSession.getRoomId().equals(roomId))
            .toList();
  }

  public boolean exists(String userId) {
    return userSessionByUserId.containsKey(userId);
  }

  public UserSession removeBySession(WebSocketSession session) {
    final UserSession user = getBySession(session);
    try {
      userSessionByUserId.remove(user.getUserId());
      userSessionBySessionId.remove(session.getId());
    } catch (NullPointerException e) {
      logger.warning(e.getMessage() + " session이 null이라 지울 수 없습니다.");
    }
    return user;
  }

}
