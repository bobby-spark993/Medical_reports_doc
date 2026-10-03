package com.prescriptionscanner.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.prescriptionscanner.exception.ApiException;

/**
 * Turns an upload into per-page images. Images pass straight through; PDFs are
 * rasterised with PDFBox. Pages are rendered as JPEG at a capped size so the
 * payload sent to the vision model stays small and fast.
 */
@Service
public class ScanPageService {

	private static final Logger log = LoggerFactory.getLogger(ScanPageService.class);

	/** Enough detail for OCR without producing huge images. */
	private static final int PDF_DPI = 150;

	/** Longest side of a rendered page; larger pages are scaled down. */
	private static final int MAX_DIMENSION = 1600;

	/** Width of the small preview returned to the browser. */
	private static final int THUMBNAIL_WIDTH = 200;

	public record Page(
			int sourceIndex,
			String sourceName,
			int page,
			int pageCount,
			byte[] imageBytes,
			String mimeType,
			String thumbnailBase64) {
	}

	/** Number of pages in a file (1 for a single image). */
	public int pageCount(byte[] data, String mimeType) {
		if (!isPdf(mimeType)) {
			return 1;
		}
		try (PDDocument document = Loader.loadPDF(data)) {
			return document.getNumberOfPages();
		} catch (Exception ex) {
			log.warn("Could not read PDF: {}", ex.getMessage());
			throw ApiException.badRequest(
					"Could not read that PDF. It may be corrupted or password-protected.");
		}
	}

	/** Renders a single (1-based) page of the file. */
	public Page renderPage(byte[] data, String mimeType, int pageNumber, int sourceIndex, String sourceName) {
		if (!isPdf(mimeType)) {
			return new Page(sourceIndex, sourceName, 1, 1, data, mimeType, thumbnail(data));
		}
		try (PDDocument document = Loader.loadPDF(data)) {
			int count = document.getNumberOfPages();
			if (pageNumber < 1 || pageNumber > count) {
				throw ApiException.badRequest("Page " + pageNumber + " does not exist in this document.");
			}
			PDFRenderer renderer = new PDFRenderer(document);
			BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, PDF_DPI);
			BufferedImage scaled = scaleDown(image, MAX_DIMENSION);
			return new Page(sourceIndex, sourceName, pageNumber, count, toJpeg(scaled), "image/jpeg",
					thumbnail(scaled));
		} catch (ApiException ex) {
			throw ex;
		} catch (Exception ex) {
			log.warn("Could not read PDF {}: {}", sourceName, ex.getMessage());
			throw ApiException.badRequest(
					"Could not read that PDF. It may be corrupted or password-protected.");
		}
	}

	/** Splits a whole file into pages (used by the bulk endpoints). */
	public List<Page> split(byte[] data, String mimeType, int sourceIndex, String sourceName) {
		if (!isPdf(mimeType)) {
			return List.of(new Page(sourceIndex, sourceName, 1, 1, data, mimeType, thumbnail(data)));
		}
		List<Page> pages = new ArrayList<>();
		try (PDDocument document = Loader.loadPDF(data)) {
			int count = document.getNumberOfPages();
			PDFRenderer renderer = new PDFRenderer(document);
			for (int index = 0; index < count; index++) {
				BufferedImage scaled = scaleDown(renderer.renderImageWithDPI(index, PDF_DPI), MAX_DIMENSION);
				pages.add(new Page(sourceIndex, sourceName, index + 1, count, toJpeg(scaled), "image/jpeg",
						thumbnail(scaled)));
			}
		} catch (Exception ex) {
			log.warn("Could not read PDF {}: {}", sourceName, ex.getMessage());
			throw ApiException.badRequest(
					"Could not read that PDF. It may be corrupted or password-protected.");
		}
		return pages;
	}

	private static boolean isPdf(String mimeType) {
		return "application/pdf".equalsIgnoreCase(mimeType);
	}

	/** Scales the image so its longest side is at most {@code maxDimension}. */
	private static BufferedImage scaleDown(BufferedImage image, int maxDimension) {
		int longest = Math.max(image.getWidth(), image.getHeight());
		if (longest <= maxDimension) {
			return image;
		}
		double ratio = maxDimension / (double) longest;
		int width = Math.max(1, (int) Math.round(image.getWidth() * ratio));
		int height = Math.max(1, (int) Math.round(image.getHeight() * ratio));
		return resize(image, width, height);
	}

	private static BufferedImage resize(BufferedImage image, int width, int height) {
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = scaled.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.drawImage(image, 0, 0, width, height, null);
		graphics.dispose();
		return scaled;
	}

	private static byte[] toJpeg(BufferedImage image) {
		// JPEG has no alpha channel; flatten onto an RGB image first.
		BufferedImage rgb = image.getType() == BufferedImage.TYPE_INT_RGB
				? image
				: resize(image, image.getWidth(), image.getHeight());
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(rgb, "jpeg", out);
			return out.toByteArray();
		} catch (Exception ex) {
			throw new IllegalStateException("Could not render a PDF page", ex);
		}
	}

	private static byte[] toPng(BufferedImage image) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (Exception ex) {
			return null;
		}
	}

	/** Down-scaled PNG, base64-encoded for inline preview in the page list. */
	private String thumbnail(BufferedImage image) {
		try {
			int width = Math.min(THUMBNAIL_WIDTH, Math.max(1, image.getWidth()));
			int height = Math.max(1,
					(int) Math.round(image.getHeight() * (width / (double) image.getWidth())));
			byte[] png = toPng(resize(image, width, height));
			return png == null ? null : Base64.getEncoder().encodeToString(png);
		} catch (Exception ex) {
			return null;
		}
	}

	/** Thumbnails need a readable image; WebP is not supported and returns null. */
	private String thumbnail(byte[] data) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
			return image == null ? null : thumbnail(image);
		} catch (Exception ex) {
			return null;
		}
	}
}
