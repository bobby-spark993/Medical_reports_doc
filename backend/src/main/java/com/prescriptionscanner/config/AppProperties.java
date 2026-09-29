package com.prescriptionscanner.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds every {@code app.*} property. Secrets live in
 * application-local.properties (gitignored) or in environment variables --
 * never in application.properties.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

	private Upload upload = new Upload();
	private Jwt jwt = new Jwt();
	private Gemini gemini = new Gemini();
	private Ai ai = new Ai();
	private Seed seed = new Seed();
	private RateLimit rateLimit = new RateLimit();
	private Cors cors = new Cors();

	public static class Upload {
		private String dir = "storage/uploads";
		private long maxMb = 10;
		private List<String> allowedTypes = List.of(
				"image/jpeg", "image/png", "image/webp", "application/pdf");

		public String getDir() { return dir; }
		public void setDir(String dir) { this.dir = dir; }
		public long getMaxMb() { return maxMb; }
		public void setMaxMb(long maxMb) { this.maxMb = maxMb; }
		public List<String> getAllowedTypes() { return allowedTypes; }
		public void setAllowedTypes(List<String> allowedTypes) { this.allowedTypes = allowedTypes; }
	}

	public static class Jwt {
		private String secret = "";
		private long expirationMinutes = 480;
		private String issuer = "prescription-scanner";
		/** Must be true when served over HTTPS. Keep false for http://localhost. */
		private boolean cookieSecure = false;
		private String cookieSameSite = "Lax";

		public String getSecret() { return secret; }
		public void setSecret(String secret) { this.secret = secret; }
		public long getExpirationMinutes() { return expirationMinutes; }
		public void setExpirationMinutes(long expirationMinutes) { this.expirationMinutes = expirationMinutes; }
		public String getIssuer() { return issuer; }
		public void setIssuer(String issuer) { this.issuer = issuer; }
		public boolean isCookieSecure() { return cookieSecure; }
		public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
		public String getCookieSameSite() { return cookieSameSite; }
		public void setCookieSameSite(String cookieSameSite) { this.cookieSameSite = cookieSameSite; }
	}

	public static class Gemini {
		private String apiKey = "";
		private String model = "gemini-3.8-flash";
		/** Comma-separated models tried in order when the primary answers 503/overloaded. */
		private String fallbackModel = "gemini-3.7-flash,gemini-3.5-flash-lite,gemini-3.1-flash-lite";
		private long timeoutMs = 45_000;
		private String baseUrl = "https://generativelanguage.googleapis.com/v1beta/models";

		public String getApiKey() { return apiKey; }
		public void setApiKey(String apiKey) { this.apiKey = apiKey; }
		public String getModel() { return model; }
		public void setModel(String model) { this.model = model; }
		public String getFallbackModel() { return fallbackModel; }
		public void setFallbackModel(String fallbackModel) { this.fallbackModel = fallbackModel; }
		public long getTimeoutMs() { return timeoutMs; }
		public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
		public String getBaseUrl() { return baseUrl; }
		public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
	}

	public static class Ai {
		private boolean maskPii = false;

		public boolean isMaskPii() { return maskPii; }
		public void setMaskPii(boolean maskPii) { this.maskPii = maskPii; }
	}

	public static class Seed {
		private boolean enabled = true;
		private String adminEmail = "admin@prescriptionscanner.local";
		private String adminPassword = "Admin@12345";
		private String adminName = "Clinic Admin";

		public boolean isEnabled() { return enabled; }
		public void setEnabled(boolean enabled) { this.enabled = enabled; }
		public String getAdminEmail() { return adminEmail; }
		public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
		public String getAdminPassword() { return adminPassword; }
		public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
		public String getAdminName() { return adminName; }
		public void setAdminName(String adminName) { this.adminName = adminName; }
	}

	public static class RateLimit {
		private int loginMax = 10;
		private int loginWindowMinutes = 15;
		private int uploadMax = 20;
		private int uploadWindowMinutes = 60;

		public int getLoginMax() { return loginMax; }
		public void setLoginMax(int loginMax) { this.loginMax = loginMax; }
		public int getLoginWindowMinutes() { return loginWindowMinutes; }
		public void setLoginWindowMinutes(int loginWindowMinutes) { this.loginWindowMinutes = loginWindowMinutes; }
		public int getUploadMax() { return uploadMax; }
		public void setUploadMax(int uploadMax) { this.uploadMax = uploadMax; }
		public int getUploadWindowMinutes() { return uploadWindowMinutes; }
		public void setUploadWindowMinutes(int uploadWindowMinutes) { this.uploadWindowMinutes = uploadWindowMinutes; }
	}

	public static class Cors {
		private List<String> allowedOrigins = List.of("http://localhost:5173");

		public List<String> getAllowedOrigins() { return allowedOrigins; }
		public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
	}

	public Upload getUpload() { return upload; }
	public void setUpload(Upload upload) { this.upload = upload; }
	public Jwt getJwt() { return jwt; }
	public void setJwt(Jwt jwt) { this.jwt = jwt; }
	public Gemini getGemini() { return gemini; }
	public void setGemini(Gemini gemini) { this.gemini = gemini; }
	public Ai getAi() { return ai; }
	public void setAi(Ai ai) { this.ai = ai; }
	public Seed getSeed() { return seed; }
	public void setSeed(Seed seed) { this.seed = seed; }
	public RateLimit getRateLimit() { return rateLimit; }
	public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }
	public Cors getCors() { return cors; }
	public void setCors(Cors cors) { this.cors = cors; }
}
