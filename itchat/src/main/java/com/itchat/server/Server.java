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
                // Vérifie si le délai d'attente du nom d'utilisateur est dépassé pour chaque
                // client non identifié
                long currentTime = System.currentTimeMillis();
                for (Map.Entry<SocketChannel, Long> entry : nonidentifiedClients.entrySet()) {
                    SocketChannel channel = entry.getKey();
                    long connectionTime = entry.getValue();
                    if (currentTime - connectionTime > USER_TIMEOUT) {
                        // Supprime les clients non identifiés dont le délai d'attente a été dépassé
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

    /**
     * Lit un message qui arrive au serveur
     */
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

    /**
     * Permet d'accepter une nouvelle connection d'un client et de l'enregistrer
     */
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

    /**
     * Permet de gérer l'envoi de message
     */
    public void readAndSentMessage(SocketChannel clientChannel) {

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
            } else if (message.getType().equals("gm")) {
                broadcastMessage(message);
            } else if (message.getType().equals("pm")) {
                sendPrivateMessage(message);
            }
        }
        connectedClients.get(clientChannel).set(1, false);
    }

    /**
     * Envoi un message à tous les clients
     */
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
                        buffer.rewind(); // Réinitialise la position du buffer
                    }
                }
                bytesWritten += bytesToWrite;
            }
            sendLogToUI(
                    LocalDateTime.now().format(formatter) + " " + message.getUser() + " : " + message.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
        buffer.clear(); // Réinitialiser le buffer après l'envoi complet.
    }

    /**
     * Permet l'envoi d'un message privé
     */
    private void sendPrivateMessage(Message message) {
        ByteBuffer buffer = ByteBuffer.allocate(ITchat.BUFFER_SIZE);
        SocketChannel sender = null;
        SocketChannel receiver = null;
        Boolean validReceiver = false;
        // On récupère les socket channel associer aux utilisateurs et on vérifie que le
        // destinataire existe
        for (Map.Entry<SocketChannel, ArrayList<Object>> entry : connectedClients.entrySet()) {
            ArrayList<Object> values = entry.getValue();
            String stringValue = (String) values.get(0);
            if (stringValue.equals(message.getUser())) {
                sender = entry.getKey();
            } else if (stringValue.equals(message.getDestinataire())) {
                receiver = entry.getKey();
                validReceiver = true;
            }
        }
        // Envoi des logs
        sendLogToUI(
                LocalDateTime.now().format(formatter) + " From : " + message.getUser() + " to : "
                        + message.getDestinataire() + " : " + message.getMessage());
        if (validReceiver.equals(false)) {
            sendLogToUI(LocalDateTime.now().format(formatter) + " " + message.getDestinataire() + " n'existe pas");
            message = new Message("Server", message.getDestinataire() + " n'existe pas", "gm", "");
        }

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

                // Envoie des données à l'envoyeur
                if (!sender.equals(null) && sender.isOpen()) {
                    sender.write(buffer);
                    buffer.rewind(); // Réinitialise la position du buffer
                }
                // Envoie des données au receveur
                if (validReceiver && receiver.isOpen()) {
                    receiver.write(buffer);
                }
                bytesWritten += bytesToWrite;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Déconnecte un client
     */
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

    /**
     * Arrete le serveur
     */
    public void stopServer() {
        try {

            // Arrête le pool de threads
            threadPool.shutdown();

            // Attend que toutes les tâches en cours se terminent
            threadPool.awaitTermination(10, TimeUnit.SECONDS); // Delai d'attente de 10 secondes

            // Ferme toutes les connexions client
            for (SocketChannel clientChannel : connectedClients.keySet()) {
                disconnectClient(clientChannel);
            }

            // Ferme le sélecteur
            if (selector != null && selector.isOpen()) {
                selector.close();
            }

            // Ferme le socket serveur
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
