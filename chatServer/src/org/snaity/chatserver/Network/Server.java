/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.snaity.chatserver.Network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.snaity.chatserver.App;
import org.snaity.chatserver.Protocol.*;
import org.snaity.chatserver.ThreadWorker.Worker;

/**
 *
 * @author Adriano
 */
public final class Server implements Runnable {

    private String _srvAddress;
    private int _srvPort;
    private static final long Timeout = 10000;

    private ServerSocketChannel srvChannel;
    private Selector srvSelector;

    private final List<Client> clientList;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    private final Worker worker;

    private final List changeRequests = new LinkedList();
    private final Map pendingData = new HashMap();

    public Server(Worker worker) throws IOException {
        this.clientList = new ArrayList<>();
        this.setAddress("127.0.0.1");
        this.setPort(5200);
        this.worker = worker;
        this.init();
    }

    private void init() {
        System.out.println("Initializing server ...");
        if (this.srvSelector != null) {
            return;
        }
        if (this.srvChannel != null) {
            return;
        }

        try {
            this.srvSelector = SelectorProvider.provider().openSelector();
            this.srvChannel = ServerSocketChannel.open();
            this.srvChannel.configureBlocking(false);
            this.srvChannel.bind(new InetSocketAddress(this._srvAddress, this._srvPort));
            this.srvChannel.register(this.srvSelector, SelectionKey.OP_ACCEPT);
            System.out.println("Server is listening on " + this.getAddress() + ":" + this.getPort());
        } catch (IOException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void AcceptSocket(SelectionKey key) {
        try {
            Client client = new Client();
            SocketChannel socket = this.srvChannel.accept();
            socket.configureBlocking(false);
            socket.register(this.srvSelector, SelectionKey.OP_READ);
            client.setSocket(socket, this);
            this.clientList.add(client);

            System.out.println("Accepted new connection from client: " + socket.getRemoteAddress());

            // send login request
            serverLoginRequest ulr = new serverLoginRequest();
            ulr.InitPacket(4);
            this.send(socket, ulr.getData());
            System.out.println(Arrays.toString(ulr.getData()));
        } catch (IOException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void WriteData(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        synchronized (this.pendingData) {
            List queue = (List) this.pendingData.get(socketChannel);

            while (!queue.isEmpty()) {
                ByteBuffer buf = (ByteBuffer) queue.get(0);
                socketChannel.write(buf);
                if (buf.remaining() > 0) {
                    break;
                }
                queue.remove(0);
            }

            if (queue.isEmpty()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private void ReadData(SelectionKey key) throws IOException {
        this.readBuffer.clear();
        SocketChannel socket = (SocketChannel) key.channel();

        int numRead;

        try {
            numRead = socket.read(readBuffer);
        } catch (IOException ex) {
            System.out.println("Client disconnected " + socket.getRemoteAddress());
            key.cancel();
            socket.close();
            return;
        }

        if (numRead == -1) {
            System.out.println("Client disconnected " + socket.getRemoteAddress());
            key.channel().close();
            key.cancel();
            return;
        }

        Client client = null;
        for (Client c : this.clientList) {
            if (c.getSocket().equals(key.channel())) {
                client = c;
            }
        }

        if (client != null) {
            this.worker.processPacketData(this, client, this.readBuffer.array(), numRead);
        }
    }

    public void send(SocketChannel socket, byte[] data) {
        synchronized (this.changeRequests) {
            this.changeRequests.add(new ChangeRequest(socket, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));

            synchronized (this.pendingData) {
                List queue = (List) this.pendingData.get(socket);
                if (queue == null) {
                    queue = new ArrayList();
                    this.pendingData.put(socket, queue);
                }
                queue.add(ByteBuffer.wrap(data));
            }
        }

        this.srvSelector.wakeup();
    }

    @Override
    public void run() {
        System.out.println("Now accepting connections...");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (this.changeRequests) {
                    Iterator changes = this.changeRequests.iterator();
                    while (changes.hasNext()) {
                        ChangeRequest change = (ChangeRequest) changes.next();
                        switch (change.type) {
                            case ChangeRequest.CHANGEOPS:
                                SelectionKey key = change.socket.keyFor(this.srvSelector);
                                key.interestOps(change.ops);
                        }
                    }
                    this.changeRequests.clear();
                }

                this.srvSelector.select(Timeout);
                Iterator<SelectionKey> keys = this.srvSelector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        this.AcceptSocket(key);
                    } else if (key.isReadable()) {
                        this.ReadData(key);
                    } else if (key.isWritable()) {
                        this.WriteData(key);
                    }

                    keys.remove();
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void setAddress(String addr) {
        this._srvAddress = addr;
    }

    public String getAddress() {
        return this._srvAddress;
    }

    public void setPort(int Port) {
        this._srvPort = Port;
    }

    public int getPort() {
        return this._srvPort;
    }
}
