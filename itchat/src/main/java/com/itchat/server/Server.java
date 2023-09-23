package com.itchat.server;

import com.itchat.ITchat.*;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Processus serveur qui ecoute les connexion entrantes,
 * les messages entrant et les rediffuse au clients connectes
 *
 * @author mathieu.fabre
 */
public class Server extends Thread implements ITchat {

    private ServerUI serverUI;
    private int port;
    private InetAddress ipAdress;
    private static Map<SocketChannel, String> connectedClients = new HashMap<>();
    private ServerSocketChannel serverSocketChannel;
    private ByteBuffer buffer = ByteBuffer.allocate(ITchat.BUFFER_SIZE);

    public Server(int port, InetAddress ipAdress, ServerUI serverUI) {

        this.port = port;
        this.ipAdress = ipAdress;
        this.serverUI = serverUI;
    }

    public void run() {

        try {
            // Creation du server socket channel
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(ipAdress, port));

            // Creation du selecteur et enregistrement
            Selector selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Chat Server has started on port : " + port);

            // Boucle qui fait tourner le serveur
            while (serverUI.isRunning()) {
                int readyChannels = selector.select();

                if (readyChannels == 0) {
                    continue;
                }
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

                // Boucle pour traiter ce qui arrive au serveur
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();

                    if (key.isAcceptable()) {
                        acceptClientConnection(serverSocketChannel, selector);
                    } else if (key.isReadable()) {
                        readAndSentMessage((SocketChannel) key.channel());
                    }

                    keyIterator.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Message read(SocketChannel clientChannel) {
        StringBuilder messageBuilder = new StringBuilder(); // Utilisation d'un StringBuilder pour construire la chaîne
                                                            // de caractères.
        try {
            int bytesRead;
            while ((bytesRead = clientChannel.read(buffer)) > 0) {
                // Passer en mode lecture pour lire les données du tampon.
                buffer.flip();
                while (buffer.hasRemaining()) {
                    char c = (char) buffer.get(); // Lire un caractère du tampon.
                    messageBuilder.append(c); // Ajouter le caractère au StringBuilder.
                }
                // Effacer le tampon pour qu'il soit prêt pour la prochaine lecture.
                buffer.clear();
            }
            if (bytesRead == -1) {
                // Le client a fermé la connexion.
                // disconnectClient(clientChannel);
            }
            Message message = Message.fromJson(messageBuilder.toString());
            return message;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void acceptClientConnection(ServerSocketChannel serverSocketChannel, Selector selector)
            throws IOException {

        SocketChannel clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);

        // Enregistre la connexion avec le nom du client
        connectedClients.put(clientChannel, "");
        System.out.println("Nouvelle connexion client établie");
    }

    public void readAndSentMessage(SocketChannel clientChannel) {

        Message message = read(clientChannel);
        if (message != null) {
            sendLogToUI(message.getUser() + " : " + message.getMessage());
            broadcastMessage(message);
        }
    }

    private void broadcastMessage(Message message) {
        try {
            // Conversion de la chaîne JSON en tableau d'octets
            byte[] messageBytes = message.toJson().getBytes(StandardCharsets.UTF_8);

            // Compteur pour suivre le nombre d'octets déjà écrits
            int bytesWritten = 0;

            // Envoie du message au fur et à mesure
            while (bytesWritten < messageBytes.length) {
                buffer.clear();
                int bytesToWrite = Math.min(buffer.remaining(), messageBytes.length - bytesWritten);
                buffer.put(messageBytes, bytesWritten, bytesToWrite);
                buffer.flip();

                // Envoie des données à tous les clients
                for (Map.Entry<SocketChannel, String> entry : connectedClients.entrySet()) {
                    SocketChannel channel = entry.getKey();
                    if (channel.isOpen()) {
                        channel.write(buffer);
                        buffer.rewind(); // Réinitialiser la position du buffer
                    }
                }

                bytesWritten += bytesToWrite;
            }
        } catch (IOException e) {
            // Gérer l'exception d'écriture.
            e.printStackTrace();
        }
        buffer.clear(); // Réinitialiser le buffer après l'envoi complet.
    }

    /**
     * Envoi un message de log a l'IHM
     */
    public void sendLogToUI(String message) {
        Platform.runLater(() -> serverUI.log(message));
    }
}
