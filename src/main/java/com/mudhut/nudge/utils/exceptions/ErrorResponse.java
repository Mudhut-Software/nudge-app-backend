package com.mudhut.nudge.utils.exceptions;

public class ErrorResponse {
    private String code;
    private Object message;

    public ErrorResponse(String code, Object message) {
        this.code = code;
        this.message = message;
    }

    // Getters
    public String getCode() {
        return code;
    }

    public Object getMessage() {
        return message;
    }
}
