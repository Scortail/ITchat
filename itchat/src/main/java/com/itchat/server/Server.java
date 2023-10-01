package com.itchat.server;

import com.itchat.ITchat.*;

import javafx.application.Platform;
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
import java.util.Iterator;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    private Selector selector;
    private static Map<SocketChannel, ArrayList<Object>> connectedClients = new ConcurrentHashMap<>();
    private static Map<SocketChannel, Long> nonidentifiedClients = new ConcurrentHashMap<>();
    private static final long USER_TIMEOUT = 10000;
    private ServerSocketChannel serverSocketChannel;
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private ExecutorService threadPool;

    public Server(int port, InetAddress ipAdress, ServerUI serverUI) {

        this.port = port;
        this.ipAdress = ipAdress;
        this.serverUI = serverUI;
        this.threadPool = Executors.newFixedThreadPool(10);
    }

    public void run() {

        try {
            // Creation du server socket channel
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(ipAdress, port));

            // Creation du selecteur et enregistrement
            selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Chat Server has started on port : " + port);

            // Boucle qui fait tourner le serveur
            while (serverUI.isRunning()) {
                int readyChannels = selector.select(1000);

                if (readyChannels == 0) {
                    continue;
                }
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

                // Boucle pour traiter le flux entrant
                while (keyIterator.hasNext()) {
                    System.out.println("test" + selector.keys().size());
                    SelectionKey key = keyIterator.next();

                    if (!key.isValid()) {
                        key.cancel();
                    } else if (key.isAcceptable()) {
                        acceptClientConnection(serverSocketChannel, selector);

                    } else if (key.isReadable()
                            && ((boolean) connectedClients.get((SocketChannel) key.channel())
                                    .get(1) == false)) {
                        connectedClients.get((SocketChannel) key.channel()).set(1, true);
                        threadPool.submit(() -> {
                            readAndSentMessage((SocketChannel) key.channel());
                        });
                    }
                    keyIterator.remove();
                }
                // Vérifiez si le délai d'attente du nom d'utilisateur est dépassé pour chaque
                // client non identifié
                long currentTime = System.currentTimeMillis();
                for (Map.Entry<SocketChannel, Long> entry : nonidentifiedClients.entrySet()) {
                    SocketChannel channel = entry.getKey();
                    long connectionTime = entry.getValue();
                    if (currentTime - connectionTime > USER_TIMEOUT) {
                        // Supprimez les clients non identifiés dont le délai d'attente a été dépassé
                        System.out.println(
                                "Délai d'attente pour le nom d'utilisateur dépassé, fermeture de la connexion.");
                        nonidentifiedClients.remove(channel);
                        connectedClients.remove(channel);
                        channel.close();
                    }
                }
            }
            stopServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Message read(SocketChannel clientChannel) {
        StringBuilder messageBuilder = new StringBuilder(); // Utilisation d'un StringBuilder pour construire la chaîne
                                                            // de caractères.
        ByteBuffer buffer = ByteBuffer.allocate(ITchat.BUFFER_SIZE);
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
                disconnectClient(clientChannel);
                return null;
            } else {
                Message message = Message.fromJson(messageBuilder.toString());
                return message;
            }

        } catch (IOException e) {
            sendLogToUI(LocalDateTime.now().format(formatter) + " : " + "La connexion avec "
                    + connectedClients.get(clientChannel).get(0) + " s'est interrompu");
            disconnectClient(clientChannel);
            return null;
        }
    }

    private void acceptClientConnection(ServerSocketChannel serverSocketChannel, Selector selector)
            throws IOException {

        SocketChannel clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);

        // Enregistre l'heure à laquelle le client s'est connecté
        long connectionTime = System.currentTimeMillis();
        nonidentifiedClients.put(clientChannel, connectionTime);
        ArrayList<Object> list = new ArrayList<Object>();
        list.add("");
        list.add(false);
        connectedClients.put(clientChannel, list);
        System.out.println("Nouvelle connexion client établie");
    }

    public void readAndSentMessage(SocketChannel clientChannel) {
        System.out.println("je suis :" + currentThread());

        Message message = read(clientChannel);
        if (message != null && message.getUser() != null) {

            // Message de connexion
            if (message.getType().equals("cm") && nonidentifiedClients.containsKey(clientChannel)) {
                sendLogToUI(LocalDateTime.now().format(formatter) + " " + message.getUser() + " s'est connecté.");
                nonidentifiedClients.remove(clientChannel);
                // On associe au socketChannel le nom du client
                connectedClients.get(clientChannel).set(0, message.getUser());

                // Message de déconnexion
            } else if (message.getType().equals("dm")) {
                disconnectClient(clientChannel);

                // gerer les gm et pm
            } else {
                sendLogToUI(
                        LocalDateTime.now().format(formatter) + " " + message.getUser() + " : " + message.getMessage());
                broadcastMessage(message);
            }
        }
        connectedClients.get(clientChannel).set(1, false);
    }

    private void broadcastMessage(Message message) {
        ByteBuffer buffer = ByteBuffer.allocate(ITchat.BUFFER_SIZE);

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
                for (Map.Entry<SocketChannel, ArrayList<Object>> entry : connectedClients.entrySet()) {
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

    public void disconnectClient(SocketChannel clientChannel) {
        try {
            // Ferme la connexion avec le serveur
            if (clientChannel != null && clientChannel.isConnected()) {
                clientChannel.close();
                sendLogToUI(LocalDateTime.now().format(formatter) + " " + connectedClients.get(clientChannel).get(0)
                        + "s'est déconnecté.");
                connectedClients.remove(clientChannel);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopServer() {
        try {

            // Arrêtez le pool de threads
            threadPool.shutdown();
            // Attendre que toutes les tâches en cours se terminent (ou délai d'attente)
            threadPool.awaitTermination(10, TimeUnit.SECONDS); // Ajustez le délai d'attente si nécessaire
            // Fermer toutes les connexions client
            for (SocketChannel clientChannel : connectedClients.keySet()) {
                disconnectClient(clientChannel);
            }

            // Fermer le sélecteur
            if (selector != null && selector.isOpen()) {
                selector.close();
            }

            // Fermer le socket serveur
            if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
                serverSocketChannel.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    /**
     * Envoi un message de log a l'IHM
     */
    public void sendLogToUI(String message) {
        Platform.runLater(() -> serverUI.log(message));
    }
}
