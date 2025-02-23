package org.improvejava.kurento_chat;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Participant {
    public Participant() {}

    public Participant(String userId, String userName) {
        this.userId = userId;
        this.userName = userName;
    }

    String userId;

    String userName;
}
