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
	private UserSettingsRepository userSettingsRepository;

	@Autowired
	private UserRepository userRepository;

	private Long userId;
	private User testUser;

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
	}

	@Test
	@DisplayName("설정 조회 - 기존 설정이 있는 경우")
	public void getSettings_WithExistingSettings() {
		// given
		UserSettings existingSettings = UserSettings.builder()
			.userId(userId)
			.isAlarmOn(false)
			.isDarkMode(true)
			.build();
		userSettingsRepository.save(existingSettings);

		// when
		SettingsResponse response = settingsService.getSettings(userId);

		// then
		assertThat(response).isNotNull();
		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.isAlarmOn()).isFalse();
		assertThat(response.isDarkMode()).isTrue();
	}

	@Test
	@DisplayName("설정 조회 - 설정이 없어 기본값으로 생성하는 경우")
	public void getSettings_CreateDefaultSettings() {
		// when
		SettingsResponse response = settingsService.getSettings(userId);

		// then
		assertThat(response).isNotNull();
		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.isAlarmOn()).isTrue(); // 기본값
		assertThat(response.isDarkMode()).isFalse(); // 기본값

		// DB에 설정이 생성되었는지 확인
		Optional<UserSettings> savedSettings = userSettingsRepository.findByUserId(userId);
		assertThat(savedSettings).isPresent();
		assertThat(savedSettings.get().getIsAlarmOn()).isTrue();
		assertThat(savedSettings.get().getIsDarkMode()).isFalse();
	}

	@Test
	@DisplayName("설정 조회 실패 - 존재하지 않는 사용자")
	public void getSettings_Fail_UserNotFound() {
		// given
		Long nonExistentUserId = 999L;

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.getSettings(nonExistentUserId));

		assertThat(exception.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("설정 조회 실패 - null 사용자 ID")
	public void getSettings_Fail_NullUserId() {
		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.getSettings(null));

		assertThat(exception.getMessage()).isEqualTo("사용자 ID는 null일 수 없습니다.");
	}

	@Test
	@DisplayName("설정 조회 실패 - 비활성화된 사용자")
	public void getSettings_Fail_InactiveUser() {
		// given
		testUser.setActive(false);
		userRepository.save(testUser);

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.getSettings(userId));

		assertThat(exception.getMessage()).isEqualTo("비활성화된 사용자입니다.");
	}

	@Test
	@DisplayName("설정 초기화")
	public void resetSettings() {
		// given
		UserSettings existingSettings = UserSettings.builder()
			.userId(userId)
			.isAlarmOn(false)
			.isDarkMode(true)
			.build();
		userSettingsRepository.save(existingSettings);

		// when
		SettingsResponse response = settingsService.resetSettings(userId);

		// then
		assertThat(response).isNotNull();
		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.isAlarmOn()).isTrue(); // 초기화됨
		assertThat(response.isDarkMode()).isFalse(); // 초기화됨

		// DB에서 확인
		Optional<UserSettings> updatedSettings = userSettingsRepository.findByUserId(userId);
		assertThat(updatedSettings).isPresent();
		assertThat(updatedSettings.get().getIsAlarmOn()).isTrue();
		assertThat(updatedSettings.get().getIsDarkMode()).isFalse();
	}

	@Test
	@DisplayName("설정 초기화 실패 - 존재하지 않는 사용자")
	public void resetSettings_Fail_UserNotFound() {
		// given
		Long nonExistentUserId = 999L;

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.resetSettings(nonExistentUserId));

		assertThat(exception.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("알림 설정 업데이트 - true로 변경")
	public void updateAlarmSetting_ToTrue() {
		// given
		UserSettings existingSettings = UserSettings.builder()
			.userId(userId)
			.isAlarmOn(false)
			.isDarkMode(false)
			.build();
		userSettingsRepository.save(existingSettings);

		// when
		Boolean result = settingsService.updateAlarmSetting(userId, true);

		// then
		assertThat(result).isTrue();

		// DB에서 확인
		Optional<UserSettings> updatedSettings = userSettingsRepository.findByUserId(userId);
		assertThat(updatedSettings).isPresent();
		assertThat(updatedSettings.get().getIsAlarmOn()).isTrue();
		assertThat(updatedSettings.get().getIsDarkMode()).isFalse(); // 다른 설정은 변경되지 않음
	}

	@Test
	@DisplayName("알림 설정 업데이트 - false로 변경")
	public void updateAlarmSetting_ToFalse() {
		// given
		UserSettings existingSettings = UserSettings.builder()
			.userId(userId)
			.isAlarmOn(true)
			.isDarkMode(false)
			.build();
		userSettingsRepository.save(existingSettings);

		// when
		Boolean result = settingsService.updateAlarmSetting(userId, false);

		// then
		assertThat(result).isFalse();

		// DB에서 확인
		Optional<UserSettings> updatedSettings = userSettingsRepository.findByUserId(userId);
		assertThat(updatedSettings).isPresent();
		assertThat(updatedSettings.get().getIsAlarmOn()).isFalse();
	}

	@Test
	@DisplayName("알림 설정 업데이트 실패 - null 값")
	public void updateAlarmSetting_Fail_NullValue() {
		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.updateAlarmSetting(userId, null));

		assertThat(exception.getMessage()).isEqualTo("알림 설정 값은 null일 수 없습니다.");
	}

	@Test
	@DisplayName("알림 설정 업데이트 실패 - 존재하지 않는 사용자")
	public void updateAlarmSetting_Fail_UserNotFound() {
		// given
		Long nonExistentUserId = 999L;

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.updateAlarmSetting(nonExistentUserId, true));

		assertThat(exception.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("다크모드 설정 업데이트 - true로 변경")
	public void updateDarkModeSetting_ToTrue() {
		// given
		UserSettings existingSettings = UserSettings.builder()
			.userId(userId)
			.isAlarmOn(true)
			.isDarkMode(false)
			.build();
		userSettingsRepository.save(existingSettings);

		// when
		Boolean result = settingsService.updateDarkModeSetting(userId, true);

		// then
		assertThat(result).isTrue();

		// DB에서 확인
		Optional<UserSettings> updatedSettings = userSettingsRepository.findByUserId(userId);
		assertThat(updatedSettings).isPresent();
		assertThat(updatedSettings.get().getIsDarkMode()).isTrue();
		assertThat(updatedSettings.get().getIsAlarmOn()).isTrue(); // 다른 설정은 변경되지 않음
	}

	@Test
	@DisplayName("다크모드 설정 업데이트 - false로 변경")
	public void updateDarkModeSetting_ToFalse() {
		// given
		UserSettings existingSettings = UserSettings.builder()
			.userId(userId)
			.isAlarmOn(true)
			.isDarkMode(true)
			.build();
		userSettingsRepository.save(existingSettings);

		// when
		Boolean result = settingsService.updateDarkModeSetting(userId, false);

		// then
		assertThat(result).isFalse();

		// DB에서 확인
		Optional<UserSettings> updatedSettings = userSettingsRepository.findByUserId(userId);
		assertThat(updatedSettings).isPresent();
		assertThat(updatedSettings.get().getIsDarkMode()).isFalse();
	}

	@Test
	@DisplayName("다크모드 설정 업데이트 실패 - null 값")
	public void updateDarkModeSetting_Fail_NullValue() {
		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.updateDarkModeSetting(userId, null));

		assertThat(exception.getMessage()).isEqualTo("다크모드 설정 값은 null일 수 없습니다.");
	}

	@Test
	@DisplayName("다크모드 설정 업데이트 실패 - 존재하지 않는 사용자")
	public void updateDarkModeSetting_Fail_UserNotFound() {
		// given
		Long nonExistentUserId = 999L;

		// when & then
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> settingsService.updateDarkModeSetting(nonExistentUserId, true));

		assertThat(exception.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("설정이 없는 사용자의 알림 설정 업데이트 - 새로 생성")
	public void updateAlarmSetting_CreateNewSettings() {
		// when
		Boolean result = settingsService.updateAlarmSetting(userId, false);

		// then
		assertThat(result).isFalse();

		// DB에 새로 생성되었는지 확인
		Optional<UserSettings> newSettings = userSettingsRepository.findByUserId(userId);
		assertThat(newSettings).isPresent();
		assertThat(newSettings.get().getIsAlarmOn()).isFalse();
		assertThat(newSettings.get().getIsDarkMode()).isFalse(); // 기본값
	}

	@Test
	@DisplayName("설정이 없는 사용자의 다크모드 설정 업데이트 - 새로 생성")
	public void updateDarkModeSetting_CreateNewSettings() {
		// when
		Boolean result = settingsService.updateDarkModeSetting(userId, true);

		// then
		assertThat(result).isTrue();

		// DB에 새로 생성되었는지 확인
		Optional<UserSettings> newSettings = userSettingsRepository.findByUserId(userId);
		assertThat(newSettings).isPresent();
		assertThat(newSettings.get().getIsDarkMode()).isTrue();
		assertThat(newSettings.get().getIsAlarmOn()).isTrue(); // 기본값
	}
}