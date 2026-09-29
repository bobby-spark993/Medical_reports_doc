package com.prescriptionscanner.domain;

public enum AppointmentStatus {
	SCHEDULED("scheduled"),
	COMPLETED("completed"),
	CANCELLED("cancelled"),
	NO_SHOW("no_show");

	private final String value;

	AppointmentStatus(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}
}
