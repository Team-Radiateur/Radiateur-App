package dataAccess;

import model.Punishment;
import model.User;
import model.exceptions.CredentialsNotSetException;
import model.exceptions.CryptoException;
import model.exceptions.GetAllDataException;
import model.exceptions.UpdateDataException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;

public class UsersDBAccess implements IPlayerAccess {
    public UsersDBAccess() {}

    public ArrayList<User> getAllUsers() throws CredentialsNotSetException, GetAllDataException {
        ArrayList<User> users = new ArrayList<>();

        try {
            Connection dbConnection = DBConnection.getInstance();
            PreparedStatement getAllUsersStatement = dbConnection.prepareStatement("select * from whitelist;");
            ResultSet allUsersResultSet = getAllUsersStatement.executeQuery();

            while(allUsersResultSet.next()) {
                PreparedStatement getUsersPunishmentsStatement = dbConnection.prepareStatement("select * from PunishmentHistory where name = ?;");
                User user = new User(allUsersResultSet.getString("uuid"), allUsersResultSet.getString("name"), allUsersResultSet.getString("whitelisted").equals("true"));
                getUsersPunishmentsStatement.setString(1, user.getUsername());
                ResultSet usersPunishmentsSet = getUsersPunishmentsStatement.executeQuery();

                while(usersPunishmentsSet.next()) {
                    Punishment punishment = new Punishment(usersPunishmentsSet.getString("punishmentType"), usersPunishmentsSet.getString("reason"), usersPunishmentsSet.getString("operator"), new Date(usersPunishmentsSet.getLong("start")), new Date(usersPunishmentsSet.getLong("end")));
                    PreparedStatement getStillInStateStatement = dbConnection.prepareStatement("select * from Punishments where name = ? and reason = ? and punishmentType = ? and start = ?;");
                    getStillInStateStatement.setString(1, usersPunishmentsSet.getString("name"));
                    getStillInStateStatement.setString(2, usersPunishmentsSet.getString("reason"));
                    getStillInStateStatement.setString(3, usersPunishmentsSet.getString("punishmentType"));
                    getStillInStateStatement.setLong(4, usersPunishmentsSet.getLong("start"));
                    ResultSet getStillInStateSet = getStillInStateStatement.executeQuery();
                    punishment.setStillInPunishmentState(getStillInStateSet.next());
                    user.addPunishment(punishment);
                }

                users.add(user);
            }
        } catch (IOException | SQLException | ParserConfigurationException | CryptoException | SAXException exception) {
            throw new GetAllDataException("joueurs", exception.getMessage());
        }

        users.sort(Comparator.comparing((user) -> user.getUsername().toLowerCase()));

        return users;
    }

    private void addPunishment(Connection dbConnection, User user, String wantedBy, Punishment punishment, String table) throws SQLException {
        PreparedStatement setNewPunishmentStatement = dbConnection.prepareStatement("insert into " + table + "(`name`, `uuid`, `reason`, `operator`, `punishmentType`, `start`, `end`, `calculation`) values (?, ?, ?, ?, ?, ?, ?, ?)");
        setNewPunishmentStatement.setString(1, user.getUsername());
        setNewPunishmentStatement.setString(2, user.getUsername().toLowerCase());
        setNewPunishmentStatement.setString(3, punishment.getReason());
        setNewPunishmentStatement.setString(4, wantedBy);
        setNewPunishmentStatement.setString(5, punishment.getPunishmentType());
        setNewPunishmentStatement.setLong(6, punishment.getPunishmentStartTime().getTime());
        setNewPunishmentStatement.setLong(7, punishment.getPunishmentEndTime().getTime());
        setNewPunishmentStatement.setString(8, "");
        setNewPunishmentStatement.executeUpdate();
    }

    public void updateUser(User user) throws CredentialsNotSetException, UpdateDataException {
        try {
            Connection dbConnection = DBConnection.getInstance();
            PreparedStatement preparedStatement = dbConnection.prepareStatement("update whitelist set whitelisted = ? where uuid = ?");
            preparedStatement.setString(1, user.isWhitelisted().toString());
            preparedStatement.setString(2, user.getUUID());
            preparedStatement.executeUpdate();

            for (Punishment punishment : user.getPunishments()) {
                if (punishment.isNewlyCreated()) {
                    CredentialsXMLAccess credentialsAccess = new CredentialsXMLAccess();

                    if (punishment.getPunishmentType().endsWith("BAN") || punishment.getPunishmentType().endsWith("MUTE"))
                        addPunishment(dbConnection, user, credentialsAccess.getUsername(), punishment, "Punishments");

                    addPunishment(dbConnection, user, credentialsAccess.getUsername(), punishment, "PunishmentHistory");
                }
            }
        } catch (IOException | SQLException | ParserConfigurationException | CryptoException | SAXException exception) {
            throw new UpdateDataException(user.getUsername(), exception.getMessage());
        }
    }
}