import javafx.scene.shape.Polyline;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;

public class GameServer {
    private ArrayList<GameClientHandler> listOfGameClientHandlers = new ArrayList<>(); // Kinda like the observer pattern
    private byte numberOfPlayers;
    private boolean allReady, gameOver;
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
                ServerSocket serverSocket = new ServerSocket(8000); // Create a server socket
                System.out.println("GameServer started at " + new Date());

                while (true) {
                    Socket socket = serverSocket.accept(); // Listen for a new connection request
                    System.out.println("Player " + numberOfPlayers + " joined. Connection from " + socket + " at " + new Date());

                    // Create and start a new thread for the connection
                    GameClientHandler gameClientHandler = new GameClientHandler(socket, numberOfPlayers++);
                    readyPlayers = new boolean[numberOfPlayers];
                    directions = new byte[numberOfPlayers];
                    deadPlayers = new boolean[numberOfPlayers];
                    xCoordinates = new double[numberOfPlayers];
                    yCoordinates = new double[numberOfPlayers];

                    listOfGameClientHandlers.add(gameClientHandler);
                    new Thread(gameClientHandler).start();
                }
            } catch (IOException ex) {
                System.err.println(ex);
            }
        }).start();

        while (true) {
            if (allReady) {
                new GameEngine();
                break;
            }

//            System.out.println("Sleep");
            try {
                Thread.sleep(3000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("End main");
    }

    class GameEngine {
        private double speed = 3; // The number of pixels the line moves per calculation
        private double radius = 5; // The radius of the circle that makes up the front of the line the player controls
        private double width = 1000, height = 700; // Width and height of the pane. Used for checking whether a player hit a side
        private int[] angles = new int[numberOfPlayers];
        private Polyline[] polylines = new Polyline[numberOfPlayers];

        public GameEngine() {
            for (int i = 0; i < numberOfPlayers; i++) {
                xCoordinates[i] = 400 + (i * 50);
                yCoordinates[i] = 400 + (i * 50);
                Polyline polyline = new Polyline();
                polyline.setStrokeWidth(5);
                polylines[i] = polyline;
            }

            System.out.println("Start the game");
            start();
        }

        private void start() {
            while (!gameOver) {
                // Add previous coordinates to polylines
                for (int i = 0; i < numberOfPlayers; i++) {
                    if (!deadPlayers[i]) {
                        polylines[i].getPoints().add(xCoordinates[i]);
                        polylines[i].getPoints().add(yCoordinates[i]);
                    }
                }

                // Calculate new coordinates for all clients
                for (int i = 0; i < numberOfPlayers; i++) {
                    if (!deadPlayers[i]) {
                        byte angleChangeInDegrees = 6;
                        angles[i] += angleChangeInDegrees * directions[i];

                        xCoordinates[i] += Math.cos(angles[i] * Math.PI / 180) * speed;
                        yCoordinates[i] += Math.sin(angles[i] * Math.PI / 180) * speed;
                    }
                }

                for (int i = 0; i < numberOfPlayers; i++) {
                    // Can't do the "if (!deadPlayers[i])" check here, since you can't check if a set of coordinates contains a polyline. You can only check if a polyline contains a set of coordinates
                    for (int j = 0; j < numberOfPlayers; j++) {
                        if (!deadPlayers[j] && polylines[i].contains(xCoordinates[j], yCoordinates[j])) {
                            System.out.println("Player " + j + " dead");
                            deadPlayers[j] = true;
                        }
                    }

                    if (!deadPlayers[i]) {
                        if (xCoordinates[i] < radius || xCoordinates[i] > width - radius) {
                            System.out.println("Player " + i + " hit the right or left side");
                            deadPlayers[i] = true;
                        }

                        if (yCoordinates[i] < radius || yCoordinates[i] > height - radius) {
                            System.out.println("Player " + i + " hit the top or the bottom side");
                            deadPlayers[i] = true;
                        }
                    }
                }

                // Send new coordinates to all clients
                for (GameClientHandler client : listOfGameClientHandlers) {
                    client.sendCoordinates();
                }

                byte numberOfPlayersAlive = numberOfPlayersAlive();
                if (numberOfPlayers > 1 && (numberOfPlayersAlive == 1 || numberOfPlayersAlive == 0)) // If 1 player is alive, someone has one. If 0 players are alive, it's a draw
                    gameOver = true;
                else if (numberOfPlayers == 1 && numberOfPlayersAlive == 0) // If 1 player is playing, don't end the game until he dies
                    gameOver = true;

                // Pause the thread for a short while
                try {
                    Thread.sleep(25); // This determines how often coordinates are sent
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.println("Game over");
        }

        public byte numberOfPlayersAlive() {
            byte counter = 0;
            for (int i = 0; i < deadPlayers.length; i++) {
                if (!deadPlayers[i])
                    counter++;
            }
            return counter;
        }
    }

    class GameClientHandler implements Runnable {
        private Socket socket; // A connected socket
        private DataOutputStream dataOutputStream;
        private DataInputStream dataInputStream;
        private byte playerId;
        private boolean[] notifiedDeadPlayer; // This must be placed inside this class, as all clients need to know which players have been notified of their death

        public GameClientHandler(Socket socket, byte playerId) {
            this.socket = socket;
            this.playerId = playerId;
        }

        public void run() {
            try {
                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                dataInputStream = new DataInputStream(socket.getInputStream());

                dataOutputStream.writeByte(playerId);
                System.out.println("Player " + playerId + " waiting for ready");

                for (GameClientHandler gameClientHandler : listOfGameClientHandlers)
                    gameClientHandler.sendNumberOfPlayers();

                boolean ready = dataInputStream.readBoolean();
                readyPlayers[playerId] = ready; // Just writing "readyPlayers[playerId] = dataInputStream.readBoolean();" doesn't work and I have no idea why.
                System.out.println("Player " + playerId + " ready");

                for (GameClientHandler gameClientHandler : listOfGameClientHandlers)
                    gameClientHandler.sendPlayerReady(playerId);

                checkAllReady();

                while (!gameOver) {
                    directions[playerId] = dataInputStream.readByte();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        public void checkAllReady() {
            boolean allReadyLocal = true;

            for (int i = 0; i < readyPlayers.length; i++) {
                if (!readyPlayers[i]) {
                    allReadyLocal = false;
                    break;
                }
            }

            if (allReadyLocal) {
                for (GameClientHandler gameClientHandler : listOfGameClientHandlers)
                    gameClientHandler.sendAllReady();

                allReady = true;
                System.out.println("All players ready");
            }
        }

        public void sendPlayerReady(byte playerId) {
            try {
                dataOutputStream.writeByte(1);
                dataOutputStream.writeByte(playerId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void sendAllReady() {
            try {
                dataOutputStream.writeByte(2);
            } catch (Exception e) {
                e.printStackTrace();
            }

            notifiedDeadPlayer = new boolean[numberOfPlayers]; // Can't initialize this variable until the total number of players is known
        }

        public void sendNumberOfPlayers() {
            try {
                dataOutputStream.writeByte(0);
                dataOutputStream.writeByte(numberOfPlayers);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void sendCoordinates() {
            try {
                for (byte i = 0; i < numberOfPlayers; i++) {
                    if (!notifiedDeadPlayer[i]) { // Only send dead status and coordinates if the player has not been notified that he is dead
                        dataOutputStream.writeBoolean(deadPlayers[i]); // Notify the player whether he is dead
                        dataOutputStream.writeDouble(xCoordinates[i]); // Send new x coordinate
                        dataOutputStream.writeDouble(yCoordinates[i]); // Send new y coordinate
//                        System.out.println(xCoordinates[i] + ", " + yCoordinates[i]);

                        if (deadPlayers[i])
                            notifiedDeadPlayer[i] = true; // If the player is dead, he has been notified of this and we can stop sending him coordinates
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}