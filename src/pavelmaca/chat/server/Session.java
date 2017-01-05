package pavelmaca.chat.server;

import pavelmaca.chat.share.Lambdas;
import pavelmaca.chat.share.comunication.ErrorResponse;
import pavelmaca.chat.share.comunication.Request;
import pavelmaca.chat.share.comunication.Response;
import pavelmaca.chat.server.entity.Message;
import pavelmaca.chat.server.entity.Room;
import pavelmaca.chat.server.entity.User;
import pavelmaca.chat.server.repository.MessageRepository;
import pavelmaca.chat.server.repository.RoomRepository;
import pavelmaca.chat.server.repository.UserRepository;
import pavelmaca.chat.share.model.MessageInfo;
import pavelmaca.chat.share.model.RoomInfo;
import pavelmaca.chat.share.model.RoomStatus;
import pavelmaca.chat.share.model.UserInfo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

/**
 * @author Pavel Máca <maca.pavel@gmail.com>
 */
public class Session implements Runnable {

    private User user;

    private States state;

    private Socket clientSocket;
    private ObjectInputStream inputStream = null;
    private ObjectOutputStream outputStream = null;

    private RoomManager roomManager;

    private UserRepository userRepository;
    private RoomRepository roomRepository;
    private MessageRepository messageRepository;

    private HashMap<Request.Types, Lambdas.Function1<Request>> requestHandlers = new HashMap<>();

    public Session(Socket clientSocket, RoomManager roomManager, UserRepository userRepository, RoomRepository roomRepository, MessageRepository messageRepository) {
        this.state = States.NEW;
        this.clientSocket = clientSocket;
        this.roomManager = roomManager;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.messageRepository = messageRepository;

        // Setup handlers
        requestHandlers.put(Request.Types.HAND_SHAKE, this::handleHandShake);
        requestHandlers.put(Request.Types.AUTHENTICATION, this::handleAuthentication);
        requestHandlers.put(Request.Types.CLOSE, request -> this.closeSession());
        requestHandlers.put(Request.Types.ROOM_GET_AVAILABLE_LIST, this::handleRetrieveAvailableRoomList);
        requestHandlers.put(Request.Types.ROOM_CREATE, this::handleCreateRoom);
        requestHandlers.put(Request.Types.MESSAGE_NEW, this::handleMessageReceiver);
        requestHandlers.put(Request.Types.USER_ROOM_JOIN, this::handleJoinRoom);
    }

