package com.itchat.ITchat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Message {
    private final String user;
    private final String message;

    @JsonCreator
    public Message(@JsonProperty("user") String user, @JsonProperty("message") String message) {
        this.user = user;
        this.message = message;
    }

    public String getUser() {
        return user;
    }

    public String getMessage() {
        return message;
    }

    // Méthode pour sérialiser un objet Message en JSON
    public String toJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Méthode pour désérialiser une chaîne JSON en un objet Message
    public static Message fromJson(String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(json, Message.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
}