package com.prescriptionscanner.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.prescriptionscanner.domain.Role;
import com.prescriptionscanner.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	List<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email, Sort sort);

	long countByRole(Role role);
}