    @Override
    public void run() {
        try {
            inputStream = new ObjectInputStream(clientSocket.getInputStream());
            outputStream = new ObjectOutputStream(clientSocket.getOutputStream());

            while (!clientSocket.isClosed()) {
                try {
                    Request request = (Request) inputStream.readObject();
                    synchronized (outputStream) { // prevent other thread send messages to client, until request is processed
                        processCommand(request);
                    }
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
            closeSession();
        } catch (IOException e) {
            e.printStackTrace();
            closeSession();
        }
    }

    private void processCommand(Request request) {
        // check access to request type
        if (Arrays.stream(state.getAllowedCommands()).noneMatch(x -> x == request.type)) {
            sendResponse(new ErrorResponse("No access to request: " + request.type));
            return;
        }

        if (!requestHandlers.containsKey(request.getType())) {
            sendResponse(new ErrorResponse("Unknown request type" + request.type));
            return;
        }

        // call handler to precess request
        Lambdas.Function1 handler = requestHandlers.get(request.getType());
        handler.apply(request);
    }

    private boolean sendResponse(Response response) {
        // print outgoing error responses
        if (response.getCode() == Response.Codes.ERROR) {
            System.out.println("user " + user.getId() + " - " + response.getBody());
        }

        try {
            synchronized (outputStream) {
                outputStream.writeObject(response);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }


    private void handleHandShake(Request request) {
        System.out.println("hand shake received");
        if (sendResponse(new Response(Response.Codes.OK))) {
            state = States.GUEST;
        }
    }

    private void handleAuthentication(Request request) {
        // TODO only one connection per user
        System.out.println("authentication request received");

        Response response = new Response(Response.Codes.OK);

        String username = request.getParam("username");
        String password = request.getParam("password");

        user = userRepository.authenticate(username, password);
        if (user == null) {
            sendResponse(new Response(Response.Codes.ERROR));
            return;
        }

        ArrayList<RoomStatus> activeRoomsStatus = new ArrayList<>();
        ArrayList<Room> activeRooms = roomRepository.getActiveRooms(user);
        activeRooms.forEach(room -> {
            activeRoomsStatus.add(getRoomStatus(room));
        });


        response.setBody(activeRoomsStatus);

        if (sendResponse(response)) {
            state = States.AUTHENTICATED;
        }
    }

    private RoomStatus getRoomStatus(Room room) {
        RoomThread roomThread = roomManager.joinRoomThread(room, user, this);
        Set<User> activeUsers = roomThread.getConnectedUsers();

        ArrayList<UserInfo> userInfos = new ArrayList<>();
        activeUsers.forEach(user -> {
            userInfos.add(user.getInfoModel());
        });

        RoomStatus roomStatus = new RoomStatus(room.getInfoModel(), userInfos);

        ArrayList<MessageInfo> messageHistory = messageRepository.getHistory(room, 50);
        messageHistory.forEach(roomStatus::addMessage);

        return roomStatus;
    }

    private void handleRetrieveAvailableRoomList(Request request) {
        System.out.println("room list request received");

        ArrayList<RoomInfo> roomList = roomRepository.getAllAvailable(user);
        Response response = new Response(Response.Codes.OK);
        response.setBody(roomList);
        sendResponse(response);
    }

    private void handleCreateRoom(Request request) {
        System.out.println("new room request received");

        String roomName = request.getParam("name");
        Room room = roomRepository.createRoom(roomName, user);
        roomRepository.joinRoom(room, user);

        roomManager.joinRoomThread(room, user, this);

        Response response = new Response(Response.Codes.OK);
        response.setBody(getRoomStatus(room));
        sendResponse(response);
    }

    private void handleMessageReceiver(Request request) {
        System.out.println("new message received");

        String text = request.getParam("text");
        int roomId = request.getParam("roomId");


        if (!roomManager.isConnected(user, roomId)) {
            // user is not connected to this room!
            System.out.println("User is not connected to room " + roomId);
            return;
        }

        RoomThread roomThread = roomManager.getThread(roomId);

        Message message = messageRepository.save(text, roomThread.getRoom(), user);
        if (message != null) {
            roomThread.receiveMessage(message);
        }

        System.out.println("from: " + user.getName() + " message: " + text + " room:" + roomId);
    }

    private void handleJoinRoom(Request request) {
        System.out.println("join room request recieved");

        int roomId = request.getParam("roomId");

        Room room = roomRepository.joinRoom(roomId, user);
        roomManager.joinRoomThread(room, user, this);

        Response response = new Response(Response.Codes.OK);
        response.setBody(getRoomStatus(room));
        sendResponse(response);
    }

    public void sendDisconect() {
        sendCommand(new Request(Request.Types.CLOSE));
    }

    public void sendCommand(Request request) {
        try {
            synchronized (outputStream) {
                outputStream.writeObject(request);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeSession() {
        System.out.println("closing session");

        try {
            if (inputStream != null)
                inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (outputStream != null)
                outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        roomManager.getAllConnectedThreads(user).parallelStream().forEach(roomThread -> {
            roomThread.disconnect(user);
            roomManager.purgeRoomThread(roomThread.getRoom());
        });
    }

    enum States {
        NEW(new Request.Types[]{
                Request.Types.HAND_SHAKE,
                Request.Types.CLOSE
        }),
        GUEST(new Request.Types[]{
                Request.Types.AUTHENTICATION,
                Request.Types.CLOSE
        }),

        AUTHENTICATED(new Request.Types[]{
                Request.Types.ROOM_CREATE,
                Request.Types.ROOM_GET_AVAILABLE_LIST,
                Request.Types.USER_ROOM_JOIN,
                Request.Types.MESSAGE_NEW,
                Request.Types.CLOSE,
        });

        protected Request.Types[] allowedCommands;

        States(Request.Types[] allowedCommands) {
            this.allowedCommands = allowedCommands;
        }

        public Request.Types[] getAllowedCommands() {
            return allowedCommands;
        }
    }
}
