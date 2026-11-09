package com.example.medrecords.web;

import com.example.medrecords.dto.auth.UserResponse;
import com.example.medrecords.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Directory of doctors, available to authenticated doctors and patients.
     */
    @GetMapping("/doctors")
    public List<UserResponse> doctors() {
        return userService.listDoctors();
    }
}
