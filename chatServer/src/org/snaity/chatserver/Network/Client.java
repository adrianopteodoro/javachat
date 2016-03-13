/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.snaity.chatserver.Network;

import java.lang.reflect.Array;
import java.nio.channels.SocketChannel;
import org.snaity.chatserver.ThreadWorker.ServerDataEvent;

/**
 *
 * @author aluno
 */
public class Client {

    private String nickName;
    private SocketChannel socket;
    private Server server;

    public SocketChannel getSocket() {
        return socket;
    }

    public void setSocket(SocketChannel socket, Server server) {
        this.socket = socket;
        this.setServer(server);
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public void ProcessPacket(ServerDataEvent de) {
        short packetlen = Array.getShort(de.data, 0);
        System.out.println("Recv Len: " + packetlen);
        
        if (packetlen >= 4 && de.data.length >= packetlen) {
            short packetid = Array.getShort(de.data, 2);
            switch (packetid) {
                //case 3100: // CHECK NICK NAME
                //   break;
                default:
                    System.out.println("Unknow Packet ID: " + packetid + " Len: " + packetlen);
                    break;
            }
        }
    }

	/**
	 * @return the server
	 */
	public Server getServer() {
		return server;
	}

	/**
	 * @param server the server to set
	 */
	public void setServer(Server server) {
		this.server = server;
	}
}
