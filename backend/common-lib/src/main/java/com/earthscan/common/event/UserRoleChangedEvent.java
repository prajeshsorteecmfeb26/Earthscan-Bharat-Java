package com.earthscan.common.event;

/** Published by auth-service when an administrator changes a user's role. */
public class UserRoleChangedEvent extends IntegrationEvent {

    private Long userId;
    private String name;
    private String email;
    private String previousRole;
    private String newRole;

    public UserRoleChangedEvent() {
    }

    public UserRoleChangedEvent(Long userId, String name, String email,
                               String previousRole, String newRole) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.previousRole = previousRole;
        this.newRole = newRole;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPreviousRole() {
        return previousRole;
    }

    public void setPreviousRole(String previousRole) {
        this.previousRole = previousRole;
    }

    public String getNewRole() {
        return newRole;
    }

    public void setNewRole(String newRole) {
        this.newRole = newRole;
    }
}
