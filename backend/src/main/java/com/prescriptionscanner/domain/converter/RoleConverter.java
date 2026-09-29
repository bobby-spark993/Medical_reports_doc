package com.prescriptionscanner.domain.converter;

import com.prescriptionscanner.domain.Role;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores roles as lowercase ('admin') rather than the Java constant ('ADMIN'). */
@Converter(autoApply = true)
public class RoleConverter implements AttributeConverter<Role, String> {

	@Override
	public String convertToDatabaseColumn(Role attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public Role convertToEntityAttribute(String dbData) {
		return dbData == null ? null : Role.from(dbData);
	}
}
