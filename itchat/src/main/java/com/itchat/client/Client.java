package com.itchat.client;

import com.itchat.ITchat.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.nio.channels.NotYetConnectedException;

/**
 * Client de tchat
 */
public class Client extends Thread implements ITchat {

    private int serverPort;
    private InetAddress serverIP;
    private String userName;
    private ClientUI clientUI;
    private SocketChannel socketChannel;
    private Selector selector;

    public Client(InetAddress serverIP, int serverPort, String userName, ClientUI clientUI) throws IOException {

        this.serverIP = serverIP;
        this.serverPort = serverPort;
        this.userName = userName;
        this.clientUI = clientUI;
    }

    @Override
    public void run() {
        try {

            // Ouverture du channel client
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(serverIP, serverPort));
            clientUI.setStatus("Connection en cours");

            // Enregistrement du SocketChannel sur un selecteur
            selector = Selector.open();
            socketChannel.register(selector, SelectionKey.OP_CONNECT);

            // Boucle qui fait tourner le Client
            while (clientUI.isRunning()) {
                int readyChannels = selector.select(1000);
                if (readyChannels == 0) {
                    continue;
                }
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

                // Boucle pour traiter ce qui arrive au serveur
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (key.isReadable()) {
                        readMessage((SocketChannel) key.channel());

                    } else if (key.isConnectable()) {
                        // Finalisation de la connexion et changement de l'interet via le selecteur pour
                        // la lecture
                        if (socketChannel.finishConnect()) {
                            socketChannel.register(selector, SelectionKey.OP_READ);
                            clientUI.setStatus("Connecté au serveur");
                        } else {
                            throw new ConnectException();
                        }

                        // Envoi d'un message permettant au serveur d'identifier le client
                        sendMessage("", "cm");
                    }
                    keyIterator.remove();
                }
            }
            disconnect();

        } catch (ConnectException ce) {
            clientUI.setStatus("Impossible de se connecter au serveur");
            try {
                Thread.sleep(2000); // Attendre pendant 2 secondes pour afficher l'erreur au client
            } catch (InterruptedException e) {
            }
            clientUI.disconnectFromServer();
        } catch (IOException ioe) {
            clientUI.setStatus("Vous avez était deconnecté  du serveur");
            try {
                Thread.sleep(2000); // Attendre pendant 2 secondes pour afficher l'erreur au client
            } catch (InterruptedException e) {
            }
            clientUI.disconnectFromServer();
        }
    }

    /**
     * Envoi un message au serveur en utilisant le SocketChannel et en specifiant un
     * destinataire
     */
    public void sendMessage(String messageText, String type, String destinataire) {
        try {
            // Sérialisation de l'objet en JSON
            String messageJson = new Message(userName, messageText, type, destinataire).toJson();

            // Conversion de la chaîne JSON en tableau d'octets
            byte[] messageBytes = messageJson.getBytes(StandardCharsets.UTF_8);

            // ByteBuffer pour envoyer les données
            ByteBuffer buffer = ByteBuffer.allocate(ITchat.BUFFER_SIZE);

            // Compteur pour suivre le nombre d'octets déjà écrits
            int bytesWritten = 0;

            // Envoie du message au fûr et à mesure
            while (bytesWritten < messageBytes.length) {
                buffer.clear();
                int bytesToWrite = Math.min(buffer.remaining(), messageBytes.length - bytesWritten);
                buffer.put(messageBytes, bytesWritten, bytesToWrite);
                buffer.flip();

                // Envoie des données au serveur
                while (buffer.hasRemaining()) {
                    socketChannel.write(buffer);
                }
                bytesWritten += bytesToWrite;
            }
        } catch (NotYetConnectedException nyce) {
        }

        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Envoi un message au serveur en utilisant le SocketChannel sans réel
     * destinataire
     */
    public void sendMessage(String messageText, String type) {
        sendMessage(messageText, type, "");
    }

    /**
     * Lit un message envoyé par le serveur et l'affiche dans l'interface
     */
    public void readMessage(SocketChannel clientChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(ITchat.BUFFER_SIZE);
        StringBuilder messageBuilder = new StringBuilder(); // Utilisation d'un StringBuilder pour construire la chaîne
        // de caractères.
        int bytesRead;
        while ((bytesRead = clientChannel.read(buffer)) > 0) {
            // Passe en mode lecture pour lire les données du tampon.
            buffer.flip();

            while (buffer.hasRemaining()) {
                char c = (char) buffer.get(); // Lire un caractère du tampon.
                messageBuilder.append(c); // Ajouter le caractère au StringBuilder.
            }
            // Effacer le tampon pour qu'il soit prêt pour la prochaine lecture.
            buffer.clear();
        }
        if (bytesRead == -1) {
            // Le serveur a fermé la connexion.
            clientUI.disconnectFromServer();
            clientUI.setStatus("Connexion avec le serveur perdu");
        } else {
            afficherMessage(Message.fromJson(messageBuilder.toString()));
        }
    }

    /**
     * Permet de se déconnecter du serveur
     */
    public void disconnect() {
        try {
            // Envoie un message de deconnexion simple au serveur
            sendMessage("", "dm");

            // Ferme la connexion avec le serveur
            if (socketChannel != null && socketChannel.isConnected()) {
                socketChannel.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Prend un Message en paramètre et l'affiche au client
     */
    public void afficherMessage(Message message) {
        if (message.getType().equals("gm")) {
            clientUI.appendMessage(message.getUser() + " : " + message.getMessage());
        } else if (message.getType().equals("pm")) {
            clientUI.appendMessage(" From : " + message.getUser() + " to : "
                    + message.getDestinataire() + " : " + message.getMessage());
        }
    }

}
