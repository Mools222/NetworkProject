import javafx.application.Application;
import javafx.application.Platform;
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
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    private GridPane gridPanePlayers = new GridPane();
    private HBox hBox = new HBox();
    private VBox vBox = new VBox();
    private TextArea textArea = new TextArea();
    private TextField textFieldChat = new TextField();

    @Override // Override the start method in the Application class
    public void start(Stage primaryStage) {
        GameView gameView = new GameView();
        Pane pane = gameView.getPane();

        gridPanePlayers.setHgap(10);
        gridPanePlayers.setStyle("-fx-border-color: black");
        gridPanePlayers.setPrefHeight(275);

        TextField textFieldIp = new TextField();
        textFieldIp.setPromptText("Enter host IP");

        Button buttonConnect = new Button("Connect");
        Button buttonReady = new Button("Ready");

        GridPane gridPaneConnection = new GridPane();
//        gridPaneConnection.setStyle("-fx-border-color: black");
//        gridPaneConnection.setPrefHeight(100);
        gridPaneConnection.setHgap(5);
        gridPaneConnection.setVgap(10);
        gridPaneConnection.addRow(0, textFieldIp, buttonConnect);
        gridPaneConnection.add(buttonReady, 1, 1);

        textArea.setEditable(false);
        textArea.setPrefHeight(300);
        textArea.setFocusTraversable(false);

        textFieldChat.setPromptText("Enter chat message and hit enter");

        vBox.setSpacing(10);
        vBox.getChildren().addAll(gridPanePlayers, gridPaneConnection, textArea, textFieldChat);

        hBox.setSpacing(10);
        hBox.getChildren().addAll(pane, vBox);

        // Create a scene and place it in the stage
        Scene scene = new Scene(hBox);
        primaryStage.setTitle("Client"); // Set the stage title
        primaryStage.setScene(scene); // Place the scene in the stage
        primaryStage.show(); // Display the stage

        buttonReady.requestFocus();

        buttonReady.setOnAction(event -> {
            try {
                dataOutputStream.writeBoolean(true);
            } catch (Exception e) {
                e.printStackTrace();
            }

            pane.requestFocus();
        });

        new Thread(gameView).start();
    }

    class GameView implements Runnable {
        private Pane pane = new Pane();
        private byte numberOfPlayers, playerId;
        private double radius = 5, width = 1000, height = 700;
        private double[] xCoordinates, yCoordinates;
        private Circle[] circles;
        private Polyline[] polylines;
        private boolean keydownLeft, keydownRight;
        private boolean[] deadPlayers, readyPlayers;
        private Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.PURPLE, Color.PINK};

        Label labelId = new Label("ID");
        Label labelReady = new Label("Ready?");

        public GameView() {
            pane.setPrefSize(width, height);
//            pane.setStyle("-fx-background-color: grey; -fx-border-color: black; -fx-border-width: 3");
            pane.setStyle("-fx-border-color: black; -fx-border-width: 3");

//            labelId.setStyle("-fx-border-color: black");
            labelId.setStyle("-fx-font-size: 20; -fx-font-weight: bold");
            labelId.setPrefWidth(100);
            labelReady.setStyle("-fx-font-size: 20; -fx-font-weight: bold");
            labelReady.setPrefWidth(100);

            drawSidebar();
        }

        public void drawSidebar() {
            Platform.runLater(() -> {
                gridPanePlayers.getChildren().clear();
                gridPanePlayers.addRow(0, labelId, labelReady);
                for (int i = 0; i < numberOfPlayers; i++) {
                    Label label1 = new Label("Player " + i);
                    label1.setTextFill(colors[i]);
                    label1.setStyle("-fx-font-size: 20;");
                    Label label2 = new Label(readyPlayers[i] ? "Yes" : "No");
                    label2.setTextFill(readyPlayers[i] ? Color.GREEN : Color.RED);
                    label2.setStyle("-fx-font-size: 20;");
                    gridPanePlayers.addRow(i + 1, label1, label2);
                }
            });
        }

        @Override
        public void run() {
            try {
                Socket socket = new Socket("localhost", 8000); // Create a socket to connect to the server

                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                dataInputStream = new DataInputStream(socket.getInputStream());

                byte id = dataInputStream.readByte();
                playerId = id;
                System.out.println("Player number " + id);

                System.out.println("Waiting for all ready");
                while (true) {
                    byte command = dataInputStream.readByte();
                    if (command == 0) { // 0 = receive number of players
                        numberOfPlayers = dataInputStream.readByte();
                        readyPlayers = new boolean[numberOfPlayers]; // TODO: Copy the content of the old array (if you want players to be able to ready up before all players have joined)
                        drawSidebar();
                        System.out.println("Number of players " + numberOfPlayers);
                    } else if (command == 1) { // 1 = player ready
                        byte readyPlayerId = dataInputStream.readByte();
                        readyPlayers[readyPlayerId] = true;
                        drawSidebar();
                        System.out.println("Player " + readyPlayerId + " ready");
                    } else if (command == 2) { // 2 = all players ready
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
                            deadPlayers[i] = dataInputStream.readBoolean();
                            xCoordinates[i] = dataInputStream.readDouble();
                            yCoordinates[i] = dataInputStream.readDouble();

                            if (deadPlayers[i])
                                System.out.println("Player " + i + " dead");
                        }
                    }

                    draw();

                    byte numberOfPlayersAlive = numberOfPlayersAlive();
                    if (numberOfPlayers > 1 && (numberOfPlayersAlive == 1 || numberOfPlayersAlive == 0))
                        break;
                    else if (numberOfPlayers == 1 && numberOfPlayersAlive == 0)
                        break;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
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
                            dataOutputStream.writeByte(-1);
                            dataOutputStream.flush();
                            keydownLeft = true;
                            keydownRight = false;
                        } else if (event.getCode() == KeyCode.RIGHT) {
                            dataOutputStream.writeByte(1);
                            dataOutputStream.flush();
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
                        dataOutputStream.writeByte(0);
                        dataOutputStream.flush();
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

//                System.out.println("draw " + i + " x" + xCoordinates[i] + " y " + yCoordinates[i]);
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
    }
}