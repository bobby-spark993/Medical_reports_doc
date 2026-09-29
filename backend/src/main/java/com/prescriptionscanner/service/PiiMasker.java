package com.prescriptionscanner.service;

import org.springframework.stereotype.Service;

import com.prescriptionscanner.config.AppProperties;

/**
 * DPDP Act 2023 helper.
 *
 * <p><b>Honest limitation:</b> the prescription is sent to Gemini as an image
 * (or PDF). Anything printed on the scan -- including the patient's name and
 * phone -- reaches the model regardless of this class. Pixels cannot be masked
 * in code.
 *
 * <p>What the flag does:
 * <ul>
 *   <li>masks name/phone in the free-text hints we append to the prompt;</li>
 *   <li>gives you one switch to key your real privacy control off (paid tier,
 *       no-training project, or a self-hosted model).</li>
 * </ul>
 *
 * <p>For real patient data, use a paid Gemini tier / no-training project.
 */
@Service
public class PiiMasker {

	private final AppProperties props;

	public PiiMasker(AppProperties props) {
		this.props = props;
	}

	public boolean enabled() {
		return props.getAi().isMaskPii();
	}

	public String maskName(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		StringBuilder out = new StringBuilder();
		for (String part : name.trim().split("\\s+")) {
			if (!out.isEmpty()) {
				out.append(' ');
			}
			if (part.length() <= 1) {
				out.append(part);
			} else {
				out.append(part.charAt(0));
				out.append("*".repeat(Math.min(part.length() - 1, 6)));
			}
		}
		return out.toString();
	}

	public String maskPhone(String phone) {
		if (phone == null || phone.isBlank()) {
			return null;
		}
		String digits = phone.replaceAll("\\D", "");
		if (digits.length() < 4) {
			return "***";
		}
		return "******" + digits.substring(digits.length() - 2);
	}

	/** Masks emails and Indian mobile numbers inside a free-text blob. */
	public String maskFreeText(String text) {
		if (text == null || !enabled()) {
			return text;
		}
		String out = text.replaceAll("[\\w.+-]+@[\\w-]+\\.[\\w.]+", "[email-redacted]");
		out = out.replaceAll("(?:\\+?91[\\s-]?)?[6-9]\\d{4}[\\s-]?\\d{5}", "[phone-redacted]");
		return out;
	}
}
