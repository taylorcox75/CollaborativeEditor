package networking;
import model.User;
import model.UserAssistant;

import java.io.Serializable;
import java.util.*;

@SuppressWarnings("serial")
public class LoginPacket implements Serializable {
    String userName;
    byte[] password;
    String recover;

    public LoginPacket(String uname, byte[] pw) {
        this.userName = uname;
        this.password = pw;
    }

    public String getName() {
        return userName;
    }

    public byte[] getPassword() {
        return password;
    }

    public int execute(UserAssistant theUsers) {
        // 0: Login Success!
        // 1: User found, wrong password
        // 2: No account found, need to create one
        int toReturn = 2;

        ArrayList<User> knownUsers = theUsers.getUsers();
        for (int i = 0; i < knownUsers.size(); i++) {
            if (knownUsers.get(i).getUserName().equals(userName)) {
                toReturn = 1;

                if (new String(knownUsers.get(i).getPassword()).equals(new String(password))) {
                    toReturn = 0;
                }
            }

        }

        return toReturn;

    }
}
