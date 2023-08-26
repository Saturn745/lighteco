package dev.xhyrom.lighteco.api;

import dev.xhyrom.lighteco.api.manager.CommandManager;
import dev.xhyrom.lighteco.api.manager.CurrencyManager;
import dev.xhyrom.lighteco.api.manager.UserManager;
import dev.xhyrom.lighteco.api.platform.Platform;
import dev.xhyrom.lighteco.api.platform.PlayerAdapter;
import org.checkerframework.checker.nullness.qual.NonNull;

public interface LightEco {
    /**
     * Gets the {@link Platform}, which represents the current platform the
     * plugin is running on.
     *
     * @return the platform
     */
    @NonNull Platform getPlatform();

    @NonNull UserManager getUserManager();

    @NonNull CurrencyManager getCurrencyManager();
    @NonNull CommandManager getCommandManager();

    <T> @NonNull PlayerAdapter<T> getPlayerAdapter(@NonNull Class<T> playerClass);
}
