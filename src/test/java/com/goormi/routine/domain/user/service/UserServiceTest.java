package com.goormi.routine.domain.user.service;

import com.goormi.routine.domain.auth.repository.RedisRepository;
import com.goormi.routine.domain.auth.service.JwtTokenProvider;
import com.goormi.routine.domain.ranking.service.RankingService;
import com.goormi.routine.domain.user.dto.UserRequest;
import com.goormi.routine.domain.user.dto.UserResponse;
import com.goormi.routine.domain.user.entity.User;
import com.goormi.routine.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("ci")
@Transactional
public class UserServiceTest {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@MockBean
	private RankingService rankingService;

	@MockBean
	private RedisRepository redisRepository;

	@MockBean
	private JwtTokenProvider jwtTokenProvider;

	private Long userId;
	private User testUser;

	@BeforeEach
	void setUp() {
		// 테스트용 사용자 생성
		testUser = User.builder()
			.kakaoId("testKakaoId")
			.email("test@kakao.com")
			.nickname("testUser")
			.profileMessage("Hello, I'm a test user!")
			.profileImageUrl("https://example.com/profile.jpg")
			.build();
		userRepository.save(testUser);
		userId = testUser.getId();

		when(rankingService.getTotalScoreByUser(anyLong())).thenReturn(1500L);
	}

	@Test
	@DisplayName("내 프로필 조회 성공")
	public void getMyProfile_Success() {
		// when
		UserResponse response = userService.getMyProfile(userId);

		// then
		assertThat(response).isNotNull();
		assertThat(response.id()).isEqualTo(userId);
		assertThat(response.nickname()).isEqualTo("testUser");
		assertThat(response.profileMessage()).isEqualTo("Hello, I'm a test user!");
		assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profile.jpg");
		assertThat(response.totalScore()).isEqualTo(1500L);

		verify(rankingService, times(1)).getTotalScoreByUser(userId);
	}

	@Test
	@DisplayName("내 프로필 조회 실패 - 존재하지 않는 사용자")
	public void getMyProfile_Fail_UserNotFound() {
		// given
		Long nonExistentUserId = 999L;

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> userService.getMyProfile(nonExistentUserId));

		assertThat(exception.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
		verify(rankingService, never()).getTotalScoreByUser(anyLong());
	}

	@Test
	@DisplayName("다른 사용자 프로필 조회 성공")
	public void getUserProfile_Success() {
		// when
		UserResponse response = userService.getUserProfile(userId);

		// then
		assertThat(response).isNotNull();
		assertThat(response.id()).isEqualTo(userId);
		assertThat(response.nickname()).isEqualTo("testUser");
		assertThat(response.profileMessage()).isEqualTo("Hello, I'm a test user!");
		assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profile.jpg");
		assertThat(response.totalScore()).isEqualTo(1500L);

		verify(rankingService, times(1)).getTotalScoreByUser(userId);
	}

	@Test
	@DisplayName("다른 사용자 프로필 조회 실패 - 존재하지 않는 사용자")
	public void getUserProfile_Fail_UserNotFound() {
		// given
		Long nonExistentUserId = 999L;

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> userService.getUserProfile(nonExistentUserId));

		assertThat(exception.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
		verify(rankingService, never()).getTotalScoreByUser(anyLong());
	}

	@Test
	@DisplayName("프로필 업데이트 성공 - 모든 정보 변경")
	public void updateProfile_Success_AllFields() {
		// given
		UserRequest request = UserRequest.builder()
			.nickname("updatedUser")
			.profileMessage("Updated profile message!")
			.profileImageUrl("https://example.com/updated-profile.jpg")
			.build();

		// when
		UserResponse response = userService.updateProfile(userId, request);

		// then
		assertThat(response).isNotNull();
		assertThat(response.id()).isEqualTo(userId);
		assertThat(response.nickname()).isEqualTo("updatedUser");
		assertThat(response.profileMessage()).isEqualTo("Updated profile message!");
		assertThat(response.profileImageUrl()).isEqualTo("https://example.com/updated-profile.jpg");
		assertThat(response.totalScore()).isEqualTo(1500L);

		// DB에서 확인
		User updatedUser = userRepository.findById(userId).orElseThrow();
		assertThat(updatedUser.getNickname()).isEqualTo("updatedUser");
		assertThat(updatedUser.getProfileMessage()).isEqualTo("Updated profile message!");
		assertThat(updatedUser.getProfileImageUrl()).isEqualTo("https://example.com/updated-profile.jpg");

		verify(rankingService, times(1)).getTotalScoreByUser(userId);
	}

	@Test
	@DisplayName("프로필 업데이트 성공 - 닉네임만 변경")
	public void updateProfile_Success_NicknameOnly() {
		// given
		UserRequest request = UserRequest.builder()
			.nickname("onlyNicknameChanged")
			.profileMessage(null)
			.profileImageUrl(null)
			.build();

		// when
		UserResponse response = userService.updateProfile(userId, request);

		// then
		assertThat(response).isNotNull();
		assertThat(response.nickname()).isEqualTo("onlyNicknameChanged");
		assertThat(response.profileMessage()).isEqualTo("Hello, I'm a test user!"); // 기존 값 유지
		assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profile.jpg"); // 기존 값 유지

		// DB에서 확인
		User updatedUser = userRepository.findById(userId).orElseThrow();
		assertThat(updatedUser.getNickname()).isEqualTo("onlyNicknameChanged");
		assertThat(updatedUser.getProfileMessage()).isEqualTo("Hello, I'm a test user!");
		assertThat(updatedUser.getProfileImageUrl()).isEqualTo("https://example.com/profile.jpg");
	}

