package com.mudhut.nudge.users.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "phone_verification_tokens")
public class PhoneVerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDateTime expiryDate;

    private boolean used = false;

    public PhoneVerificationToken() {
    }

    public PhoneVerificationToken(String code, User user) {
        this.code = code;
        this.user = user;
        this.expiryDate = LocalDateTime.now().plusMinutes(15); // Code valid for 15 minutes
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }
}