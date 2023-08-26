package dev.xhyrom.lighteco.common.model.user;

import dev.xhyrom.lighteco.common.api.impl.ApiUser;
import dev.xhyrom.lighteco.common.model.currency.Currency;
import dev.xhyrom.lighteco.common.plugin.LightEcoPlugin;
import lombok.Getter;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.UUID;

@Getter
public class User {
    private final LightEcoPlugin plugin;
    @Getter
    private final ApiUser proxy = new ApiUser(this);

    @Getter
    private final UUID uniqueId;
    @Getter
    @Setter
    private String username;
    private final HashMap<Currency, BigDecimal> balances = new HashMap<>();

    public User(LightEcoPlugin plugin, UUID uniqueId) {
        this(plugin, uniqueId, null);
    }

    public User(LightEcoPlugin plugin, UUID uniqueId, String username) {
        this.plugin = plugin;
        this.uniqueId = uniqueId;
        this.username = username;
    }

    public BigDecimal getBalance(@NonNull Currency currency) {
        return balances.getOrDefault(currency, currency.getDefaultBalance());
    }

    public void setBalance(@NonNull Currency currency, @NonNull BigDecimal balance) {
        balances.put(currency, balance);
    }

    public void invalidateCaches() {
        balances.clear();
    }
}
