package com.prescriptionscanner.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.prescriptionscanner.config.AppProperties;
import com.prescriptionscanner.exception.ApiException;

/**
 * Private file storage for scans.
 *
 * <p>There is no static resource mapping for this folder: the only way to read
 * a file is {@code GET /api/files/{visitId}}, which requires a JWT. Files are
 * written under a random name so the original filename never reaches disk.
 */
@Service
public class StorageService {

	private static final Logger log = LoggerFactory.getLogger(StorageService.class);

	private static final Map<String, String> EXTENSION_BY_MIME = Map.of(
			"image/jpeg", ".jpg",
			"image/png", ".png",
			"image/webp", ".webp",
			"application/pdf", ".pdf");

	private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
			".jpg", "image/jpeg",
			".jpeg", "image/jpeg",
			".png", "image/png",
			".webp", "image/webp",
			".pdf", "application/pdf");

	private final Path root;
	private final AppProperties props;

	public StorageService(AppProperties props) {
		this.props = props;
		this.root = Paths.get(props.getUpload().getDir()).toAbsolutePath().normalize();
		try {
			Files.createDirectories(root);
			log.info("Scan storage ready at {}", root);
		} catch (IOException ex) {
			throw new IllegalStateException("Could not create upload directory " + root, ex);
		}
	}

	public record StoredFile(String filename, String relativePath) {
	}

	public record LoadedFile(byte[] data, String contentType, String filename) {
	}

	public long maxBytes() {
		return props.getUpload().getMaxMb() * 1024L * 1024L;
	}

	public boolean isAllowedMime(String mime) {
		return mime != null && props.getUpload().getAllowedTypes().stream()
				.anyMatch(allowed -> allowed.equalsIgnoreCase(mime));
	}

	/**
	 * Detects the real type from the leading bytes, so a renamed .exe posted as
	 * image/jpeg is rejected.
	 *
	 * @return the detected MIME type, or null when unrecognised
	 */
	public static String sniffMime(byte[] data) {
		if (data == null || data.length < 12) {
			return null;
		}
		if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
			return "image/jpeg";
		}
		// PNG signature is 0x89 'P' 'N' 'G' -- note the leading 0x89.
		if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
			return "image/png";
		}
		if (data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F') {
			return "application/pdf";
		}
		if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
				&& data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
			return "image/webp";
		}
		return null;
	}

	public StoredFile save(byte[] data, String mimeType) {
		String extension = EXTENSION_BY_MIME.getOrDefault(mimeType.toLowerCase(Locale.ROOT), ".bin");
		String filename = System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;
		Path target = root.resolve(filename).normalize();
		if (!target.startsWith(root)) {
			throw ApiException.badRequest("Invalid filename");
		}
		try {
			Files.write(target, data);
		} catch (IOException ex) {
			throw new UncheckedIOException("Could not store the uploaded scan", ex);
		}
		// Store a project-relative path: absolute paths differ per machine.
		return new StoredFile(filename, Paths.get(props.getUpload().getDir()).resolve(filename).toString());
	}

	public Optional<LoadedFile> load(String relativePath) {
		Path target = resolveSafely(relativePath);
		if (target == null || !Files.isReadable(target)) {
			return Optional.empty();
		}
		try {
			byte[] data = Files.readAllBytes(target);
			String name = target.getFileName().toString();
			String extension = name.contains(".")
					? name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT)
					: "";
			String contentType = MIME_BY_EXTENSION.getOrDefault(extension, "application/octet-stream");
			return Optional.of(new LoadedFile(data, contentType, name));
		} catch (IOException ex) {
			log.warn("Could not read stored scan {}: {}", relativePath, ex.getMessage());
			return Optional.empty();
		}
	}

	public boolean delete(String relativePath) {
		Path target = resolveSafely(relativePath);
		if (target == null) {
			return false;
		}
		try {
			return Files.deleteIfExists(target);
		} catch (IOException ex) {
			log.warn("Could not delete stored scan {}: {}", relativePath, ex.getMessage());
			return false;
		}
	}

	/** Rejects anything that would escape the storage root. */
	private Path resolveSafely(String relativePath) {
		if (relativePath == null || relativePath.isBlank()) {
			return null;
		}
		Path candidate = Paths.get(relativePath);
		Path resolved = (candidate.isAbsolute() ? candidate : Paths.get("").toAbsolutePath().resolve(candidate))
				.normalize();
		if (!resolved.startsWith(root)) {
			log.warn("Blocked path traversal attempt: {}", relativePath);
			return null;
		}
		return resolved;
	}
}
