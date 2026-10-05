package com.prescriptionscanner.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prescriptionscanner.domain.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}