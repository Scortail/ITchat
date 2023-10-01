package com.itchat.ITchat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Representation d'un message
 */
public class Message {

    private final String user;
    private final String message;
    private String type; // pm : private message, gm : global message, cm : connexion message, dm :
                         // disconnection message
    private String destinataire;

    @JsonCreator
    public Message(@JsonProperty("user") String user, @JsonProperty("message") String message,
            @JsonProperty("type") String type, @JsonProperty("destinataire") String destinataire) {
        this.user = user;
        this.message = message;
        this.type = type;
        this.destinataire = destinataire;
    }

    public String getUser() {
        return user;
    }

    public String getMessage() {
        return message;
    }

    public String getType() {
        return type;
    }

    public String getDestinataire() {
        return destinataire;
    }

    /**
     * Sérialise un objet Message en JSON
     */
    public String toJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Désérialise une chaîne JSON en un objet Message
     */
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