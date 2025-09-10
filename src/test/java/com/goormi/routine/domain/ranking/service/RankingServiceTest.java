package com.goormi.routine.domain.ranking.service;

import com.goormi.routine.domain.group.repository.GroupMemberRepository;
import com.goormi.routine.domain.ranking.entity.Ranking;
import com.goormi.routine.domain.ranking.repository.RankingRedisRepository;
import com.goormi.routine.domain.ranking.repository.RankingRepository;
import com.goormi.routine.domain.user.entity.User;
import com.goormi.routine.domain.user.repository.UserRepository;
import com.goormi.routine.domain.userActivity.repository.UserActivityRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("ci")
@Transactional
public class RankingServiceTest {

	@Autowired
	private RankingService rankingService;
	@Autowired
	private RankingRepository rankingRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private GroupMemberRepository groupMemberRepository;
	@Autowired
	private RankingRedisRepository rankingRedisRepository;
	@Autowired
	private UserActivityRepository userActivityRepository;

	private User testUser;
	private Long testUserId;
	private Long testGroupId;

	@BeforeEach
	void setUp() {
		// 테스트용 사용자 생성
		testUser = User.builder()
			.kakaoId("ranking_test")
			.email("ranking@test.com")
			.nickname("rankingUser")
			.active(true)
			.build();
		userRepository.save(testUser);
		testUserId = testUser.getId();
		testGroupId = 1L;

		// UserActivityRepository mock 설정
		when(userActivityRepository.findByUserIdAndActivityTypeOrderByCreatedAtDesc(any(), any()))
			.thenReturn(java.util.Collections.emptyList());
		when(userActivityRepository.countByUserIdAndActivityTypeAndCreatedAtBetween(any(), any(), any(), any()))
			.thenReturn(0L);
	}

	@Test
	@DisplayName("랭킹 초기화 성공")
	public void initializeRanking_Success() {
		// when
		rankingService.initializeRanking(testUserId, testGroupId);

		// then
		Optional<Ranking> ranking = rankingRepository.findByUserIdAndGroupId(testUserId, testGroupId);
		assertThat(ranking).isPresent();
		assertThat(ranking.get().getUserId()).isEqualTo(testUserId);
		assertThat(ranking.get().getGroupId()).isEqualTo(testGroupId);
		assertThat(ranking.get().getScore()).isEqualTo(0);

		String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
		assertThat(ranking.get().getMonthYear()).isEqualTo(currentMonth);
	}

	@Test
	@DisplayName("중복 랭킹 초기화 방지")
	public void initializeRanking_AlreadyExists() {
		// given - 랭킹 초기화
		rankingService.initializeRanking(testUserId, testGroupId);
		int initialCount = rankingRepository.findAll().size();

		// when - 같은 사용자, 그룹으로 다시 초기화 시도
		rankingService.initializeRanking(testUserId, testGroupId);

		// then - 랭킹이 중복 생성되지 않아야 함
		int finalCount = rankingRepository.findAll().size();
		assertThat(finalCount).isEqualTo(initialCount);
	}

	@Test
	@DisplayName("사용자 ID null로 랭킹 초기화 실패")
	public void initializeRanking_NullUserId() {
		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> rankingService.initializeRanking(null, testGroupId));

