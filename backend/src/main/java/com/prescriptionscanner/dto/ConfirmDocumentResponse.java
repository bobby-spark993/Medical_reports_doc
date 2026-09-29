package com.prescriptionscanner.dto;

/** Result of POST /api/documents/confirm. */
public record ConfirmDocumentResponse(
		boolean ok,
		Long documentId,
		Long patientId,
		boolean patientCreated,
		String pid,
		String message) {
}
