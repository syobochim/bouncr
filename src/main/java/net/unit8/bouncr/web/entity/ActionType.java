package net.unit8.bouncr.web.entity;

import org.seasar.doma.Domain;

import java.util.Objects;
import java.util.stream.Stream;

@Domain(valueType = Long.class, factoryMethod = "of", accessorMethod = "getId")
public enum ActionType {
    USER_SIGNIN(1L, "user.signin"),
    USER_FAILED_SIGNIN(2L, "user.failed_signin"),
    CHANGE_PASSWORD(3L, "user.change_password");

    ActionType(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    private String name;
    private Long id;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static ActionType of(Long id) {
        return Stream.of(ActionType.values())
                .filter(type -> Objects.equals(type.getId(), id))
                .findAny()
                .orElseGet(null);
    }
}
