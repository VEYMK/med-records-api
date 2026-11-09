package com.example.medrecords.service;

import com.example.medrecords.domain.Role;
import com.example.medrecords.dto.auth.UserResponse;
import com.example.medrecords.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listDoctors() {
        return users.findByRoleOrderByFullNameAsc(Role.DOCTOR)
                .stream()
                .map(UserResponse::from)
                .toList();
    }
}
