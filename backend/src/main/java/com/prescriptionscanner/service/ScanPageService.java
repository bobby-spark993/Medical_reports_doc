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
 * Splits an upload into one image per page so every page can be scanned
 * independently. Images pass straight through; PDFs are rasterised page by
 * page with PDFBox.
 */
@Service
public class ScanPageService {

	private static final Logger log = LoggerFactory.getLogger(ScanPageService.class);

	/** Enough detail for OCR without producing huge images. */
	private static final int PDF_DPI = 200;

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

	public List<Page> split(byte[] data, String mimeType, int sourceIndex, String sourceName) {
		if ("application/pdf".equalsIgnoreCase(mimeType)) {
			return splitPdf(data, sourceIndex, sourceName);
		}
		return List.of(new Page(sourceIndex, sourceName, 1, 1, data, mimeType, thumbnail(data)));
	}

	private List<Page> splitPdf(byte[] data, int sourceIndex, String sourceName) {
		List<Page> pages = new ArrayList<>();
		try (PDDocument document = Loader.loadPDF(data)) {
			int count = document.getNumberOfPages();
			PDFRenderer renderer = new PDFRenderer(document);
			for (int index = 0; index < count; index++) {
				BufferedImage image = renderer.renderImageWithDPI(index, PDF_DPI);
				pages.add(new Page(sourceIndex, sourceName, index + 1, count, toPng(image), "image/png",
						thumbnail(image)));
			}
		} catch (Exception ex) {
			log.warn("Could not read PDF {}: {}", sourceName, ex.getMessage());
			throw ApiException.badRequest(
					"Could not read that PDF. It may be corrupted or password-protected.");
		}
		return pages;
	}

	private static byte[] toPng(BufferedImage image) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (Exception ex) {
			throw new IllegalStateException("Could not render a PDF page", ex);
		}
	}

	/** Down-scaled PNG, base64-encoded for inline preview in the page list. */
	private String thumbnail(BufferedImage image) {
		try {
			int width = Math.min(THUMBNAIL_WIDTH, Math.max(1, image.getWidth()));
			int height = Math.max(1,
					(int) Math.round(image.getHeight() * (width / (double) image.getWidth())));
			BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics = scaled.createGraphics();
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(image, 0, 0, width, height, null);
			graphics.dispose();
			return Base64.getEncoder().encodeToString(toPng(scaled));
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
