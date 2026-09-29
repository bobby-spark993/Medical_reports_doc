package com.prescriptionscanner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "users", indexes = {
		@Index(name = "users_email_unique", columnList = "email", unique = true),
		@Index(name = "users_role_idx", columnList = "role")
})
public class User extends BaseEntity {

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 180)
	private String email;

	@Column(nullable = false, length = 120)
	private String passwordHash;

	@Column(nullable = false, length = 20)
	private Role role = Role.RECEPTIONIST;

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
	public String getPasswordHash() { return passwordHash; }
	public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
	public Role getRole() { return role; }
	public void setRole(Role role) { this.role = role; }
}
