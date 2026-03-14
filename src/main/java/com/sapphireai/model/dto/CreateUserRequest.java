package com.sapphireai.model.dto;

import lombok.Data;

@Data
public class CreateUserRequest {
    private String email;
    private String fullName;
    private String role;
}