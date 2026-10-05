package com.prescriptionscanner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A single key/value application setting, e.g. {@code logo.path}. */
@Entity
@Table(name = "app_settings")
public class AppSetting {

	@Id
	@Column(name = "setting_key", length = 60)
	private String key;

	@Column(columnDefinition = "text")
	private String value;

	public String getKey() { return key; }
	public void setKey(String key) { this.key = key; }
	public String getValue() { return value; }
	public void setValue(String value) { this.value = value; }
}