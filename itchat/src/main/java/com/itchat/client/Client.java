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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

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

            // Connection au serveur avec les sockets channels
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(serverIP, serverPort));
            // On attend que la connection s'établisse, elle genèrera une exception si la
            // connection prend trop de temps
            while (!socketChannel.finishConnect()) {
                continue;
            }
            System.out.println("Connection etablie avec le serveur");

            // Creation du selecteur et enregistrement
            selector = Selector.open();
            socketChannel.register(selector, SelectionKey.OP_READ);

            // Envoyez le nom d'utilisateur au serveur. a fairrrreeeeeeeeeeeeeeeeeeeeeee

            while (clientUI.isRunning()) {
                int readyChannels = selector.select();

                if (readyChannels == 0) {
                    continue;
                }
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

                // Boucle pour traiter ce qui arrive au serveur
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (key.isReadable()) {
                        readMessage((SocketChannel) key.channel());
                    }

                    keyIterator.remove();
                }
            }
        } catch (ConnectException ce) {
            clientUI.appendMessage("Impossible de se connecter au serveur");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Méthode pour envoyer un message au serveur en utilisant le SocketChannel
    public void sendMessage(String messageText) {
        try {
            // Sérialisation de l'objet en JSON
            String messageJson = new Message(userName, messageText).toJson();

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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void readMessage(SocketChannel clientChannel) {
        ByteBuffer buffer = ByteBuffer.allocate(ITchat.BUFFER_SIZE);
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

            // a revoir
            if (bytesRead == -1) {
                // Le client a fermé la connexion.
                // disconnectClient(clientChannel);
            }
            afficherMessage(Message.fromJson(messageBuilder.toString()));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Prend un Message en paramètre et l'affiche au client
    public void afficherMessage(Message message) {
        clientUI.appendMessage(System.getProperty("line.separator") + message.getUser() + " : " + message.getMessage());
    }

}
