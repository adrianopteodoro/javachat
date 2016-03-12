/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.snaity.chatserver.Protocol;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 *
 * @author Adriano
 */
public class Packet {
    private ByteBuffer data;
    private int PacketSize;

    public int getPacketSize() {
        return PacketSize;
    }

    public void setPacketSize(int PacketSize) {
        this.PacketSize = PacketSize;
    }
    
    public Packet() {
        this.InitPacket();
    }
    
    public final void InitPacket() {
        this.data = ByteBuffer.allocate(8192);
    }
    
    public void InitPacket(byte[] data) {
        this.data = ByteBuffer.wrap(data);
    }
    
    public byte[] getData() {
        byte[] dat = Arrays.copyOf(this.data.array(), PacketSize);
        return dat;
    }
    
    public void putShort(int pos, short value) {
        this.data.putShort(pos, value);
    }
    
    public void putString(int pos, String value) {
        this.data.put(pos, (byte) value.length());
        this.data.put(value.getBytes());
    }
    
    public short getShort(int pos) {
        return this.data.getShort(pos);
    }
    
    public String getString(int pos) {
        String ret;
        int len = this.data.get(pos);
        byte[] data = new byte[len];
        this.data.get(data, 0, len);
        ret = new String(data);
        return ret;
    }
}
