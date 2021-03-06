package pavelmaca.chat.client.gui.components;

import pavelmaca.chat.client.gui.renderer.MessageListRenderer;
import pavelmaca.chat.server.entity.User;
import pavelmaca.chat.share.Factory;
import pavelmaca.chat.share.Lambdas;
import pavelmaca.chat.share.model.MessageInfo;
import pavelmaca.chat.share.model.RoomStatus;
import pavelmaca.chat.share.model.UserInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;

/**
 * Chat list component
 *
 * @author Pavel Máca <maca.pavel@gmail.com>
 */
public class ChatList implements IComponent<JPanel> {
    private JList<MessageInfo> chatJList;

    private JTextField message = new JTextField();
    private JButton sendBtn = new JButton("Send");
    private JPanel panel;

    private UserInfo currentUser;


    public ChatList() {
        //setup GUI
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        panel.setPreferredSize(new Dimension(400, 300));

        chatJList = new JList<>();
        chatJList.setEnabled(false);

        chatJList.setFixedCellHeight(25);
        chatJList.setFixedCellWidth(100);
        chatJList.setCellRenderer(new MessageListRenderer(currentUser));
        chatJList.addComponentListener(new ComponentAdapter() {
            /**
             * https://stackoverflow.com/questions/7306295/swing-jlist-with-multiline-text-and-dynamic-height
             * @param e
             */
            @Override
            public void componentResized(ComponentEvent e) {
                // for core: force cache invalidation by temporarily setting fixed height
                chatJList.setFixedCellHeight(10);
                chatJList.setFixedCellHeight(-1);
            }
        });

        Dimension minimumSize = new Dimension(300, 25);
        chatJList.setMinimumSize(minimumSize);
        panel.setMinimumSize(minimumSize);

        //Lay out the buttons from left to right.
        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.LINE_AXIS));
        buttonPane.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        buttonPane.add(Box.createHorizontalGlue());
        buttonPane.add(message);
        buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));
        buttonPane.add(sendBtn);

        panel.add(new JScrollPane(chatJList), BorderLayout.CENTER);
        panel.add(buttonPane, BorderLayout.PAGE_END);
    }

    @Override
    public JPanel getComponent() {
        return panel;
    }

    /**
     * @param handler Action performed after valid message is submitted
     */
    public void addMessageSubmitListener(Lambdas.Function1<String> handler) {
        Action action = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // validation
                if (!message.getText().isEmpty()) {
                    handler.apply(message.getText());
                    message.setText("");
                }
            }
        };

        message.addActionListener(action);
        sendBtn.addActionListener(action);
    }

    /**
     * @param messages List of messages to show in GUI
     */
    public void show(java.util.List<MessageInfo> messages) {
        DefaultListModel<MessageInfo> chatListModel = new DefaultListModel<>();
        messages.forEach(chatListModel::addElement);
        chatJList.setModel(chatListModel);
        chatJList.ensureIndexIsVisible(chatListModel.size() - 1);
    }

    /**
     * @param currentUser Set current user for the message formatting
     */
    public void setCurrentUser(UserInfo currentUser) {
        this.currentUser = currentUser;
    }
}
