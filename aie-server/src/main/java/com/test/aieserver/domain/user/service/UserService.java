package com.test.aieserver.domain.user.service;

import com.test.aieserver.domain.user.User;
import com.test.aieserver.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User makeUserIfNotPresent(String nickname) {
        return userRepository.findByNickname(nickname)
                .orElseGet(() -> userRepository.save(User.builder()
                        .nickname(nickname)
                        .userQuestionSet(new HashSet<>())
                        .build()));
    }
}

