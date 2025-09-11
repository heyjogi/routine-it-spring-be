package com.goormi.routine.domain.user.service;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import com.goormi.routine.domain.auth.repository.RedisRepository;
import com.goormi.routine.domain.auth.service.JwtTokenProvider;
import com.goormi.routine.domain.ranking.service.RankingService;
import com.goormi.routine.domain.user.dto.UserRequest;
import com.goormi.routine.domain.user.dto.UserResponse;
import com.goormi.routine.domain.user.entity.User;
import com.goormi.routine.domain.user.repository.UserRepository;

import jakarta.transaction.Transactional;

@SpringBootTest
@ActiveProfiles("ci")
@Transactional
public class UserServiceTest {

	@Autowired
	private UserService userService;
	@Autowired
	private UserRepository userRepository;
	@MockBean
	private RedisRepository redisRepository;
	@MockBean
	private JwtTokenProvider jwtTokenProvider;
	@MockBean
	private RankingService rankingService;

	private User testUser;
	private Long testUserId;

	@BeforeEach
	void setUp() {
		// 테스트용 사용자 생성
		testUser = User.builder()
			.kakaoId("test123")
			.email("test@example.com")
			.nickname("testUser")
			.profileImageUrl("http://example.com/profile.jpg")
			.profileMessage("안녕하세요!")
			.active(true)
			.build();

		userRepository.save(testUser);
		testUserId = testUser.getId();

		// RankingService mock 설정
		when(rankingService.getTotalScoreByUser(testUserId)).thenReturn(100L);
	}

	@Test
	@DisplayName("내 프로필 조회 성공")
	public void getMyProfile_Success() {
		// when
		UserResponse response = userService.getMyProfile(testUserId);

		// then
		assertThat(response).isNotNull();
		assertThat(response.id()).isEqualTo(testUserId);
		assertThat(response.nickname()).isEqualTo("testUser");
		assertThat(response.profileMessage()).isEqualTo("안녕하세요!");
		assertThat(response.profileImageUrl()).isEqualTo("http://example.com/profile.jpg");
		assertThat(response.totalScore()).isEqualTo(100L);
	}

	@Test
	@DisplayName("존재하지 않는 사용자 프로필 조회 실패")
	public void getMyProfile_UserNotFound() {
		// given
		Long nonExistentUserId = 999L;

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> userService.getMyProfile(nonExistentUserId));

		assertThat(exception.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("프로필 업데이트 성공")
	public void updateProfile_Success() {
		// given
		UserRequest request = UserRequest.builder()
			.nickname("updatedUser")
			.profileMessage("업데이트된 메시지")
			.profileImageUrl("http://example.com/new-profile.jpg")
			.build();

		// when
		UserResponse response = userService.updateProfile(testUserId, request);

		// then
		assertThat(response).isNotNull();
		assertThat(response.nickname()).isEqualTo("updatedUser");
		assertThat(response.profileMessage()).isEqualTo("업데이트된 메시지");
		assertThat(response.profileImageUrl()).isEqualTo("http://example.com/new-profile.jpg");

		// DB에서 실제로 업데이트되었는지 확인
		User updatedUser = userRepository.findById(testUserId).orElseThrow();
		assertThat(updatedUser.getNickname()).isEqualTo("updatedUser");
		assertThat(updatedUser.getProfileMessage()).isEqualTo("업데이트된 메시지");
		assertThat(updatedUser.getProfileImageUrl()).isEqualTo("http://example.com/new-profile.jpg");
	}

	@Test
	@DisplayName("부분적 프로필 업데이트 성공")
	public void updateProfile_Partial_Success() {
		// given - 닉네임만 업데이트
		UserRequest request = UserRequest.builder()
			.nickname("partialUpdate")
			.build();

		// when
		UserResponse response = userService.updateProfile(testUserId, request);

		// then
		assertThat(response.nickname()).isEqualTo("partialUpdate");
		// 기존 값 유지
		assertThat(response.profileMessage()).isEqualTo("안녕하세요!");
		assertThat(response.profileImageUrl()).isEqualTo("http://example.com/profile.jpg");
	}

	@Test
	@DisplayName("다른 사용자 프로필 조회 성공")
	public void getUserProfile_Success() {
		// when
		UserResponse response = userService.getUserProfile(testUserId);

		// then
		assertThat(response).isNotNull();
		assertThat(response.id()).isEqualTo(testUserId);
		assertThat(response.nickname()).isEqualTo("testUser");
		assertThat(response.totalScore()).isEqualTo(100L);
	}

	@Test
	@DisplayName("계정 삭제 성공")
	public void deleteAccount_Success() {
		// given
		String accessToken = "test-access-token";
		when(jwtTokenProvider.getRemainingExpiration(accessToken)).thenReturn(3600L);

		// when
		userService.deleteAccount(testUserId, accessToken);

		// then
		User deletedUser = userRepository.findById(testUserId).orElseThrow();
		assertThat(deletedUser.isActive()).isFalse();
		assertThat(deletedUser.getRefreshToken()).isNull();

		// Redis 관련 메서드 호출 확인
		verify(redisRepository).saveBlackList(accessToken, 3600L);
		verify(redisRepository).deleteRefreshToken(String.valueOf(testUserId));
	}

	@Test
	@DisplayName("존재하지 않는 사용자 계정 삭제 실패")
	public void deleteAccount_UserNotFound() {
		// given
		Long nonExistentUserId = 999L;
		String accessToken = "test-access-token";

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> userService.deleteAccount(nonExistentUserId, accessToken));

		assertThat(exception.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
	}
}