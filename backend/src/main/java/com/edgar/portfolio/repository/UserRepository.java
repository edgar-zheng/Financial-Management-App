package com.edgar.portfolio.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.edgar.portfolio.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByEmailIgnoreCase(String email);
}
