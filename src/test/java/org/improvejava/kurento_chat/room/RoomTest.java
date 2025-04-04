package org.improvejava.kurento_chat.room;

import org.improvejava.kurento_chat.user.Participant;
import org.improvejava.kurento_chat.user.UserSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kurento.client.Continuation;
import org.kurento.client.MediaPipeline;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RoomTest {

    @Mock
    private MediaPipeline mediaPipeline;

    @Mock
    private UserSession user1;

    @Mock
    private UserSession user2;

    private Room room;

    private final String USERID1 = "4641bf8f-36df-4a1e-a908-60b4073631c9";
    private final String USERID2 = "erwegwrr-324g-546h-xdf3-535462343242";

    @BeforeEach
    void setUp() {
        room = new Room(mediaPipeline);
    }

    @DisplayName("방을 생성한다.")
    @Test
    public void createRoom() {
        assertThat(room.getRoomId()).isNotNull();
        assertThat(room.getPipeline()).isNotNull();
        assertThat(room.getParticipants().isEmpty()).isTrue();
    }

    @DisplayName("방 참가자 리스트에 새로운 참가자를 추가한다.")
    @Test
    public void addParticipant() {
        // given
        when(user1.getUserId()).thenReturn(USERID1);

        // when
        room.addParticipant(user1);

        // then
        assertThat(room.getParticipants().size()).isEqualTo(1);
        assertThat(room.getParticipants().contains(user1)).isTrue();
    }

    @DisplayName("방 참가자 리스트에서 기존의 참가자를 삭제한다.")
    @Test
    public void removeParticipant() {
        // given
        when(user1.getUserId()).thenReturn(USERID1);

        room.addParticipant(user1);

        // when
        room.removeParticipant(USERID1);

        // then
        assertThat(room.getParticipants().isEmpty()).isTrue();
    }

    @DisplayName("방 참가자 리스트에서 존재하지 않는 참가자를 삭제하려면 예외가 발생한다.")
    @Test
    public void removeParticipantNotExist() {
        // given
        when(user1.getUserId()).thenReturn(USERID1);

        room.addParticipant(user1);

        // when & then
        assertThatThrownBy(() -> room.removeParticipant(USERID2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 ID를 가진 사용자는 참가자 리스트에 없으므로 삭제할 수 없습니다.");
    }

    @DisplayName("방장을 변경한다.")
    @Test
    public void changeRoomLeader() {
        //given
        Participant newRoomLeader = mock(Participant.class);

        // when
        room.changeRoomCreator(newRoomLeader);

        // then
        assertThat(newRoomLeader).isEqualTo(room.getRoomLeader());
    }

    @DisplayName("랜덤으로 방 참가자 중 한 명을 선택한다.")
    @Test
    public void getRandomParticipant() {
        // given
        when(user1.getUserId()).thenReturn(USERID1);
        when(user2.getUserId()).thenReturn(USERID2);

        room.addParticipant(user1);
        room.addParticipant(user2);

        // when & then
        assertThat(room.getRandomParticipant()).isIn(user1, user2);
    }

    @DisplayName("방을 닫는다.")
    @Test
    public void closeRoom() throws IOException {
        InOrder inOrder = inOrder(user1, user2, mediaPipeline);

        // given
        when(user1.getUserId()).thenReturn(USERID1);
        when(user2.getUserId()).thenReturn(USERID2);

        room.addParticipant(user1);
        room.addParticipant(user2);

        // when
        room.close();

        // then
        inOrder.verify(user1).close();
        inOrder.verify(user2).close();
        inOrder.verify(mediaPipeline).release(any(Continuation.class));

        assertThat(room.getParticipants().isEmpty()).isTrue();
    }
}