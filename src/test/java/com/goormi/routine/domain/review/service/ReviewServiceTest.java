package com.goormi.routine.domain.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.goormi.routine.domain.group.repository.GroupMemberRepository;
import com.goormi.routine.domain.notification.entity.NotificationType;
import com.goormi.routine.domain.notification.service.NotificationService;
import com.goormi.routine.domain.ranking.service.RankingService;
import com.goormi.routine.domain.review.dto.MonthlyReviewResponse;
import com.goormi.routine.domain.review.repository.ReviewRedisRepository;
import com.goormi.routine.domain.user.entity.User;
import com.goormi.routine.domain.user.repository.UserRepository;
import com.goormi.routine.domain.userActivity.entity.ActivityType;
import com.goormi.routine.domain.userActivity.entity.UserActivity;
import com.goormi.routine.domain.userActivity.repository.UserActivityRepository;
import com.goormi.routine.domain.personal_routines.domain.PersonalRoutine;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("ci")
@Transactional
public class ReviewServiceTest {

	@Autowired
	private ReviewService reviewService;

	@Autowired
	private UserRepository userRepository;

	@MockBean
	private RankingService rankingService;

	@MockBean
	private NotificationService notificationService;

	@MockBean
	private GroupMemberRepository groupMemberRepository;

	@MockBean
	private ReviewRedisRepository reviewRedisRepository;

	@MockBean
	private UserActivityRepository userActivityRepository;

	@MockBean
	private ObjectMapper objectMapper;

	private Long userId;
	private User testUser;
	private String testMonthYear = "2024-01";

	@BeforeEach
	void setUp() {
		// 테스트용 사용자 생성
		testUser = User.builder()
			.kakaoId("testKakaoId")
			.email("test@kakao.com")
			.nickname("testUser")
			.build();
		userRepository.save(testUser);
		userId = testUser.getId();
		ObjectReader mockReader = Mockito.mock(ObjectReader.class);

		// 기본 모킹 설정
		when(rankingService.getTotalScoreByUser(anyLong())).thenReturn(1500L);
		when(groupMemberRepository.findActiveGroupsByUserId(anyLong())).thenReturn(Arrays.asList());
		when(userActivityRepository.findByUserIdAndActivityTypeAndActivityDateBetween(
			anyLong(), any(ActivityType.class), any(LocalDate.class), any(LocalDate.class)))
			.thenReturn(Arrays.asList());
		doNothing().when(notificationService).createNotification(
			any(NotificationType.class), anyLong(), anyLong(), anyLong());
		when(objectMapper.reader()).thenReturn(mockReader);
		when(mockReader.forType(any(Class.class))).thenReturn(mockReader);
	}

	@Test
	@DisplayName("월간 회고 조회 성공 - Redis에서 기존 데이터 조회")
	public void getMonthlyReview_Success_FromRedis() throws Exception {
		// given
		MonthlyReviewResponse savedReview = MonthlyReviewResponse.builder()
			.userId(userId)
			.nickname("testUser")
			.monthYear(testMonthYear)
			.totalScore(1500)
			.participatingGroups(2)
			.personalRoutineAchievementRate(85)
			.build();

		String jsonData = "{\"userId\":" + userId + ",\"nickname\":\"testUser\",\"monthYear\":\"" + testMonthYear + "\",\"totalScore\":1500}";
		when(reviewRedisRepository.getReviewData(userId.toString(), testMonthYear)).thenReturn(jsonData);
		when(objectMapper.readValue(jsonData, MonthlyReviewResponse.class)).thenReturn(savedReview);

		// when
		MonthlyReviewResponse response = reviewService.getMonthlyReview(userId, testMonthYear);

		// then
		assertThat(response).isNotNull();
		assertThat(response.getUserId()).isEqualTo(userId);
		assertThat(response.getNickname()).isEqualTo("testUser");
		assertThat(response.getMonthYear()).isEqualTo(testMonthYear);
		assertThat(response.getTotalScore()).isEqualTo(1500);

		verify(reviewRedisRepository, times(1)).getReviewData(userId.toString(), testMonthYear);
		verify(objectMapper, times(1)).readValue(jsonData, MonthlyReviewResponse.class);
	}

