package dev.xhyrom.lighteco.api.model.user;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.UUID;

public interface User {
    /**
     * Get the unique id of this user.
     *
     * @return the unique id
     */
    @NonNull UUID getUniqueId();

    /**
     * Get the username of this user.
     *
     * @return the username
     */
    @NonNull String getUsername();
}
