package com.prescriptionscanner.dto;

public record OkResponse(boolean ok) {

	/** Named success() so it does not clash with the record's generated ok() accessor. */
	public static OkResponse success() {
		return new OkResponse(true);
	}
}
