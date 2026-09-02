package com.kickoff.be.user.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.chat.dto.ChatRoomResponse;
import com.kickoff.be.chat.service.ChatService;
import com.kickoff.be.user.dto.PushTokenRequest;
import com.kickoff.be.user.dto.UserResponse;
import com.kickoff.be.user.dto.UserUpdateRequest;
import com.kickoff.be.team.dto.MyTeamResponse;
import com.kickoff.be.team.service.TeamAdminService;
import com.kickoff.be.user.dto.AccountDeleteRequest;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.service.AccountDeletionService;
import com.kickoff.be.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;
    private final TeamAdminService teamAdminService;
    private final ChatService chatService;

    /** 내가 소유·관리하는 팀 목록 (계약서 §4-2, v1.9.1). 없으면 빈 배열. */
    @GetMapping("/me/teams")
    public ResponseEntity<List<MyTeamResponse>> myTeams(@LoginUser User user) {
        return ResponseEntity.ok(teamAdminService.myTeams(user));
    }

    /**
     * 채팅 탭의 방 목록 (계약서 §6-1, v1.13.0). 내가 나간 방은 빠지고, 팀이 없으면 빈 배열.
     *
     * 경로가 /api/requests 쪽이 아니라 여기인 건 방이 아니라 <b>사용자</b>를 기준으로 모으는
     * 조회라서다 — 내가 주장인 팀 전부의 방이 한 목록에 들어온다.
     */
    @GetMapping("/me/chats")
    public ResponseEntity<List<ChatRoomResponse>> myChats(@LoginUser User user) {
        return ResponseEntity.ok(chatService.myChats(user));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(@LoginUser User user,
                                                 @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.update(user, request));
    }

    /** Expo push token 등록. body 의 토큰이 null 이면 해제다 (계약서 §8). */
    @PutMapping("/me/push-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePushToken(@LoginUser User user,
                                @Valid @RequestBody PushTokenRequest request) {
        userService.updatePushToken(user, request);
    }

    /**
     * 회원 탈퇴 (계약서 §3-4, v1.23.0). 되돌릴 수 없다.
     *
     * <b>body 가 없어도 된다.</b> 소셜 계정은 비밀번호가 없어 그냥 부른다 —
     * {@code required = false} 를 빼면 소셜 사용자가 400 으로 막혀 영영 탈퇴할 수 없다.
     *
     * 이중 확인은 FE 몫이다. 서버가 두 번 물을 방법이 없다.
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@LoginUser User user,
                         @RequestBody(required = false) AccountDeleteRequest request) {
        accountDeletionService.delete(user, request);
    }
}
