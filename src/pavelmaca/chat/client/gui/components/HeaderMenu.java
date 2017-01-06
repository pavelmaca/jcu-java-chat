package pavelmaca.chat.client.gui.components;

import pavelmaca.chat.share.Factory;

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * Created by Assassik on 05.01.2017.
 */
public class HeaderMenu implements Factory<JMenuBar> {

    JMenuItem disconnectItem = new JMenuItem("Disconnect");
    JMenuItem logoutItem = new JMenuItem("Logout");
    JMenuItem changePasswordItem = new JMenuItem("Change password");

    @Override
    public JMenuBar create() {
        JMenuBar menuBar = new JMenuBar();

        JMenu server = new JMenu("Server");
        server.add(disconnectItem);
        menuBar.add(server);

        JMenu user = new JMenu("User");
        user.add(changePasswordItem);
        user.add(logoutItem);
        menuBar.add(user);

        return menuBar;
    }

    public void addDisconnectActionListener(ActionListener listener) {
        disconnectItem.addActionListener(listener);
    }

    public void addLogoutActionListener(ActionListener listener) {
        logoutItem.addActionListener(listener);
    }

    public void addChangePasswordActionListener(ActionListener listener) {
        changePasswordItem.addActionListener(listener);
    }
}