		assertThat(exception.getMessage()).isEqualTo("사용자 ID는 필수입니다.");
	}

	@Test
	@DisplayName("랭킹 점수 업데이트 성공")
	public void updateRankingScore_Success() {
		// given
		rankingService.initializeRanking(testUserId, testGroupId);
		int authCount = 5;

		// when
		rankingService.updateRankingScore(testUserId, testGroupId, authCount);

		// then
		Optional<Ranking> ranking = rankingRepository.findByUserIdAndGroupId(testUserId, testGroupId);
		assertThat(ranking).isPresent();
		// 기본 점수는 authCount * 10 = 50점
		assertThat(ranking.get().getScore()).isGreaterThanOrEqualTo(50);
	}

	@Test
	@DisplayName("잘못된 인증 횟수로 랭킹 점수 업데이트 실패")
	public void updateRankingScore_InvalidAuthCount() {
		// given
		rankingService.initializeRanking(testUserId, testGroupId);

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> rankingService.updateRankingScore(testUserId, testGroupId, -1));

		assertThat(exception.getMessage()).isEqualTo("인증 횟수는 0 이상이어야 합니다.");
	}

	@Test
	@DisplayName("null 사용자 ID로 랭킹 점수 업데이트 실패")
	public void updateRankingScore_NullUserId() {
		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
		() -> rankingService.updateRankingScore(null, testGroupId, 5));

		assertThat(exception.getMessage()).isEqualTo("사용자 ID는 필수입니다.");
	}

	@Test
	@DisplayName("null 그룹 ID로 랭킹 점수 업데이트 실패")
	public void updateRankingScore_NullGroupId() {
		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> rankingService.updateRankingScore(testUserId, null, 5));

		assertThat(exception.getMessage()).isEqualTo("그룹 ID는 필수입니다.");
	}

	@Test
	@DisplayName("사용자 총 점수 조회 성공")
	public void getTotalScoreByUser_Success() {
		// given
		// 첫 번째 그룹 랭킹 생성
		rankingService.initializeRanking(testUserId, testGroupId);
		rankingService.updateGroupScore(testUserId, testGroupId, 100);

		// 두 번째 그룹 랭킹 생성
		Long secondGroupId = 2L;
		rankingService.initializeRanking(testUserId, secondGroupId);
		rankingService.updateGroupScore(testUserId, secondGroupId, 150);

		// when
		long totalScore = rankingService.getTotalScoreByUser(testUserId);

		// then
		assertThat(totalScore).isEqualTo(250L);
	}

	@Test
	@DisplayName("랭킹이 없는 사용자의 총 점수 조회")
	public void getTotalScoreByUser_NoRankings() {
		// when
		long totalScore = rankingService.getTotalScoreByUser(testUserId);

		// then
		assertThat(totalScore).isEqualTo(0L);
	}

	@Test
	@DisplayName("null 사용자 ID로 총 점수 조회 실패")
	public void getTotalScoreByUser_NullUserId() {
		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> rankingService.getTotalScoreByUser(null));

		assertThat(exception.getMessage()).isEqualTo("사용자 ID는 필수입니다.");
	}

	@Test
	@DisplayName("그룹 점수 업데이트 성공")
	public void updateGroupScore_Success() {
		// given
		rankingService.initializeRanking(testUserId, testGroupId);
		int scoreToAdd = 50;

		// when
		rankingService.updateGroupScore(testUserId, testGroupId, scoreToAdd);

		// then
		Optional<Ranking> ranking = rankingRepository.findByUserIdAndGroupId(testUserId, testGroupId);
		assertThat(ranking).isPresent();
		assertThat(ranking.get().getScore()).isEqualTo(scoreToAdd);
	}

	@Test
	@DisplayName("존재하지 않는 랭킹에 점수 업데이트 시 자동 초기화")
	public void updateGroupScore_AutoInitialize() {
		// given - 랭킹이 초기화되지 않은 상태
		int scoreToAdd = 30;

		// when
		rankingService.updateGroupScore(testUserId, testGroupId, scoreToAdd);

		// then
		Optional<Ranking> ranking = rankingRepository.findByUserIdAndGroupId(testUserId, testGroupId);
		assertThat(ranking).isPresent();
		assertThat(ranking.get().getScore()).isEqualTo(scoreToAdd);
	}

	@Test
	@DisplayName("월별 랭킹 리셋 성공")
	public void resetMonthlyRankings_Success() {
		// given
		rankingService.initializeRanking(testUserId, testGroupId);
		rankingService.updateGroupScore(testUserId, testGroupId, 100);

		// 초기 점수 확인
		Optional<Ranking> rankingBefore = rankingRepository.findByUserIdAndGroupId(testUserId, testGroupId);
		assertThat(rankingBefore).isPresent();
		assertThat(rankingBefore.get().getScore()).isEqualTo(100);

		// when
		rankingService.resetMonthlyRankings();

		// then
		Optional<Ranking> rankingAfter = rankingRepository.findByUserIdAndGroupId(testUserId, testGroupId);
		assertThat(rankingAfter).isPresent();
		assertThat(rankingAfter.get().getScore()).isEqualTo(0);

		String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
		assertThat(rankingAfter.get().getMonthYear()).isEqualTo(currentMonth);

		// Redis 저장 확인
		verify(rankingRedisRepository).saveLastResetMonth(currentMonth);
	}
}