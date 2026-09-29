package com.prescriptionscanner.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "patients", indexes = {
		@Index(name = "patients_pid_unique", columnList = "pid", unique = true),
		@Index(name = "patients_pid_short_idx", columnList = "pid_short"),
		@Index(name = "patients_name_idx", columnList = "name"),
		@Index(name = "patients_name_gender_age_idx", columnList = "name,gender,age"),
		@Index(name = "patients_phone_idx", columnList = "phone")
})
public class Patient extends BaseEntity {

	/** Hospital patient id, e.g. SNP260404071824. Nullable: not every scan carries one. */
	@Column(length = 60)
	private String pid;

	/**
	 * The last 6 digits of {@link #pid}. Lab reports print only this number next
	 * to the name, so it is indexed for automatic linking.
	 */
	@Column(name = "pid_short", length = 12)
	private String pidShort;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(length = 20)
	private String gender;

	@Column(length = 20)
	private String age;

	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	@Column(length = 40)
	private String maritalStatus;

	@Column(length = 40)
	private String phone;

	@Column(columnDefinition = "text")
	private String address;

	@Column(columnDefinition = "text")
	private String allergies;

	@OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Visit> visits = new ArrayList<>();

	@OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Appointment> appointments = new ArrayList<>();

	public String getPid() { return pid; }
	public void setPid(String pid) { this.pid = pid; }
	public String getPidShort() { return pidShort; }
	public void setPidShort(String pidShort) { this.pidShort = pidShort; }
	public LocalDate getDateOfBirth() { return dateOfBirth; }
	public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getGender() { return gender; }
	public void setGender(String gender) { this.gender = gender; }
	public String getAge() { return age; }
	public void setAge(String age) { this.age = age; }
	public String getMaritalStatus() { return maritalStatus; }
	public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }
	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }
	public String getAddress() { return address; }
	public void setAddress(String address) { this.address = address; }
	public String getAllergies() { return allergies; }
	public void setAllergies(String allergies) { this.allergies = allergies; }
	public List<Visit> getVisits() { return visits; }
	public void setVisits(List<Visit> visits) { this.visits = visits; }
	public List<Appointment> getAppointments() { return appointments; }
	public void setAppointments(List<Appointment> appointments) { this.appointments = appointments; }
}
