/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.snaity.chatserver.Protocol;

/**
 *
 * @author Adriano
 */
public class serverLoginRequest extends Packet {
    
    public void InitPacket(int size) {
        this.setPacketSize(size);
        this.putShort(0, (short) size);
        this.putShort(2, (short) 3100);
    }
}
