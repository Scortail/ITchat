package com.itchat.client;

import javafx.application.Application;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;

/**
 * Interface graphique du client
 *
 * @author mathieu.fabre
 */
public class ClientUI extends Application implements EventHandler {

    private TextField ip;
    private TextField port;
    private TextField nickname;
    private Button connect;
    private Button disconnect;
    private TextArea textArea;
    private TextField input;
    private Label status;

    /**
     * Le thread client
     */
    private Client client;

    /**
     * Indique si le client tourne
     */
    private boolean running = false;

    public void start(Stage stage) throws Exception {

        // Border pane et scene
        BorderPane borderPane = new BorderPane();
        Scene scene = new Scene(borderPane);
        stage.setScene(scene);

        // ZOne haute pour la connection
        ToolBar toolBar = new ToolBar();
        ip = new TextField("127.0.0.1");
        port = new TextField("6699");
        nickname = new TextField("user" + (new Random().nextInt(100)));
        connect = new Button("Connect");
        connect.setOnAction(this);
        disconnect = new Button("Disconnect");
        disconnect.setOnAction(this);
        toolBar.getItems().addAll(ip, port, connect, disconnect);
        borderPane.setTop(toolBar);

        // Zone centrale de log de tchat
        textArea = new TextArea();
        borderPane.setCenter(textArea);

        // Zone basse pour la zone de texte et le statut
        VBox bottomBox = new VBox();
        status = new Label("Pret");
        input = new TextField();
        input.addEventFilter(KeyEvent.KEY_RELEASED, this);
        bottomBox.getChildren().addAll(input, status);
        borderPane.setBottom(bottomBox);

        // Statut initial deconnecte
        setDisconnectedState();

        stage.setTitle("Client de tchat");
        stage.show();
    }

    /**
     * Mets l'IHM dans le statut deconnecte
     */
    public void setDisconnectedState() {
        ip.setDisable(false);
        port.setDisable(false);
        connect.setDisable(false);
        disconnect.setDisable(true);
        input.setDisable(true);
        setStatus("Pret");
    }

    public void setConnectedState() {
        ip.setDisable(true);
        port.setDisable(true);
        connect.setDisable(true);
        disconnect.setDisable(false);
        input.setDisable(false);
        setStatus("Connecté au serveur");
    }

    /**
     * Indique si le client tourne
     *
     * @return
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Ajout de message dans le log
     *
     * @param message
     */
    public void appendMessage(String message) {
        textArea.appendText(System.getProperty("line.separator") + message);
    }

    /**
     * Change le message de statut
     *
     * @param message
     */
    public void setStatus(String message) {
        status.setText(message);
    }

    /**
     * Connexion au serveur
     */
    public void connectToServer() {

        if (ip.getText().trim().length() == 0) {
            setStatus("Veuillez entrer une adresse IP valide");
            return;
        }

        if (port.getText().trim().length() == 0) {
            setStatus("Veuillez entrer un port valide");
            return;
        }

        if (nickname.getText().trim().length() == 0) {
            setStatus("Veuillez entrer un nickname valide");
            return;
        }

        try {
            // Récupére les informations d'adresse IP et de port entrées par l'utilisateur.
            InetAddress serverIP = InetAddress.getByName(ip.getText().trim());
            int serverPort = Integer.parseInt(port.getText().trim());
            String userName = nickname.getText().trim();

            // Crée une instance de la classe Client et la lance.
            client = new Client(serverIP, serverPort, userName, this);
            running = true; // Changement de l'etat du client
            client.start();

            // Changement d'état de l'IHM
            setConnectedState();

        } catch (NumberFormatException e) {
            setStatus("Le port doit être un nombre valide");
        } catch (IOException e) {
            setStatus("Erreur lors de la connexion au serveur : " + e.getMessage());
        }
    }

    /**
     * Deconnexion
     * on passe le statut a false et on attends
     * que le thread se deconnecte
     */
    public void disconnectFromServer() {
        this.running = false;
        setDisconnectedState();
    }

    /**
     * Prise en charge des events
     *
     * @param event
     */
    public void handle(Event event) {

        if (event.getSource() == connect) {
            connectToServer();
        } else if (event.getSource() == disconnect) {
            disconnectFromServer();
        } else if (event.getSource() == input) {
            processEnter((KeyEvent) event);
        }

    }

    /**
     * Envoi le message si l utilisateur
     * appui sur la touche entree
     *
     * @param event
     */
    public void processEnter(KeyEvent event) {

        // Envoi du texte si on appui sur entree et que le contenu n est pas vide
        if (event.getCode() == KeyCode.ENTER && input.getText().trim().length() > 0) {

            // / Recupere le texte saisi par l'utilisateur.
            String messageText = input.getText().trim();

            // Envoi le message au client pour qu'il puisse l'envoyer au serveur.
            client.sendMessage(messageText, "gm");
            input.setText("");
        }
    }

    /**
     * Demarrage du client
     *
     * @param args
     */
    public static void main(String[] args) {
        launch(args);
    }
}
