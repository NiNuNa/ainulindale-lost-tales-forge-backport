package com.ninuna.losttales.entity;

public enum ELostTalesUser {
    //  LostTales - Team.
    NINUNA("NiNuNa", "42c208f1-bdde-445b-91f6-b76a3606f333"),
    SCOSHER("Scosher", "d0269a66-bbce-4123-bbc4-472623201eda"),
    BALARAUKO("Balarauko", "e1968bbb-813c-425a-998e-3f75e8aa1b68"),
    CAPTAIN_CHEESE("captainCheese", "d36e696d-dbbe-48ed-a878-bc8eb480a29c"),

    //  LostTales - Community.


    //  Empty User - No Credits.
    NULL("", "");

    private final String name;
    private final String uuid;

    ELostTalesUser(String name, String uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public String getUuid() {
        return uuid;
    }
}