	@Test
	@DisplayName("월간 회고 조회 성공 - Redis에 데이터가 없어 새로 계산")
	public void getMonthlyReview_Success_CalculateNew() {
		// given
		when(reviewRedisRepository.getReviewData(userId.toString(), testMonthYear)).thenReturn(null);

		// when
		MonthlyReviewResponse response = reviewService.getMonthlyReview(userId, testMonthYear);

		// then
		assertThat(response).isNotNull();
		assertThat(response.getUserId()).isEqualTo(userId);
		assertThat(response.getNickname()).isEqualTo("testUser");
		assertThat(response.getMonthYear()).isEqualTo(testMonthYear);
		assertThat(response.getTotalScore()).isEqualTo(1500);

		verify(reviewRedisRepository, times(1)).getReviewData(userId.toString(), testMonthYear);
		verify(rankingService, times(1)).getTotalScoreByUser(userId);
		verify(groupMemberRepository, times(1)).findActiveGroupsByUserId(userId);
	}

	@Test
	@DisplayName("월간 회고 조회 실패 - null 사용자 ID")
	public void getMonthlyReview_Fail_NullUserId() {
		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> reviewService.getMonthlyReview(null, testMonthYear));

		assertThat(exception.getMessage()).isEqualTo("사용자 ID는 필수입니다.");

