package com.test.aieserver.domain.user.repository;

import com.test.aieserver.domain.question.Question;
import com.test.aieserver.domain.user.User;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User,Integer> {
    @NonNull
    Optional<User> findByNickname(String nickname);
}
