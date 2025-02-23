package org.improvejava.kurento_chat;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Component
public class UserRegistry {
  Logger logger = Logger.getLogger(UserRegistry.class.getName());

  private final ConcurrentHashMap<String, UserSession> userSessionByUserId = new ConcurrentHashMap<>();

  public void register(UserSession user) {
    userSessionByUserId.put(user.getSession().getId(), user);
  }

  public UserSession getByUserId(String userId) {
    return userSessionByUserId.get(userId);
  }

  public UserSession getBySession(WebSocketSession session) {
    return userSessionByUserId.get(session.getId());
  }

  public boolean exists(String userId) {
    return userSessionByUserId.containsKey(userId);
  }

  public UserSession removeBySession(WebSocketSession session) {
    final UserSession user = getBySession(session);
    try {
      userSessionByUserId.remove(session.getId());
    } catch (NullPointerException e) {
      logger.warning(e.getMessage() + " session이 null이라 지울 수 없습니다.");
    }
    return user;
  }

}
