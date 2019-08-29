import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polyline;
import javafx.stage.Stage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class GameClient extends Application {
    private String hostGame = "localhost";
    private int portGame = 8000;
    private DataOutputStream dataOutputStreamGame;
    private DataInputStream dataInputStreamGame;
    private GridPane gridPanePlayers = new GridPane();
    private TextArea textAreaChat = new TextArea();
    private TextField textFieldChat = new TextField();
    Circle circleConnected;

    @Override // Override the start method in the Application class
    public void start(Stage primaryStage) {
        GameView gameView = new GameView();
        Pane pane = gameView.getPane();

        gridPanePlayers.setPadding(new Insets(10));
        gridPanePlayers.setHgap(10);
        gridPanePlayers.setStyle("-fx-border-color: black");
        gridPanePlayers.setPrefHeight(275);

        TextField textFieldHost = new TextField();
        textFieldHost.setPromptText("Enter host IP");
        TextField textFieldPort = new TextField();
        textFieldPort.setPromptText("Enter host port");
        Button buttonConnect = new Button("Connect");
        buttonConnect.setPrefWidth(70);
        Button buttonReady = new Button("Ready");
        buttonReady.setPrefWidth(70);
        circleConnected = new Circle(8, Color.RED);

        GridPane gridPaneConnection = new GridPane();
//        gridPaneConnection.setStyle("-fx-border-color: black");
//        gridPaneConnection.setPrefHeight(100);
        gridPaneConnection.setHgap(5);
        gridPaneConnection.setVgap(10);
        gridPaneConnection.addRow(0, textFieldHost, textFieldPort, buttonConnect, circleConnected);
        gridPaneConnection.add(buttonReady, 2, 1);

        textAreaChat.setEditable(false);
        textAreaChat.setPrefHeight(300);
        textAreaChat.setFocusTraversable(false);

        textFieldChat.setPromptText("Enter chat message and hit enter");

        VBox vBoxRightSide = new VBox();
        vBoxRightSide.setSpacing(10);
        vBoxRightSide.getChildren().addAll(gridPanePlayers, gridPaneConnection, textAreaChat, textFieldChat);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(pane, vBoxRightSide);

        // Create a scene and place it in the stage
        Scene scene = new Scene(hBox);
        primaryStage.setTitle("Client"); // Set the stage title
        primaryStage.setScene(scene); // Place the scene in the stage
        primaryStage.show(); // Display the stage

        buttonConnect.requestFocus();

        buttonConnect.setOnAction(event -> {
            hostGame = textFieldHost.getText().length() > 0 ? textFieldHost.getText() : "localhost";
            portGame = textFieldPort.getText().length() > 0 ? Integer.parseInt(textFieldPort.getText()) : 8000;

            new Thread(gameView).start();
            buttonReady.requestFocus();
        });

        buttonReady.setOnAction(event -> {
            try {
                dataOutputStreamGame.writeBoolean(true);
            } catch (Exception e) {
                e.printStackTrace();
            }

            pane.requestFocus();
        });
    }

    class GameView implements Runnable {
        private Pane pane = new Pane();
        private Label labelId = new Label("ID"), labelReady = new Label("Ready?"), labelAlive = new Label("Alive?");
        private Circle[] circles;
        private Polyline[] polylines;
        private byte numberOfPlayers, playerId;
        private double radius = 5, width = 1000, height = 700;
        private double[] xCoordinates, yCoordinates;
        private boolean keydownLeft, keydownRight, allReady;
        private boolean[] deadPlayers, readyPlayers;
        private Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.PURPLE, Color.PINK};

        public GameView() {
            pane.setPrefSize(width, height);
//            pane.setStyle("-fx-background-color: grey; -fx-border-color: black; -fx-border-width: 3");
            pane.setStyle("-fx-border-color: black; -fx-border-width: 3");

//            labelId.setStyle("-fx-border-color: black");
            labelId.setStyle("-fx-font-size: 20; -fx-font-weight: bold");
            labelId.setPrefWidth(100);
            labelReady.setStyle("-fx-font-size: 20; -fx-font-weight: bold");
            labelReady.setPrefWidth(100);
            labelAlive.setStyle("-fx-font-size: 20; -fx-font-weight: bold");
            labelAlive.setPrefWidth(100);

            drawSidebar();
        }

        public void drawSidebar() {
            Platform.runLater(() -> {
                gridPanePlayers.getChildren().clear();
                gridPanePlayers.addRow(0, labelId, labelReady, labelAlive);
                for (int i = 0; i < numberOfPlayers; i++) {
                    Label label1 = new Label("Player " + i);
                    label1.setTextFill(colors[i]);
                    label1.setStyle("-fx-font-size: 20;");
                    Label label2 = new Label(readyPlayers[i] ? "Yes" : "No");
                    label2.setTextFill(readyPlayers[i] ? Color.GREEN : Color.RED);
                    label2.setStyle("-fx-font-size: 20;");

                    Label label3;
                    if (allReady) {
                        label3 = new Label(deadPlayers[i] ? "No" : "Yes");
                        label3.setTextFill(deadPlayers[i] ? Color.RED : Color.GREEN);
                    } else { // The deadPlayers array is null until the game starts, so assume everyone is alive
                        label3 = new Label("Yes");
                        label3.setTextFill(Color.GREEN);
                    }
                    label3.setStyle("-fx-font-size: 20;");

                    gridPanePlayers.addRow(i + 1, label1, label2, label3);
                }
            });
        }

        @Override
        public void run() {
            try {
                Socket socketGame = new Socket(hostGame, portGame); // Create a socket to connect to the server
                circleConnected.setFill(Color.GREEN);

                dataOutputStreamGame = new DataOutputStream(socketGame.getOutputStream());
                dataInputStreamGame = new DataInputStream(socketGame.getInputStream());

                byte id = dataInputStreamGame.readByte();
                playerId = id;
                System.out.println("Player number " + id);

                System.out.println("Waiting for all ready");
                while (true) {
                    byte command = dataInputStreamGame.readByte();
                    if (command == 0) { // 0 = receive number of players
                        numberOfPlayers = dataInputStreamGame.readByte();
                        readyPlayers = new boolean[numberOfPlayers]; // TODO: Copy the content of the old array (if you want players to be able to ready up before all players have joined)
                        drawSidebar();
                        System.out.println("Number of players " + numberOfPlayers);
                    } else if (command == 1) { // 1 = player ready
                        byte readyPlayerId = dataInputStreamGame.readByte();
                        readyPlayers[readyPlayerId] = true;
                        drawSidebar();
                        System.out.println("Player " + readyPlayerId + " ready");
                    } else if (command == 2) { // 2 = all players ready
                        allReady = true;
                        break;
                    }
                }
                System.out.println("All ready");

                deadPlayers = new boolean[numberOfPlayers];
                xCoordinates = new double[numberOfPlayers];
                yCoordinates = new double[numberOfPlayers];

                circles = new Circle[numberOfPlayers];
                polylines = new Polyline[numberOfPlayers];

                for (int i = 0; i < numberOfPlayers; i++) {
                    circles[i] = new Circle(radius, Color.YELLOW);
                    Polyline polyline = new Polyline();
                    polyline.setStroke(colors[i]);
//                    polyline.setStroke(new Color(Math.random(), Math.random(), Math.random(), 1));
//                    polyline.setStroke(new Color(0.2 * (i + 1) % 1, 0.3 * (i + 1) % 1, 0.4 * (i + 1) % 1, 1));
                    polyline.setStrokeWidth(5);
                    polylines[i] = polyline;
                }

                System.out.println("Activate controls");
                activateControls();

                System.out.println("Start getting coordinates");
                while (true) {
                    for (int i = 0; i < numberOfPlayers; i++) {
                        if (!deadPlayers[i]) {
                            deadPlayers[i] = dataInputStreamGame.readBoolean();
                            xCoordinates[i] = dataInputStreamGame.readDouble();
                            yCoordinates[i] = dataInputStreamGame.readDouble();

                            if (deadPlayers[i]) {
                                drawSidebar();
                                System.out.println("Player " + i + " dead");
                            }
                        }
                    }

                    draw();

                    byte numberOfPlayersAlive = numberOfPlayersAlive();
                    if (numberOfPlayers > 1 && (numberOfPlayersAlive == 1 || numberOfPlayersAlive == 0)) {
                        Label labelGameEnd = new Label(numberOfPlayersAlive == 0 ? "Draw" : "Player " + getWinner() + " wins!");
                        labelGameEnd.setLayoutX(400);
                        labelGameEnd.setLayoutY(500);
                        labelGameEnd.setStyle("-fx-font-size: 30; -fx-font-weight: bold");
                        Platform.runLater(() -> pane.getChildren().add(labelGameEnd));
                        break;
                    } else if (numberOfPlayers == 1 && numberOfPlayersAlive == 0) {
                        Label labelGameEnd = new Label("Game over");
                        labelGameEnd.setLayoutX(400);
                        labelGameEnd.setLayoutY(500);
                        labelGameEnd.setStyle("-fx-font-size: 30; -fx-font-weight: bold");
                        Platform.runLater(() -> pane.getChildren().add(labelGameEnd));
                        break;
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                circleConnected.setFill(Color.RED);
            }

            System.out.println("Game over");
        }

        // Bug: If you press and hold e.g. left and then press and release right once, it thinks right is being held down (and it keeps going right)
        // If you add "keydownRight = false;" after "keydownLeft = true;" and "keydownLeft = false;" after "keydownRight = true;" and do the same, it thinks no button is held down (and it now goes straight)
        public void activateControls() {
            pane.setOnKeyPressed(event -> {
                try {
                    if (!deadPlayers[playerId])
                        if (event.getCode() == KeyCode.LEFT) {
                            dataOutputStreamGame.writeByte(-1);
                            dataOutputStreamGame.flush();
                            keydownLeft = true;
                            keydownRight = false;
                        } else if (event.getCode() == KeyCode.RIGHT) {
                            dataOutputStreamGame.writeByte(1);
                            dataOutputStreamGame.flush();
                            keydownRight = true;
                            keydownLeft = false;
                        }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                event.consume(); // Consume the event to make sure focus is not lost on the pane
            });

            pane.setOnKeyReleased(event -> {
                if (event.getCode() == KeyCode.LEFT)
                    keydownLeft = false;
                else if (event.getCode() == KeyCode.RIGHT)
                    keydownRight = false;

                if (!keydownLeft && !keydownRight && !deadPlayers[playerId]) // This if statement prevent the controls from becoming unresponsive when direction is changed in quick succession
                    try {
                        dataOutputStreamGame.writeByte(0);
                        dataOutputStreamGame.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
            });

            pane.setOnMouseClicked(event -> pane.requestFocus()); // Clicking on the pane makes the pane gain focus, which is required for the controls to work
        }

        public void draw() {
            for (int i = 0; i < numberOfPlayers; i++) {
                polylines[i].getPoints().add(xCoordinates[i]);
                polylines[i].getPoints().add(yCoordinates[i]);

                circles[i].setCenterX(xCoordinates[i]);
                circles[i].setCenterY(yCoordinates[i]);
            }

            Platform.runLater(() -> {
                pane.getChildren().clear();
                for (int i = 0; i < numberOfPlayers; i++) {
                    pane.getChildren().addAll(polylines[i], circles[i]);
                }
            });
        }

        public Pane getPane() {
            return pane;
        }

        public byte numberOfPlayersAlive() {
            byte counter = 0;
            for (int i = 0; i < deadPlayers.length; i++) {
                if (!deadPlayers[i])
                    counter++;
            }
            return counter;
        }

        public byte getWinner() {
            for (byte i = 0; i < deadPlayers.length; i++) {
                if (!deadPlayers[i])
                    return i;
            }
            return -1;
        }
    }
}