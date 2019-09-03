import javafx.scene.shape.Polyline;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;

public class GameServer {
    private int portGame = 8000, portChat = 9000;
    private ArrayList<GameClientHandler> listOfGameClientHandlers = new ArrayList<>();
    private ArrayList<ChatClientHandler> listOfChatClientHandlers = new ArrayList<>();
    private byte numberOfPlayers;
    private boolean allReady;
    private byte[] directions;
    private boolean[] deadPlayers, readyPlayers;
    private double[] xCoordinates, yCoordinates;

    public static void main(String[] args) {
        new GameServer();
    }

    public GameServer() {
        // This thread stops the program from ever finishing
        new Thread(() -> {
            try {
                ServerSocket serverSocketGame = new ServerSocket(portGame); // Create a server socket
                System.out.println("Game server started at " + new Date());

                ServerSocket serverSocketChat = new ServerSocket(portChat);
                System.out.println("Chat server started at " + new Date());

                while (true) {
                    Socket socketGame = serverSocketGame.accept(); // Listen for a new connection request
                    System.out.println("Player " + numberOfPlayers + " joined game server. Connection from " + socketGame + " at " + new Date());

                    Socket socketChat = serverSocketChat.accept();
                    System.out.println("Player " + numberOfPlayers + " joined chat server. Connection from " + socketChat + " at " + new Date());

                    // Create and start a new thread for the game client
                    GameClientHandler gameClientHandler = new GameClientHandler(socketGame, numberOfPlayers++); // Postfix increment numberOfPlayers
                    initializeArrays(); // Since we can't know how many players will join the game, this must be done every time a player joins

                    listOfGameClientHandlers.add(gameClientHandler);
                    new Thread(gameClientHandler).start();

                    ChatClientHandler chatClientHandler = new ChatClientHandler(socketChat);
                    listOfChatClientHandlers.add(chatClientHandler);
                    new Thread(chatClientHandler).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        while (true) {
            if (allReady) { // Check whether the players are ready
                new GameEngine(); // Create a new GameEngine object
                break;
            }

            try {
                Thread.sleep(3000); // Sleep the loop. No need to check constantly
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("End main");
    }

    private void initializeArrays() {
        readyPlayers = new boolean[numberOfPlayers];
        directions = new byte[numberOfPlayers];
        deadPlayers = new boolean[numberOfPlayers];
        xCoordinates = new double[numberOfPlayers];
        yCoordinates = new double[numberOfPlayers];
    }

    class GameEngine implements GameConstants {
        private final double SPEED = 3; // The number of pixels the line moves per calculation
        private int[] angles = new int[numberOfPlayers];
        private Polyline[] polylines = new Polyline[numberOfPlayers];
        private byte roundsPlayed;

        public GameEngine() {
            while (roundsPlayed < ROUNDSTOTAL) {
                setStartingPoints();
                System.out.println("Start round " + roundsPlayed);
                startGameEngine();
                resetDataFields();

                try {
                    Thread.sleep(5000); // Time between rounds
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("Game over");
        }

        private void resetDataFields() {
            directions = new byte[numberOfPlayers];
            deadPlayers = new boolean[numberOfPlayers];
            xCoordinates = new double[numberOfPlayers];
            yCoordinates = new double[numberOfPlayers];

            for (GameClientHandler gameClientHandler : listOfGameClientHandlers)
                gameClientHandler.initializeArray();
        }

        private void setStartingPoints() {
            for (int i = 0; i < numberOfPlayers; i++) {
                xCoordinates[i] = (int) (100 + (Math.random() * (WIDTH - 200))); // Pick a random starting x coordinate, but make sure the player can turn if he is facing a side
                yCoordinates[i] = (int) (100 + (Math.random() * (HEIGHT - 200))); // Pick a random starting y coordinate, but make sure the player can turn if he is facing a side
                angles[i] = (int) (Math.random() * 361); // Pick a random starting angle
                Polyline polyline = new Polyline();
                polyline.setStrokeWidth(5); // Stroke width must be set to ensure the contains method works properly
                polylines[i] = polyline; // Initialize the polylines
            }
        }

        private void startGameEngine() {
            while (true) {
                // Add previous coordinates to polylines, which trace after the player
                for (int i = 0; i < numberOfPlayers; i++) {
                    if (!deadPlayers[i]) {
                        polylines[i].getPoints().add(xCoordinates[i]);
                        polylines[i].getPoints().add(yCoordinates[i]);
                    }
                }

                // Calculate new coordinates for all players
                for (int i = 0; i < numberOfPlayers; i++) {
                    if (!deadPlayers[i]) {
                        byte angleChangeInDegrees = 6;
                        angles[i] += angleChangeInDegrees * directions[i]; // Add the change in degrees to the angle

                        xCoordinates[i] += Math.cos(angles[i] * Math.PI / 180) * SPEED; // Calculate new x coordinate based on the change in angle and speed
                        yCoordinates[i] += Math.sin(angles[i] * Math.PI / 180) * SPEED; // Calculate new y coordinate based on the change in angle and speed
                    }
                }

                // Check if any player is dead
                checkForDeadPlayer();

                // Send new coordinates & dead/alive status to all players
                for (GameClientHandler client : listOfGameClientHandlers)
                    client.sendGameInfo();

                // Check if the game is over
                byte numberOfPlayersAlive = getNumberOfPlayersAlive();
                if (numberOfPlayers > 1 && (numberOfPlayersAlive == 1 || numberOfPlayersAlive == 0)) // If 1 player is alive, someone has one. If 0 players are alive, it's a draw
                    break;
                else if (numberOfPlayers == 1 && numberOfPlayersAlive == 0) // If 1 player is playing, don't end the game until he dies
                    break;

                // Pause the thread for a short while. This determines how often game info is calculated and sent
                try {
                    Thread.sleep(25);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.println("Round " + roundsPlayed++ + " over");
        }

        private void checkForDeadPlayer() {
            for (int i = 0; i < numberOfPlayers; i++) {
                if (!deadPlayers[i]) { // Only check if the player isn't already dead
                    if (xCoordinates[i] < RADIUS || xCoordinates[i] > WIDTH - RADIUS) {
                        System.out.println("Player " + i + " hit the right or left side");
                        deadPlayers[i] = true;
                    } else if (yCoordinates[i] < RADIUS || yCoordinates[i] > HEIGHT - RADIUS) {
                        System.out.println("Player " + i + " hit the top or the bottom side");
                        deadPlayers[i] = true;
                    } else
                        for (int j = 0; j < numberOfPlayers; j++)
                            if (polylines[j].contains(xCoordinates[i], yCoordinates[i])) {
                                System.out.println("Player " + i + " collided with the line of player " + j);
                                deadPlayers[i] = true;
                            }
                }
            }
        }

        // I guess this method can be replaced by a simple variable
        private byte getNumberOfPlayersAlive() {
            byte counter = 0;
            for (int i = 0; i < numberOfPlayers; i++) {
                if (!deadPlayers[i])
                    counter++;
            }
            return counter;
        }
    }

    class GameClientHandler implements Runnable {
        private Socket socketGame; // A connected socket
        private DataOutputStream dataOutputStream;
        private DataInputStream dataInputStream;
        private byte playerId;
        private boolean[] notifiedDeadPlayer;

        public GameClientHandler(Socket socketGame, byte playerId) {
            this.socketGame = socketGame;
            this.playerId = playerId;
        }

        public void run() {
            try {
                initializeStreams();
                sendPlayerInfo();
                receiveDirection();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        private void initializeStreams() throws IOException {
            dataOutputStream = new DataOutputStream(socketGame.getOutputStream());
            dataInputStream = new DataInputStream(socketGame.getInputStream());
        }

        private void sendPlayerInfo() throws IOException {
            // Send player ID
            dataOutputStream.writeByte(playerId);
            System.out.println("Player " + playerId + " waiting for ready");

            // Send number of players
            for (GameClientHandler gameClientHandler : listOfGameClientHandlers)
                gameClientHandler.sendNumberOfPlayers();

            // Wait for player to signal he is ready
            boolean ready = dataInputStream.readBoolean();
            readyPlayers[playerId] = ready; // Just writing "readyPlayers[playerId] = dataInputStream.readBoolean();" doesn't work and I have no idea why.
            System.out.println("Player " + playerId + " ready");

            // Tell all players that this player is ready
            for (GameClientHandler gameClientHandler : listOfGameClientHandlers)
                gameClientHandler.sendPlayerReady(playerId);

            // Check if all players are ready
            checkAllReady();
        }

        private void receiveDirection() throws IOException {
            while (true) {
                byte direction = dataInputStream.readByte();
                directions[playerId] = direction; // Just writing "directions[playerId] = dataInputStream.readByte();" doesn't work properly for some reason
            }
        }

        private void checkAllReady() {
            boolean allReadyLocal = true;

            for (int i = 0; i < numberOfPlayers; i++) {
                if (!readyPlayers[i]) {
                    allReadyLocal = false;
                    break;
                }
            }

            if (allReadyLocal) {
                for (GameClientHandler gameClientHandler : listOfGameClientHandlers) {
                    gameClientHandler.sendAllReady();
                    gameClientHandler.initializeArray();
                }

                allReady = true;
                System.out.println("All players ready");
            }
        }

        private void initializeArray() {
            notifiedDeadPlayer = new boolean[numberOfPlayers]; // Can't initialize this variable until the total number of players is known
        }

        private void sendPlayerReady(byte playerId) {
            try {
                dataOutputStream.writeByte(1);
                dataOutputStream.writeByte(playerId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void sendAllReady() {
            try {
                dataOutputStream.writeByte(2);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void sendNumberOfPlayers() {
            try {
                dataOutputStream.writeByte(0);
                dataOutputStream.writeByte(numberOfPlayers);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * This method sends the following to every player:
         * 1) Info about the alive/dead status of every player
         * 2) The coordinates of every player
         * <p>
         * If a player dies, the alive/dead status and coordinates of this player is sent one more time and then not anymore.
         */
        private void sendGameInfo() {
            try {
                for (byte i = 0; i < numberOfPlayers; i++) {
                    if (!notifiedDeadPlayer[i]) { // Only send death status and coordinates if players have not been notified that a player is dead
                        dataOutputStream.writeBoolean(deadPlayers[i]); // Notify the player whether he is dead
                        dataOutputStream.writeDouble(xCoordinates[i]); // Send new x coordinate
                        dataOutputStream.writeDouble(yCoordinates[i]); // Send new y coordinate

                        if (deadPlayers[i])
                            notifiedDeadPlayer[i] = true; // If the player is dead, he has been notified of this and we can stop sending
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    class ChatClientHandler implements Runnable {
        private Socket socket; // A connected socket
        private DataOutputStream dataOutputStream;
        private DataInputStream dataInputStream;

        public ChatClientHandler(Socket socket) {
            this.socket = socket;
            initializeStreams();
        }

        private void initializeStreams() {
            try {
                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                dataInputStream = new DataInputStream(socket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            while (true) {
                try {
                    // Get a chat message
                    String chatMessage = dataInputStream.readUTF();

                    // Send chat message to all clients
                    for (ChatClientHandler chatClientHandler : listOfChatClientHandlers)
                        chatClientHandler.receiveChatMessage(chatMessage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public void receiveChatMessage(String text) {
            try {
                dataOutputStream.writeUTF(text);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}