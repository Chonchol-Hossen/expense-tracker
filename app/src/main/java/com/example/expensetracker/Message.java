package com.example.expensetracker.model;

public class Message {
    private String messageText; // The message content
    private boolean isSentByUser; // Indicates if the message is from the user

    public Message(String messageText, boolean isSentByUser) {
        this.messageText = messageText;
        this.isSentByUser = isSentByUser;
    }

    public String getMessageText() {
        return messageText;
    }

    public boolean isSentByUser() {
        return isSentByUser;
    }
}