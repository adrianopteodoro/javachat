/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.snaity.chatserver.ThreadWorker;

import org.snaity.chatserver.Network.*;

/**
 *
 * @author Adriano
 */
public class ServerDataEvent {

    public Server server;
    public Client client;
    public byte[] data;

    public ServerDataEvent(Server server, Client client, byte[] data) {
        this.server = server;
        this.client = client;
        this.data = data;
    }
    
}
