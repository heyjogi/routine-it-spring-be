package com.goormi.routine.domain.settings.service;

import com.goormi.routine.domain.settings.dto.SettingsResponse;
import com.goormi.routine.domain.settings.entity.UserSettings;
import com.goormi.routine.domain.settings.repository.UserSettingsRepository;
import com.goormi.routine.domain.user.entity.User;
import com.goormi.routine.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("ci")
@Transactional
public class SettingsServiceTest {

	@Autowired
	private SettingsService settingsService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private UserSettingsRepository userSettingsRepository;

	private User testUser;
	private Long testUserId;

	@BeforeEach
	void setUp() {
		// 테스트용 사용자 생성
		testUser = userRepository.saveAndFlush(
			User.builder()
				.kakaoId("settings_test")
				.email("settings@test.com")
				.nickname("settingsUser")
				.active(true)
				.build()
		);
		testUserId = testUser.getId();

		UserSettings settings = UserSettings.builder()
			.userId(testUserId)
			.isAlarmOn(true) // 기본값
			.isDarkMode(false) // 기본값
			.build();
	}

	@Test
	@DisplayName("설정 조회 성공 - 기본 설정 자동 생성")
	public void getSettings_Success_AutoCreate() {
		// when
		SettingsResponse response = settingsService.getSettings(testUserId);

		// then
		assertThat(response).isNotNull();
		assertThat(response.userId()).isEqualTo(testUserId);
		assertThat(response.isAlarmOn()).isTrue(); // 기본값
		assertThat(response.isDarkMode()).isFalse(); // 기본값

		// DB에 실제로 생성되었는지 확인
		Optional<UserSettings> settings = userSettingsRepository.findByUserId(testUserId);
		assertThat(settings).isPresent();
	}

	@Test
	@DisplayName("설정 조회 성공 - 기존 설정 존재")
	public void getSettings_Success_ExistingSettings() {
		// given
		UserSettings existingSettings = UserSettings.builder()
			.userId(testUserId)
			.isAlarmOn(false)
			.isDarkMode(true)
			.build();
		userSettingsRepository.save(existingSettings);

		// when
		SettingsResponse response = settingsService.getSettings(testUserId);

		// then
		assertThat(response.userId()).isEqualTo(testUserId);
		assertThat(response.isAlarmOn()).isFalse();
		assertThat(response.isDarkMode()).isTrue();
	}

	@Test
	@DisplayName("존재하지 않는 사용자 설정 조회 실패")
	public void getSettings_UserNotFound() {
		// given
		Long nonExistentUserId = 999L;

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.getSettings(nonExistentUserId));

		assertThat(exception.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("비활성화된 사용자 설정 조회 실패")
	public void getSettings_InactiveUser() {
		// given
		testUser.setActive(false);
		userRepository.save(testUser);

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.getSettings(testUserId));

		assertThat(exception.getMessage()).isEqualTo("비활성화된 사용자입니다.");
	}

	@Test
	@DisplayName("null 사용자 ID로 설정 조회 실패")
	public void getSettings_NullUserId() {
		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.getSettings(null));

		assertThat(exception.getMessage()).isEqualTo("사용자 ID는 null일 수 없습니다.");
	}

	@Test
	@DisplayName("알림 설정 업데이트 성공")
	public void updateAlarmSetting_Success() {
		// when
		Boolean result = settingsService.updateAlarmSetting(testUserId, false);

		// then
		assertThat(result).isFalse();

		// DB에서 확인
		UserSettings settings = userSettingsRepository.findByUserId(testUserId).orElseThrow();
		assertThat(settings.getIsAlarmOn()).isFalse();
	}

	@Test
	@DisplayName("null 알림 설정값으로 업데이트 실패")
	public void updateAlarmSetting_NullValue() {
		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.updateAlarmSetting(testUserId, null));

		assertThat(exception.getMessage()).isEqualTo("알림 설정 값은 null일 수 없습니다.");
	}

	@Test
	@DisplayName("다크모드 설정 업데이트 성공")
	public void updateDarkModeSetting_Success() {
		// when
		Boolean result = settingsService.updateDarkModeSetting(testUserId, true);

		// then
		assertThat(result).isTrue();

		// DB에서 확인
		UserSettings settings = userSettingsRepository.findByUserId(testUserId).orElseThrow();
		assertThat(settings.getIsDarkMode()).isTrue();
	}

	@Test
	@DisplayName("null 다크모드 설정값으로 업데이트 실패")
	public void updateDarkModeSetting_NullValue() {
		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.updateDarkModeSetting(testUserId, null));

		assertThat(exception.getMessage()).isEqualTo("다크모드 설정 값은 null일 수 없습니다.");
	}

	@Test
	@DisplayName("설정 리셋 성공")
	public void resetSettings_Success() {
		// given - 기존 설정 변경
		settingsService.updateAlarmSetting(testUserId, false);
		settingsService.updateDarkModeSetting(testUserId, true);

		// when
		SettingsResponse response = settingsService.resetSettings(testUserId);

		// then
		assertThat(response.isAlarmOn()).isTrue(); // 기본값으로 리셋
		assertThat(response.isDarkMode()).isFalse(); // 기본값으로 리셋

		// DB에서 확인
		UserSettings settings = userSettingsRepository.findByUserId(testUserId).orElseThrow();
		assertThat(settings.getIsAlarmOn()).isTrue();
		assertThat(settings.getIsDarkMode()).isFalse();
	}

	@Test
	@DisplayName("존재하지 않는 사용자 설정 리셋 실패")
	public void resetSettings_UserNotFound() {
		// given
		Long nonExistentUserId = 999L;

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.resetSettings(nonExistentUserId));

		assertThat(exception.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("설정이 없는 사용자의 알림 설정 업데이트 - 자동 생성")
	public void updateAlarmSetting_AutoCreateSettings() {
		// given - 설정이 없는 상태에서 시작
		Optional<UserSettings> beforeSettings = userSettingsRepository.findByUserId(testUserId);
		assertThat(beforeSettings).isEmpty();

		// when
		Boolean result = settingsService.updateAlarmSetting(testUserId, false);

		// then
		assertThat(result).isFalse();

		// 설정이 자동으로 생성되었는지 확인
		UserSettings settings = userSettingsRepository.findByUserId(testUserId).orElseThrow();
		assertThat(settings.getIsAlarmOn()).isFalse();
		assertThat(settings.getIsDarkMode()).isFalse(); // 기본값
	}

	@Test
	@DisplayName("설정이 없는 사용자의 다크모드 설정 업데이트 - 자동 생성")
	public void updateDarkModeSetting_AutoCreateSettings() {
		// given - 설정이 없는 상태에서 시작
		Optional<UserSettings> beforeSettings = userSettingsRepository.findByUserId(testUserId);
		assertThat(beforeSettings).isEmpty();

		// when
		Boolean result = settingsService.updateDarkModeSetting(testUserId, true);

		// then
		assertThat(result).isTrue();

		// 설정이 자동으로 생성되었는지 확인
		UserSettings settings = userSettingsRepository.findByUserId(testUserId).orElseThrow();
		assertThat(settings.getIsDarkMode()).isTrue();
		assertThat(settings.getIsAlarmOn()).isTrue(); // 기본값
	}
}