	@Test
	@DisplayName("프로필 업데이트 성공 - 프로필 메시지만 변경")
	public void updateProfile_Success_ProfileMessageOnly() {
		// given
		UserRequest request = UserRequest.builder()
			.nickname(null)
			.profileMessage("Only message changed!")
			.profileImageUrl(null)
			.build();

		// when
		UserResponse response = userService.updateProfile(userId, request);

		// then
		assertThat(response.nickname()).isEqualTo("testUser"); // 기존 값 유지
		assertThat(response.profileMessage()).isEqualTo("Only message changed!");
		assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profile.jpg"); // 기존 값 유지

		// DB에서 확인
		User updatedUser = userRepository.findById(userId).orElseThrow();
		assertThat(updatedUser.getNickname()).isEqualTo("testUser");
		assertThat(updatedUser.getProfileMessage()).isEqualTo("Only message changed!");
		assertThat(updatedUser.getProfileImageUrl()).isEqualTo("https://example.com/profile.jpg");
	}

	@Test
	@DisplayName("프로필 업데이트 성공 - 프로필 이미지만 변경")
	public void updateProfile_Success_ProfileImageOnly() {
		// given
		UserRequest request = UserRequest.builder()
			.nickname(null)
			.profileMessage(null)
			.profileImageUrl("https://example.com/new-image.jpg")
			.build();

		// when
		UserResponse response = userService.updateProfile(userId, request);

		// then
		assertThat(response.nickname()).isEqualTo("testUser"); // 기존 값 유지
		assertThat(response.profileMessage()).isEqualTo("Hello, I'm a test user!"); // 기존 값 유지
		assertThat(response.profileImageUrl()).isEqualTo("https://example.com/new-image.jpg");

		// DB에서 확인
		User updatedUser = userRepository.findById(userId).orElseThrow();
		assertThat(updatedUser.getNickname()).isEqualTo("testUser");
		assertThat(updatedUser.getProfileMessage()).isEqualTo("Hello, I'm a test user!");
		assertThat(updatedUser.getProfileImageUrl()).isEqualTo("https://example.com/new-image.jpg");
	}

	@Test
	@DisplayName("프로필 업데이트 실패 - 존재하지 않는 사용자")
	public void updateProfile_Fail_UserNotFound() {
		// given
		Long nonExistentUserId = 999L;
		UserRequest request = UserRequest.builder()
			.nickname("newNickname")
			.build();

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> userService.updateProfile(nonExistentUserId, request));

		assertThat(exception.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
		verify(rankingService, never()).getTotalScoreByUser(anyLong());
	}

	@Test
	@DisplayName("계정 삭제 성공")
	public void deleteAccount_Success() {
		// given
		String accessToken = "test.access.token";
		long tokenExpiration = 3600L;

		when(jwtTokenProvider.getRemainingExpiration(accessToken)).thenReturn(tokenExpiration);
		doNothing().when(redisRepository).saveBlackList(accessToken, tokenExpiration);
		doNothing().when(redisRepository).deleteRefreshToken(String.valueOf(userId));

		// when
		userService.deleteAccount(userId, accessToken);

		// then
		User deletedUser = userRepository.findById(userId).orElseThrow();
		assertThat(deletedUser.isActive()).isFalse();
		assertThat(deletedUser.getRefreshToken()).isNull();

		verify(jwtTokenProvider, times(1)).getRemainingExpiration(accessToken);
		verify(redisRepository, times(1)).saveBlackList(accessToken, tokenExpiration);
		verify(redisRepository, times(1)).deleteRefreshToken(String.valueOf(userId));
	}

	@Test
	@DisplayName("계정 삭제 실패 - 존재하지 않는 사용자")
	public void deleteAccount_Fail_UserNotFound() {
		// given
		Long nonExistentUserId = 999L;
		String accessToken = "test.access.token";

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> userService.deleteAccount(nonExistentUserId, accessToken));

		assertThat(exception.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");

		verify(jwtTokenProvider, never()).getRemainingExpiration(anyString());
		verify(redisRepository, never()).saveBlackList(anyString(), anyLong());
		verify(redisRepository, never()).deleteRefreshToken(anyString());
	}

	@Test
	@DisplayName("총 점수가 0인 사용자의 프로필 조회")
	public void getMyProfile_WithZeroScore() {
		// given
		when(rankingService.getTotalScoreByUser(userId)).thenReturn(0L);

		// when
		UserResponse response = userService.getMyProfile(userId);

		// then
		assertThat(response.totalScore()).isEqualTo(0L);
		verify(rankingService, times(1)).getTotalScoreByUser(userId);
	}

	@Test
	@DisplayName("프로필 업데이트 - 빈 문자열 처리")
	public void updateProfile_WithEmptyStrings() {
		// given
		UserRequest request = UserRequest.builder()
			.nickname("")
			.profileMessage("")
			.profileImageUrl("")
			.build();

		// when
		UserResponse response = userService.updateProfile(userId, request);

		// then
		assertThat(response.nickname()).isEqualTo("");
		assertThat(response.profileMessage()).isEqualTo("");
		assertThat(response.profileImageUrl()).isEqualTo("");

		// DB에서 확인
		User updatedUser = userRepository.findById(userId).orElseThrow();
		assertThat(updatedUser.getNickname()).isEqualTo("");
		assertThat(updatedUser.getProfileMessage()).isEqualTo("");
		assertThat(updatedUser.getProfileImageUrl()).isEqualTo("");
	}
}