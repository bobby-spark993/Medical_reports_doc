package com.prescriptionscanner.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prescriptionscanner.config.AppProperties;
import com.prescriptionscanner.dto.GeminiExtraction;
import com.prescriptionscanner.dto.MedicalExtraction;
import com.prescriptionscanner.exception.ApiException;

/**
 * Calls the Gemini REST API directly (no SDK) and returns a validated
 * {@link GeminiExtraction}.
 *
 * <p>Free key: https://aistudio.google.com/apikey
 */
@Service
public class GeminiService {

	private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

	private static final String MEDICAL_PROMPT_PATH = MedicalPrompts.MEDICAL_PROMPT_PATH;
	private static final String MEDICAL_SCHEMA_PATH = MedicalPrompts.MEDICAL_SCHEMA_PATH;

	/** Retries per model before moving on to the fallback model. */
	private static final int MAX_TRANSIENT_ATTEMPTS = 3;

	/** Full passes over the candidate models before giving up. */
	private static final int MAX_ROUNDS = 2;

	private final AppProperties props;
	private final ObjectMapper mapper;
	private final RestClient restClient;
	private final String medicalPrompt;
	private final JsonNode medicalSchema;
	private final JsonNode extractionSchema;

	public GeminiService(AppProperties props, ObjectMapper mapper) {
		this.props = props;
		this.mapper = mapper;
		this.restClient = buildRestClient(props);
		this.medicalPrompt = readResource(MEDICAL_PROMPT_PATH);
		this.medicalSchema = readJsonResource(MEDICAL_SCHEMA_PATH);
		this.extractionSchema = readJsonResource(MedicalPrompts.EXTRACTION_SCHEMA_PATH);
	}

