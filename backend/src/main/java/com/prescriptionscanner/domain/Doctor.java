package com.prescriptionscanner.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "doctors", indexes = {
		@Index(name = "doctors_name_idx", columnList = "name"),
		@Index(name = "doctors_registration_no_idx", columnList = "registration_no")
})
public class Doctor extends BaseEntity {

	@Column(nullable = false, length = 160)
	private String name;

	@Column(length = 160)
	private String qualification;

	@Column(length = 200)
	private String experience;

	@Column(name = "registration_no", length = 80)
	private String registrationNo;

	@Column(length = 120)
	private String designation;

	@Column(length = 200)
	private String clinic;

	@OneToMany(mappedBy = "doctor")
	private List<Visit> visits = new ArrayList<>();

	@OneToMany(mappedBy = "doctor")
	private List<Appointment> appointments = new ArrayList<>();

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getQualification() { return qualification; }
	public void setQualification(String qualification) { this.qualification = qualification; }
	public String getExperience() { return experience; }
	public void setExperience(String experience) { this.experience = experience; }
	public String getRegistrationNo() { return registrationNo; }
	public void setRegistrationNo(String registrationNo) { this.registrationNo = registrationNo; }
	public String getDesignation() { return designation; }
	public void setDesignation(String designation) { this.designation = designation; }
	public String getClinic() { return clinic; }
	public void setClinic(String clinic) { this.clinic = clinic; }
	public List<Visit> getVisits() { return visits; }
	public void setVisits(List<Visit> visits) { this.visits = visits; }
	public List<Appointment> getAppointments() { return appointments; }
	public void setAppointments(List<Appointment> appointments) { this.appointments = appointments; }
}
