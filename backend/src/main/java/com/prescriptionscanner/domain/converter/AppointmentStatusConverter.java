package com.prescriptionscanner.domain.converter;

import com.prescriptionscanner.domain.AppointmentStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores appointment status as 'scheduled' rather than 'SCHEDULED'. */
@Converter(autoApply = true)
public class AppointmentStatusConverter implements AttributeConverter<AppointmentStatus, String> {

	@Override
	public String convertToDatabaseColumn(AppointmentStatus attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public AppointmentStatus convertToEntityAttribute(String dbData) {
		if (dbData == null) {
			return null;
		}
		for (AppointmentStatus s : AppointmentStatus.values()) {
			if (s.value().equalsIgnoreCase(dbData) || s.name().equalsIgnoreCase(dbData)) {
				return s;
			}
		}
		return AppointmentStatus.SCHEDULED;
	}
}