	/**
	 * A {@link RestClient} backed by the JDK {@link HttpClient}: a 15s connect
	 * timeout plus the configurable read timeout, so a slow scan fails cleanly
	 * instead of hanging the request thread.
	 */
	private static RestClient buildRestClient(AppProperties props) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(15))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(props.getGemini().getTimeoutMs()));
		return RestClient.builder()
				.requestFactory(requestFactory)
				.build();
	}

	/** Legacy prescription extraction (kept for the existing /api/prescriptions flow). */
	public GeminiExtraction extract(byte[] imageBytes, String mimeType, String contextHint) {
		Map<String, Object> body = buildRequestBody(imageBytes, mimeType, contextHint);
		return parseExtraction(callGemini(body, this::hasPatientIdentity));
	}

	/**
	 * Document-oriented extraction (lab report or prescription) with a strict
	 * responseSchema, as used by /api/documents/scan.
	 */
	public MedicalExtraction extractMedicalDocument(byte[] data, String mimeType) {
		Map<String, Object> body = buildMedicalRequestBody(data, mimeType);
		return parseMedicalExtraction(callGemini(body));
	}

	/** Sends one generateContent request, validating config and retrying once on 429. */
	private String callGemini(Map<String, Object> body) {
		return callGemini(body, response -> true);
	}

	/**
	 * Tries each candidate model and returns the first response the {@code acceptable}
	 * test passes. When none pass, the last response is returned as a best effort
	 * so a partial scan still yields something for the user to review.
	 */
	private String callGemini(Map<String, Object> body, java.util.function.Predicate<String> acceptable) {
		String apiKey = props.getGemini().getApiKey();
		if (apiKey == null || apiKey.isBlank() || "your_key_here".equals(apiKey)) {
			throw ApiException.internal(
					"GEMINI_API_KEY is not configured. Get a free key at https://aistudio.google.com/apikey "
					+ "and set app.gemini.api-key in application-local.properties.");
		}

		String payload;
		try {
			payload = mapper.writeValueAsString(body);
		} catch (Exception ex) {
			log.error("Could not serialise the Gemini request", ex);
			throw ApiException.internal("Could not prepare the Gemini request.");
		}

		// Walk the candidate models. If every model is busy or rate-limited,
		// wait briefly and run the whole chain again, because "high demand" and
		// per-minute quotas usually clear within seconds.
		List<String> models = candidateModels();
		ApiException lastError = null;
		String bestEffort = null;
		for (int round = 1; round <= MAX_ROUNDS; round++) {
			boolean retryable = false;
			for (String model : models) {
				String endpoint = props.getGemini().getBaseUrl() + "/" + model + ":generateContent";
				for (int attempt = 1; attempt <= MAX_TRANSIENT_ATTEMPTS; attempt++) {
					try {
						ResponseEntity<String> response = restClient.post()
								.uri(endpoint)
								.header("x-goog-api-key", apiKey)
								.contentType(MediaType.APPLICATION_JSON)
								.body(payload)
								.retrieve()
								.toEntity(String.class);
						String responseBody = response.getBody();
						if (acceptable.test(responseBody)) {
							return responseBody;
						}
						// Usable as a fallback, but keep looking for a model that
						// returns the fuller extraction (e.g. left out the age).
						log.info("Model {} returned an incomplete extraction; trying the next model", model);
						bestEffort = responseBody;
						break; // try the next candidate model
					} catch (RestClientResponseException ex) {
						int status = ex.getStatusCode().value();
						// 429 (quota) and 503 (high demand) do not clear within a
						// second, so skip straight to the next candidate model
						// instead of retrying the same one and burning quota.
						boolean retrySameModel = status == 500 || status == 502 || status == 504;
						if (retrySameModel && attempt < MAX_TRANSIENT_ATTEMPTS) {
							long waitMs = retryDelayMillis(ex.getResponseHeaders(), attempt);
							log.warn("Gemini {} for model {} (attempt {}/{}), retrying in {} ms",
									status, model, attempt, MAX_TRANSIENT_ATTEMPTS, waitMs);
							sleep(waitMs);
							continue;
						}
						lastError = translateHttpError(status, ex.getResponseBodyAsString(), model);
						retryable = status == 429 || status == 503;
						break; // try the next candidate model
					} catch (ResourceAccessException ex) {
						if (isTimeout(ex)) {
							throw new ApiException(HttpStatus.GATEWAY_TIMEOUT,
									"Gemini took longer than "
									+ (props.getGemini().getTimeoutMs() / 1000)
									+ "s to read this scan. Try a smaller or clearer image.");
						}
						throw ApiException.badGateway("Could not reach Gemini. Check the server's internet connection.");
					} catch (RestClientException ex) {
						log.error("Gemini call failed", ex);
						throw ApiException.badGateway("Gemini request failed. Please try again.");
					}
				}
			}
			if (round < MAX_ROUNDS && retryable) {
				long waitMs = 2000L * round;
				log.warn("All {} Gemini models busy/rate-limited; retrying in {} ms (round {}/{})",
						models.size(), waitMs, round, MAX_ROUNDS);
				sleep(waitMs);
			} else {
				break;
			}
		}
		if (bestEffort != null) {
			return bestEffort;
		}
		throw lastError != null ? lastError : ApiException.badGateway("Gemini request failed. Please try again.");
	}

	/**
	 * True when the extraction carries enough patient identity to match an
	 * existing folder: a name plus an age or a gender. Used to prefer a model
	 * that returns the complete patient block over one that dropped fields.
	 */
	private boolean hasPatientIdentity(String responseBody) {
		try {
			JsonNode patient = mapper.readTree(extractJsonText(responseBody)).path("patient");
			boolean hasName = !patient.path("name").asText("").isBlank();
			boolean hasAge = !patient.path("age").asText("").isBlank()
					|| !patient.path("age_years").asText("").isBlank();
			boolean hasGender = !patient.path("gender").asText("").isBlank();
			return hasName && (hasAge || hasGender);
		} catch (Exception ex) {
			return false;
		}
	}

	/**
	 * The configured primary model first, then each comma-separated fallback
	 * (all de-duplicated). Several fallbacks keep scans working when the free
	 * tier reports "high demand" on more than one model at once.
	 */
	private List<String> candidateModels() {
		List<String> models = new ArrayList<>();
		addCandidate(models, props.getGemini().getModel());
		String fallbacks = props.getGemini().getFallbackModel();
		if (fallbacks != null) {
			for (String candidate : fallbacks.split(",")) {
				addCandidate(models, candidate);
			}
		}
		if (models.isEmpty()) {
			models.add("gemini-3.7-flash");
		}
		return models;
	}

	private static void addCandidate(List<String> models, String name) {
		if (name == null) {
			return;
		}
		String trimmed = name.trim();
		if (trimmed.isEmpty() || models.stream().anyMatch(existing -> existing.equalsIgnoreCase(trimmed))) {
			return;
		}
		models.add(trimmed);
	}

	/** Walks the cause chain for the timeout exceptions a slow Gemini call can raise. */
	private static boolean isTimeout(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof HttpTimeoutException
					|| cause instanceof java.net.SocketTimeoutException
					|| cause instanceof java.util.concurrent.TimeoutException) {
				return true;
			}
			if (cause.getCause() == cause) {
				break;
			}
		}
		return false;
	}

	private Map<String, Object> buildMedicalRequestBody(byte[] data, String mimeType) {
		String base64 = Base64.getEncoder().encodeToString(data);

		List<Map<String, Object>> parts = new ArrayList<>();
		parts.add(Map.of("inline_data", Map.of("mime_type", mimeType, "data", base64)));
		parts.add(Map.of("text", "Extract this medical document. Return only the JSON object."));

		Map<String, Object> generationConfig = new LinkedHashMap<>();
		generationConfig.put("responseMimeType", "application/json");
		generationConfig.put("responseSchema", medicalSchema);
		generationConfig.put("temperature", 0);
		// This is OCR, not reasoning: skipping thinking cuts latency and cost.
		generationConfig.put("thinkingConfig", Map.of("thinkingBudget", 0));

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", medicalPrompt))));
		body.put("contents", List.of(Map.of("role", "user", "parts", parts)));
		body.put("generationConfig", generationConfig);
		return body;
	}

	private static String readResource(String path) {
		try (InputStream in = new ClassPathResource(path).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new IllegalStateException("Missing bundled resource " + path, ex);
		}
	}

	private JsonNode readJsonResource(String path) {
		try {
			return mapper.readTree(readResource(path));
		} catch (Exception ex) {
			throw new IllegalStateException("Bundled resource is not valid JSON: " + path, ex);
		}
	}

	private Map<String, Object> buildRequestBody(byte[] imageBytes, String mimeType, String contextHint) {
		String base64 = Base64.getEncoder().encodeToString(imageBytes);

		List<Map<String, Object>> parts = new ArrayList<>();
		parts.add(Map.of("inline_data", Map.of("mime_type", mimeType, "data", base64)));
		parts.add(Map.of("text", MedicalPrompts.USER_PROMPT));

		if (contextHint != null && !contextHint.isBlank()) {
			parts.add(Map.of("text",
					"Staff-supplied context (may be wrong, prefer what is on the scan): " + contextHint));
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", MedicalPrompts.SYSTEM_PROMPT))));
		body.put("contents", List.of(Map.of("role", "user", "parts", parts)));
		body.put("generationConfig", Map.of(
				"responseMimeType", "application/json",
				"responseSchema", extractionSchema,
				"temperature", 0,
				// This is OCR, not reasoning: skipping thinking cuts latency and cost.
				"thinkingConfig", Map.of("thinkingBudget", 0)));
		return body;
	}

	private long retryDelayMillis(HttpHeaders headers, int attempt) {
		String retryAfter = headers == null ? null : headers.getFirst("retry-after");
		if (retryAfter != null) {
			try {
				return Math.min(Long.parseLong(retryAfter.trim()) * 1000L, 20_000L);
			} catch (NumberFormatException ignored) {
				// fall through to the exponential backoff
			}
		}
		// 1.5s, 3s, 6s ... capped at 15s.
		return Math.min(1500L * (1L << (attempt - 1)), 15_000L);
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private ApiException translateHttpError(int status, String body, String model) {
		String snippet = body == null ? "" : body.substring(0, Math.min(body.length(), 500));
		// Never contains the API key; helpful when Gemini changes its API.
		log.warn("Gemini returned HTTP {}: {}", status, snippet);

		if (status == 400 && (snippet.contains("API key not valid") || snippet.contains("API_KEY_INVALID"))) {
			return ApiException.internal(
					"Gemini rejected the API key. Get a fresh key at https://aistudio.google.com/apikey "
					+ "and update app.gemini.api-key.");
		}
		return switch (status) {
			case 400 -> ApiException.badGateway("Gemini could not process this document.");
			case 401, 403 -> ApiException.internal(
					"The Gemini API key is not allowed to use this model. Check the key type in AI Studio.");
			case 404 -> ApiException.internal("Gemini model '" + model
					+ "' is not available for this key. Set app.gemini.model=gemini-3.8-flash.");
			case 429 -> ApiException.tooManyRequests(
					"Gemini rate limit reached. Please wait a moment and try again.");
			case 503 -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"Gemini is busy (high demand) on '" + model
					+ "'. Please try again in a minute, or set app.gemini.model to another model.");
			default -> status >= 500
					? new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
							"Gemini is temporarily unavailable. Please try again.")
					: ApiException.badGateway("Gemini request failed. Please try again.");
		};
	}

	private GeminiExtraction parseExtraction(String responseBody) {
		String json = extractJsonText(responseBody);
		try {
			return mapper.readValue(json, GeminiExtraction.class).normalized();
		} catch (Exception ex) {
			log.warn("Gemini returned JSON that did not match the prescription schema: {}", ex.getMessage());
			throw ApiException.badGateway(
					"Gemini's response did not match the expected schema. Please re-upload the scan.");
		}
	}

	private MedicalExtraction parseMedicalExtraction(String responseBody) {
		String json = extractJsonText(responseBody);
		try {
			return mapper.readValue(json, MedicalExtraction.class);
		} catch (Exception ex) {
			log.warn("Gemini returned JSON that did not match the document schema: {}", ex.getMessage());
			throw ApiException.badGateway(
					"Gemini's response did not match the expected schema. Please re-upload the document.");
		}
	}

	/** Pulls the model's JSON text out of the generateContent envelope and un-fences it. */
	private String extractJsonText(String responseBody) {
		JsonNode root;
		try {
			root = mapper.readTree(responseBody);
		} catch (Exception ex) {
			throw ApiException.badGateway("Gemini returned an unreadable response. Please re-upload the scan.");
		}

		String blockReason = root.path("promptFeedback").path("blockReason").asText(null);
		if (blockReason != null && !blockReason.isBlank()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
					"The upload was blocked by Gemini's safety filter (" + blockReason + ").");
		}

		JsonNode candidates = root.path("candidates");
		if (!candidates.isArray() || candidates.isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
					"Gemini returned no result for this document. Please re-upload the scan.");
		}

		JsonNode candidate = candidates.get(0);
		String finishReason = candidate.path("finishReason").asText("");
		StringBuilder text = new StringBuilder();
		JsonNode parts = candidate.path("content").path("parts");
		if (parts.isArray()) {
			for (JsonNode part : parts) {
				text.append(part.path("text").asText(""));
			}
		}

		if (text.isEmpty()) {
			String reason = finishReason.isBlank() ? "" : " (" + finishReason + ")";
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
					"Gemini could not read any text from this document" + reason
							+ ". Make sure the scan is not blank and is in focus.");
		}

		return stripJsonFences(text.toString());
	}

	/** Tolerates ```json fences even though responseMimeType should prevent them. */
	private String stripJsonFences(String raw) {
		String trimmed = raw == null ? "" : raw.trim();
		if (trimmed.startsWith("```")) {
			int firstNewline = trimmed.indexOf('\n');
			int lastFence = trimmed.lastIndexOf("```");
			if (firstNewline != -1 && lastFence > firstNewline) {
				trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
			}
		}
		if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
			int firstBrace = trimmed.indexOf('{');
			int lastBrace = trimmed.lastIndexOf('}');
			if (firstBrace != -1 && lastBrace > firstBrace) {
				trimmed = trimmed.substring(firstBrace, lastBrace + 1);
			}
		}
		return trimmed;
	}
}
