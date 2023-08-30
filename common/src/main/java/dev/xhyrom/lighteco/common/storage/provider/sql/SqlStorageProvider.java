package dev.xhyrom.lighteco.common.storage.provider.sql;

import dev.xhyrom.lighteco.api.model.user.User;
import dev.xhyrom.lighteco.api.storage.StorageProvider;
import dev.xhyrom.lighteco.common.model.currency.Currency;
import dev.xhyrom.lighteco.common.plugin.LightEcoPlugin;
import dev.xhyrom.lighteco.common.storage.StorageType;
import dev.xhyrom.lighteco.common.storage.provider.sql.connection.ConnectionFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class SqlStorageProvider implements StorageProvider {
    private static String SAVE_USER_LOCAL_CURRENCY;
    private static String SAVE_USER_GLOBAL_CURRENCY;
    private static String LOAD_WHOLE_USER;
    private static final String GET_TOP_X_USERS_LOCAL = "SELECT uuid, balance FROM {prefix}_{context}_users WHERE currency_identifier = ? ORDER BY balance DESC LIMIT ?;";
    private static final String GET_TOP_X_USERS_GLOBAL = "SELECT uuid, balance FROM {prefix}_users WHERE currency_identifier = ? ORDER BY balance DESC LIMIT ?;";
    private static final String DELETE_GLOBAL_USER_IF_BALANCE = "DELETE FROM {prefix}_{context}_users WHERE uuid = ? AND currency_identifier = ? AND balance = ?;";
    private static final String DELETE_LOCAL_USER_IF_BALANCE = "DELETE FROM {prefix}_{context}_users WHERE uuid = ? AND currency_identifier = ? AND balance = ?;";

    private final LightEcoPlugin plugin;
    private final ConnectionFactory connectionFactory;
    private final Function<String, String> statementProcessor;

    public SqlStorageProvider(LightEcoPlugin plugin, ConnectionFactory connectionFactory) {
        this.plugin = plugin;
        this.connectionFactory = connectionFactory;
        this.statementProcessor = connectionFactory.getStatementProcessor().compose(
                s -> s
                        .replace("{prefix}", plugin.getConfig().storage.tablePrefix)
                        .replace("{context}", plugin.getConfig().server)
        );

        final StorageType implementationName = this.connectionFactory.getImplementationName();
        SAVE_USER_LOCAL_CURRENCY = SqlStatements.SAVE_USER_LOCAL_CURRENCY.get(implementationName);
        SAVE_USER_GLOBAL_CURRENCY = SqlStatements.SAVE_USER_GLOBAL_CURRENCY.get(implementationName);
        LOAD_WHOLE_USER = SqlStatements.LOAD_WHOLE_USER.get(implementationName);
    }

    @Override
    public void init() throws Exception {
        this.connectionFactory.init(this.plugin);

        List<String> statements;
        String schemaFileName = "schema/" + this.connectionFactory.getImplementationName().name().toLowerCase() + ".sql";
        try (InputStream is = this.plugin.getBootstrap().getResourceStream(schemaFileName)) {
            if (is == null)
                throw new IOException("Failed to load schema file: " + schemaFileName);

            statements = SchemaReader.getStatements(is).stream()
                    .map(this.statementProcessor)
                    .toList();
        }

        try (Connection c = this.connectionFactory.getConnection()) {
            try (Statement s = c.createStatement()) {
                for (String statement : statements) {
                    s.addBatch(statement);
                }

                s.executeBatch();
            }
        }
    }

    @Override
    public void shutdown() throws Exception {
        this.connectionFactory.shutdown();
    }

    @Override
    public @NonNull User loadUser(@NonNull UUID uniqueId, @Nullable String username) throws Exception {
        String uniqueIdString = uniqueId.toString();
        dev.xhyrom.lighteco.common.model.user.User user = this.plugin.getUserManager().getOrMake(uniqueId);
        if (username != null)
            user.setUsername(username);

        try (Connection c = this.connectionFactory.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(this.statementProcessor.apply(LOAD_WHOLE_USER))) {
                ps.setString(1, uniqueIdString);
                if (SqlStatements.mustDuplicateParameters(this.connectionFactory.getImplementationName()))
                    ps.setString(2, uniqueIdString);

                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    String currencyIdentifier = rs.getString("currency_identifier");
                    Currency currency = this.plugin.getCurrencyManager().getIfLoaded(currencyIdentifier);

                    BigDecimal balance = rs.getBigDecimal("balance");

                    user.setBalance(currency, balance);
                }
            }
        }

        return user.getProxy();
    }

    @Override
    public void saveUser(@NonNull User user) throws Exception {
        String uniqueIdString = user.getUniqueId().toString();

        try (Connection c = this.connectionFactory.getConnection()) {
            try {
                saveBalances(c, user, uniqueIdString);
            } catch (SQLException e) {
                throw new SQLException("Failed to save user " + user.getUniqueId(), e);
            }
        }
    }

    @Override
    public void saveUsers(@NotNull @NonNull User... users) throws Exception {
        // use transaction
        try (Connection c = this.connectionFactory.getConnection()) {
            try {
                c.setAutoCommit(false);

                for (User user : users) {
                    String uniqueIdString = user.getUniqueId().toString();

                    saveBalances(c, user, uniqueIdString);
                }

                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Override
    public @NonNull List<User> getTopUsers(dev.xhyrom.lighteco.api.model.currency.Currency apiCurrency, int length) throws Exception {
        Currency currency = this.plugin.getCurrencyManager().getIfLoaded(apiCurrency.getIdentifier());
        String statement = currency.getType() == dev.xhyrom.lighteco.api.model.currency.Currency.Type.GLOBAL
                ? GET_TOP_X_USERS_GLOBAL
                : GET_TOP_X_USERS_LOCAL;

        try (Connection c = this.connectionFactory.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(this.statementProcessor.apply(statement))) {
                ps.setString(1, currency.getIdentifier());
                ps.setInt(2, length);

                ResultSet rs = ps.executeQuery();

                List<User> users = new ArrayList<>();
                while (rs.next()) {
                    String uniqueIdString = rs.getString("uuid");
                    UUID uniqueId = UUID.fromString(uniqueIdString);

                    BigDecimal balance = rs.getBigDecimal("balance");

                    dev.xhyrom.lighteco.common.model.user.User user = this.plugin.getUserManager().getOrMake(uniqueId);
                    user.setBalance(currency, balance);

                    users.add(user.getProxy());
                }

                return users;
            }
        }
    }

    private void saveBalances(Connection c, User user, String uniqueIdString) throws SQLException {
        try (PreparedStatement psGlobal = c.prepareStatement(this.statementProcessor.apply(SAVE_USER_GLOBAL_CURRENCY));
             PreparedStatement psLocal = c.prepareStatement(this.statementProcessor.apply(SAVE_USER_LOCAL_CURRENCY));
             PreparedStatement psDeleteGlobal = c.prepareStatement(this.statementProcessor.apply(DELETE_GLOBAL_USER_IF_BALANCE));
             PreparedStatement psDeleteLocal = c.prepareStatement(this.statementProcessor.apply(DELETE_LOCAL_USER_IF_BALANCE))) {

            for (Currency currency : this.plugin.getCurrencyManager().getRegisteredCurrencies()) {
                BigDecimal balance = user.getBalance(currency.getProxy());

                if (balance.compareTo(BigDecimal.ZERO) == 0) {
                    switch (currency.getType()) {
                        case GLOBAL -> {
                            psDeleteGlobal.setString(1, uniqueIdString);
                            psDeleteGlobal.setString(2, currency.getIdentifier());
                            psDeleteGlobal.setBigDecimal(3, balance);

                            psDeleteGlobal.addBatch();
                        }
                        case LOCAL -> {
                            psDeleteLocal.setString(1, uniqueIdString);
                            psDeleteLocal.setString(2, currency.getIdentifier());
                            psDeleteLocal.setBigDecimal(3, balance);

                            psDeleteLocal.addBatch();
                        }
                    }

                    continue;
                }

                switch (currency.getType()) {
                    case GLOBAL -> {
                        psGlobal.setString(1, uniqueIdString);
                        psGlobal.setString(2, currency.getIdentifier());
                        psGlobal.setBigDecimal(3, balance);
                        if (SqlStatements.mustDuplicateParameters(this.connectionFactory.getImplementationName()))
                            psGlobal.setBigDecimal(4, balance);

                        psGlobal.addBatch();
                    }
                    case LOCAL -> {
                        psLocal.setString(1, uniqueIdString);
                        psLocal.setString(2, currency.getIdentifier());
                        psLocal.setBigDecimal(3, balance);
                        if (SqlStatements.mustDuplicateParameters(this.connectionFactory.getImplementationName()))
                            psLocal.setBigDecimal(4, balance);

                        psLocal.addBatch();
                    }
                }
            }

            psGlobal.executeBatch();
            psLocal.executeBatch();
            psDeleteGlobal.executeBatch();
            psDeleteLocal.executeBatch();
        } catch (SQLException e) {
            throw new SQLException("Failed to save user " + user.getUniqueId(), e);
        }
    }
}
