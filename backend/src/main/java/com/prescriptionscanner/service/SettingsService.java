package com.prescriptionscanner.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.domain.AppSetting;
import com.prescriptionscanner.exception.ApiException;
import com.prescriptionscanner.repository.AppSettingRepository;

/**
 * Clinic-level settings. Currently only the brand logo, stored through
 * {@link StorageService} so it is never exposed through a static resource
 * folder -- it is streamed by the public {@code GET /api/settings/logo}.
 */
@Service
public class SettingsService {

	public static final String LOGO_KEY = "logo.path";

	private static final List<String> ALLOWED_LOGO_MIMES = List.of(
			"image/jpeg", "image/png", "image/webp");

	private static final long MAX_LOGO_BYTES = 2 * 1024 * 1024L;

	private final AppSettingRepository settings;
	private final StorageService storageService;

	public SettingsService(AppSettingRepository settings, StorageService storageService) {
		this.settings = settings;
		this.storageService = storageService;
	}

	@Transactional
	public String saveLogo(byte[] data, String declaredMime) {
		if (data == null || data.length == 0) {
			throw ApiException.badRequest("No file uploaded.");
		}
		if (data.length > MAX_LOGO_BYTES) {
			throw ApiException.badRequest("Logo must be 2 MB or smaller.");
		}

		String mime = StorageService.sniffMime(data);
		if (mime == null || !ALLOWED_LOGO_MIMES.contains(mime)) {
			throw ApiException.badRequest("Logo must be a PNG, JPEG or WebP image.");
		}

		StorageService.StoredFile stored = storageService.save(data, mime);
		Optional<String> previous = logoPath();
		if (previous.isPresent() && !previous.get().equals(stored.relativePath())) {
			storageService.delete(previous.get());
		}

		AppSetting setting = new AppSetting();
		setting.setKey(LOGO_KEY);
		setting.setValue(stored.relativePath());
		settings.save(setting);

		return stored.relativePath();
	}

	@Transactional
	public boolean deleteLogo() {
		Optional<String> path = logoPath();
		if (path.isEmpty()) {
			return false;
		}
		storageService.delete(path.get());
		settings.deleteById(LOGO_KEY);
		return true;
	}

	public Optional<String> logoPath() {
		return settings.findById(LOGO_KEY).map(AppSetting::getValue).filter(v -> v != null && !v.isBlank());
	}

	/** Loads the stored logo bytes, or empty when no logo is set or the file is gone. */
	public Optional<StorageService.LoadedFile> logo() {
		return logoPath().flatMap(storageService::load);
	}
}