		verify(reviewRedisRepository, never()).getReviewData(anyString(), anyString());
		verify(rankingService, never()).getTotalScoreByUser(anyLong());
	}

	@Test
	@DisplayName("월간 회고 조회 실패 - 존재하지 않는 사용자 (새로 계산시)")
	public void getMonthlyReview_Fail_UserNotFound() {
		// given
		Long nonExistentUserId = 999L;
		when(reviewRedisRepository.getReviewData(nonExistentUserId.toString(), testMonthYear)).thenReturn(null);

		// when & then
		RuntimeException exception = assertThrows(RuntimeException.class,
			() -> reviewService.getMonthlyReview(nonExistentUserId, testMonthYear));

		assertThat(exception.getMessage()).isEqualTo("회고 계산 중 오류가 발생했습니다.");
		assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
		assertThat(exception.getCause().getMessage()).contains("사용자를 찾을 수 없습니다");
	}

	@Test
	@DisplayName("사용자 회고 메시지 전송 성공")
	public void sendUserReviewMessage_Success() throws Exception {
		// given
		String jsonData = "{\"userId\":" + userId + "}";
		when(objectMapper.writeValueAsString(any(MonthlyReviewResponse.class))).thenReturn(jsonData);
		doNothing().when(reviewRedisRepository).saveReviewData(anyString(), anyString(), anyString());

		// when
		reviewService.sendUserReviewMessage(userId, testMonthYear);

		// then
		verify(rankingService, times(1)).getTotalScoreByUser(userId);
		verify(groupMemberRepository, times(1)).findActiveGroupsByUserId(userId);
		verify(objectMapper, times(1)).writeValueAsString(any(MonthlyReviewResponse.class));
		verify(reviewRedisRepository, times(1)).saveReviewData(eq(userId.toString()), eq(testMonthYear), eq(jsonData));
		verify(notificationService, times(1)).createNotification(
			eq(NotificationType.MONTHLY_REVIEW), isNull(), eq(userId), isNull());
	}

	@Test
	@DisplayName("사용자 회고 메시지 전송 실패 - null 사용자 ID")
	public void sendUserReviewMessage_Fail_NullUserId() {
		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> reviewService.sendUserReviewMessage(null, testMonthYear));

		assertThat(exception.getMessage()).isEqualTo("사용자 ID는 필수입니다.");

		verify(rankingService, never()).getTotalScoreByUser(anyLong());
		verify(notificationService, never()).createNotification(any(), any(), any(), any());
	}

	@Test
	@DisplayName("월간 회고 메시지 전송 성공 - 모든 사용자")
	public void sendMonthlyReviewMessages_Success() throws Exception {
		// given
		User user2 = User.builder()
			.kakaoId("testKakaoId2")
			.email("test2@kakao.com")
			.nickname("testUser2")
			.build();
		userRepository.save(user2);

		when(objectMapper.writeValueAsString(any(MonthlyReviewResponse.class))).thenReturn("{\"userId\":1}");
		doNothing().when(reviewRedisRepository).saveReviewData(anyString(), anyString(), anyString());

		// when
		reviewService.sendMonthlyReviewMessages(testMonthYear);

		// then
		verify(rankingService, times(2)).getTotalScoreByUser(anyLong());
		verify(notificationService, times(2)).createNotification(
			eq(NotificationType.MONTHLY_REVIEW), isNull(), anyLong(), isNull());
	}

	@Test
	@DisplayName("월간 회고 메시지 전송 부분 실패")
	public void sendMonthlyReviewMessages_PartialFailure() throws Exception {
		// given
		User user2 = User.builder()
			.kakaoId("testKakaoId2")
			.email("test2@kakao.com")
			.nickname("testUser2")
			.build();
		userRepository.save(user2);

		when(objectMapper.writeValueAsString(any(MonthlyReviewResponse.class)))
			.thenReturn("{\"userId\":1}")  // 첫 번째 사용자 성공
			.thenThrow(new RuntimeException("JSON 변환 실패")); // 두 번째 사용자 실패

		doNothing().when(reviewRedisRepository).saveReviewData(anyString(), anyString(), anyString());
		doNothing().when(reviewRedisRepository).saveFailedMessage(anyLong(), anyString(), anyString());

		// when & then
		RuntimeException exception = assertThrows(RuntimeException.class,
			() -> reviewService.sendMonthlyReviewMessages(testMonthYear));

		assertThat(exception.getMessage()).contains("일부 메시지 전송 실패");
		assertThat(exception.getMessage()).contains("성공 1건, 실패 1건");

		verify(reviewRedisRepository, times(1)).saveFailedMessage(anyLong(), eq(testMonthYear), anyString());
	}

	@Test
	@DisplayName("실패 메시지 재전송 성공")
	public void retryFailedMessages_Success() throws Exception {
		// given
		List<Long> failedUserIds = Arrays.asList(userId, 999L);
		when(reviewRedisRepository.getFailedUserIds(testMonthYear)).thenReturn(failedUserIds);
		when(objectMapper.writeValueAsString(any(MonthlyReviewResponse.class))).thenReturn("{\"userId\":1}");
		doNothing().when(reviewRedisRepository).saveReviewData(anyString(), anyString(), anyString());
		doNothing().when(reviewRedisRepository).removeFailedMessage(anyLong(), anyString());

		// when
		reviewService.retryFailedMessages(testMonthYear);

		// then
		verify(reviewRedisRepository, times(1)).getFailedUserIds(testMonthYear);
		// 첫 번째 사용자는 성공하고 removeFailedMessage가 호출됨
		verify(reviewRedisRepository, times(1)).removeFailedMessage(userId, testMonthYear);
		// 두 번째 사용자(999L)는 존재하지 않아서 removeFailedMessage가 호출되지 않음
	}

	@Test
	@DisplayName("실패 메시지 재전송 - 재전송할 메시지 없음")
	public void retryFailedMessages_NoFailedMessages() {
		// given
		when(reviewRedisRepository.getFailedUserIds(testMonthYear)).thenReturn(Arrays.asList());

		// when
		reviewService.retryFailedMessages(testMonthYear);

		// then
		verify(reviewRedisRepository, times(1)).getFailedUserIds(testMonthYear);
		verify(reviewRedisRepository, never()).removeFailedMessage(anyLong(), anyString());
		verify(notificationService, never()).createNotification(any(), any(), any(), any());
	}

	@Test
	@DisplayName("실패 메시지 수 조회")
	public void getFailedMessageCount() {
		// given
		when(reviewRedisRepository.getFailedMessageCount(testMonthYear)).thenReturn(3);

		// when
		int count = reviewService.getFailedMessageCount(testMonthYear);

		// then
		assertThat(count).isEqualTo(3);
		verify(reviewRedisRepository, times(1)).getFailedMessageCount(testMonthYear);
	}

	@Test
	@DisplayName("개인 루틴 성취율 계산 - 활동이 있는 경우")
	public void calculatePersonalRoutineAchievementRate_WithActivities() {
		// given
		PersonalRoutine routine = PersonalRoutine.builder()
			.routineId(1)
			.userId(userId.intValue())
			.routineName("테스트 루틴")
			.repeatDays("1010100") // 월/수/금
			.startTime(LocalTime.of(9, 0))
			.startDate(LocalDate.of(2024, 1, 1))
			.endDate(LocalDate.of(2024, 1, 31))
			.build();

		UserActivity activity1 = UserActivity.builder()
			.user(testUser)
			.personalRoutine(routine)
			.activityType(ActivityType.PERSONAL_ROUTINE_COMPLETE)
			.activityDate(LocalDate.of(2024, 1, 1))
			.createdAt(LocalDateTime.now())
			.build();

		UserActivity activity2 = UserActivity.builder()
			.user(testUser)
			.personalRoutine(routine)
			.activityType(ActivityType.PERSONAL_ROUTINE_COMPLETE)
			.activityDate(LocalDate.of(2024, 1, 3))
			.createdAt(LocalDateTime.now())
			.build();

		when(userActivityRepository.findByUserIdAndActivityTypeAndActivityDateBetween(
			eq(userId), eq(ActivityType.PERSONAL_ROUTINE_COMPLETE), any(LocalDate.class), any(LocalDate.class)))
			.thenReturn(Arrays.asList(activity1, activity2));

		// when
		MonthlyReviewResponse response = reviewService.getMonthlyReview(userId, testMonthYear);

		// then
		assertThat(response.getPersonalRoutineAchievementRate()).isNotNull();
		// 1월에 월/수/금은 총 13회, 실제 2회 완료 시 약 15% 달성률
		verify(userActivityRepository, times(1)).findByUserIdAndActivityTypeAndActivityDateBetween(
			eq(userId), eq(ActivityType.PERSONAL_ROUTINE_COMPLETE), any(LocalDate.class), any(LocalDate.class));
	}

	@Test
	@DisplayName("개인 루틴 성취율 계산 - 활동이 없는 경우")
	public void calculatePersonalRoutineAchievementRate_NoActivities() {
		// given
		when(userActivityRepository.findByUserIdAndActivityTypeAndActivityDateBetween(
			eq(userId), eq(ActivityType.PERSONAL_ROUTINE_COMPLETE), any(LocalDate.class), any(LocalDate.class)))
			.thenReturn(Arrays.asList());

		// when
		MonthlyReviewResponse response = reviewService.getMonthlyReview(userId, testMonthYear);

		// then
		assertThat(response.getPersonalRoutineAchievementRate()).isEqualTo(0);
		verify(userActivityRepository, times(1)).findByUserIdAndActivityTypeAndActivityDateBetween(
			eq(userId), eq(ActivityType.PERSONAL_ROUTINE_COMPLETE), any(LocalDate.class), any(LocalDate.class));
	}

	@Test
	@DisplayName("현재 달 기준으로 전월 회고 메시지 전송")
	public void sendMonthlyReviewMessages_DefaultToPreviousMonth() throws Exception {
		// given
		doNothing().when(reviewRedisRepository).saveReviewData(anyString(), anyString(), anyString());

		String expectedMonthYear = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));

		// when
		reviewService.sendMonthlyReviewMessages(null);

		// then
		verify(notificationService, times(1)).createNotification(
			eq(NotificationType.MONTHLY_REVIEW), isNull(), eq(userId), isNull());
		verify(reviewRedisRepository, times(1)).saveReviewData(
			eq(userId.toString()), eq(expectedMonthYear), anyString());
	}

	@Test
	@DisplayName("실패 메시지 수 조회 - null monthYear인 경우 현재 월 사용")
	public void getFailedMessageCount_NullMonthYear() {
		// given
		String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
		when(reviewRedisRepository.getFailedMessageCount(currentMonth)).thenReturn(2);

		// when
		int count = reviewService.getFailedMessageCount(null);

		// then
		assertThat(count).isEqualTo(2);
		verify(reviewRedisRepository, times(1)).getFailedMessageCount(currentMonth);
	}
}