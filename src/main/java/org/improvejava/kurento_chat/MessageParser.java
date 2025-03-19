package org.improvejava.kurento_chat;

import com.google.gson.JsonObject;
import org.springframework.stereotype.Component;

@Component
public class MessageParser {
    private JsonObject receivedMessage;

    public MessageParser(JsonObject receivedMessage) {
        this.receivedMessage = receivedMessage;
    }

    /**
     * 받은 메시지를 방에 참가할 수 있게 파싱
     */
    public JoinRoomDTO parseForJoinRoom(JsonObject receivedMessage) {
        return new JoinRoomDTO(receivedMessage.get("userName").getAsString(),
                receivedMessage.get("roomId").getAsString());
    }

    /**
     * 받은 메시지를 방 생성 가능하게 파싱
     */
    public CreateRoomDTO parseForCreateRoom(JsonObject receivedMessage) {
        return new CreateRoomDTO(receivedMessage.get("userName").getAsString());
    }

    /**
     * 받은 메시지를 채팅 보내기 가능하게 파싱
     * */
    public SendChatDTO parseForSendChat(JsonObject receivedMessage) {
        return new SendChatDTO(receivedMessage.get("senderId").getAsString(),
                receivedMessage.get("receiverId").getAsString(),
                receivedMessage.get("message").getAsString());
    }

    /**
     * 받은 메시지를 채팅 보내기 가능하게 파싱
     * */
    public SendEmojiDTO parseForSendEmoji(JsonObject receivedMessage) {
        return new SendEmojiDTO(receivedMessage.get("senderId").getAsString(),
                receivedMessage.get("receiverId").getAsString(),
                receivedMessage.get("emoji").getAsString());
    }
}
