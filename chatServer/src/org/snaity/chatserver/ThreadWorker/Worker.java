/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.snaity.chatserver.ThreadWorker;

import java.util.LinkedList;
import java.util.List;
import org.snaity.chatserver.Network.*;

/**
 *
 * @author Adriano
 */
public class Worker implements Runnable {

    private final List queue = new LinkedList();

    public void processPacketData(Server svr, Client client, byte[] data, int size) {
        byte[] dataCopy = new byte[size];
        System.arraycopy(data, 0, dataCopy, 0, size);

        synchronized (queue) {
            queue.add(new ServerDataEvent(svr, client, dataCopy));
            queue.notify();
        }
    }

    @Override
    public void run() {
        ServerDataEvent dataEvent;

        while (true) {
            synchronized (queue) {
                while (queue.isEmpty()) {
                    try {
                        queue.wait();
                    } catch (InterruptedException e) {
                    }
                }
                dataEvent = (ServerDataEvent) queue.remove(0);
            }
            
            dataEvent.client.ProcessPacket(dataEvent);
        }
    }

}
