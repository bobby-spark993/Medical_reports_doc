package com.prescriptionscanner.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.prescriptionscanner.dto.PatientDocumentDetail;
import com.prescriptionscanner.dto.PatientSummaryDto;
import com.prescriptionscanner.security.SecurityUser;
import com.prescriptionscanner.service.PatientLookupService;

/** Document-centric patient search and lookup (coexists with PatientController). */
@RestController
@RequestMapping("/api/patients")
public class PatientDocumentController {

	private final PatientLookupService patientLookupService;

	public PatientDocumentController(PatientLookupService patientLookupService) {
		this.patientLookupService = patientLookupService;
	}

	@GetMapping("/search")
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public List<PatientSummaryDto> search(
			@RequestParam(value = "name", required = false) String name,
			@RequestParam(value = "pid", required = false) String pid,
			@AuthenticationPrincipal SecurityUser user) {
		return patientLookupService.search(name, pid, user.getId());
	}

	@GetMapping("/by-pid/{pid}")
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public PatientDocumentDetail byPid(@PathVariable String pid,
			@AuthenticationPrincipal SecurityUser user) {
		return patientLookupService.detailByPid(pid, user.getId());
	}
}
