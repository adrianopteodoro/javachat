/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.snaity.chatclient;

import java.awt.Color;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.*;
import java.lang.reflect.Array;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.*;
import javax.swing.*;
import javax.swing.text.*;
import sun.audio.AudioPlayer;
import sun.audio.AudioStream;

/**
 *
 * @author Adriano
 */
public class mainWindow extends javax.swing.JFrame {

    /**
	 * 
	 */
	private static final long serialVersionUID = 5265014493047382068L;
	private static final int READ_BUFFER_SIZE = 0x100000;
    private static final int WRITE_BUFFER_SIZE = 0x100000;

    private final ByteBuffer readBuf = ByteBuffer.allocate(READ_BUFFER_SIZE);
    private final ByteBuffer writeBuf = ByteBuffer.allocate(WRITE_BUFFER_SIZE);

    private String nickName;
    private int serverPort;
    private String serverAddress;
    private boolean bConected;
    private final DefaultListModel<String> onlineListmodel;

    private SocketChannel socket;
    private Selector selector;

    private final AtomicLong bytesOut = new AtomicLong(0L);
    private static final long Timeout = 10000;

    public boolean isConected() {
        return bConected;
    }

    public void setConected(boolean Conected) {
        this.bConected = Conected;
    }

    public String getNickName() {
        return nickName;
    }

    public boolean setNickName(String nickName) {
        if (nickName.matches("[a-zA-Z0-9]+")) {
            this.nickName = nickName;
            this.setTitle("Bate Papo - " + nickName);
            return true;
        } else {
            JOptionPane.showMessageDialog(null, "Digite um nickname valido, não pode possuir espaços e nem caracteres especiais");
            return false;
        }
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public boolean setServerPort(String serverPort) {
        if (serverPort.matches("(6553[0-5])|(655[0-2]\\d)|(65[0-4]\\d{2})|(6[0-4]\\d{3})|([1-5]\\d{4})|([1-9]\\d{1,3})")) {
            this.serverPort = Integer.parseInt(serverPort);
            return true;
        } else {
            JOptionPane.showMessageDialog(null, "Digite uma porta valida, valor valido entre 1000 ~ 65535");
            return false;
        }
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public boolean setServerAddress(String serverAddress) {
        if (serverAddress.matches("(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)")) {
            this.serverAddress = serverAddress;
            return true;
        } else {
            JOptionPane.showMessageDialog(null, "Digite um endreço de IP válido");
            return false;
        }
    }

    /**
     * Creates new form mainWindow
     */
    public mainWindow() {
        this.nickName = null;
        this.serverAddress = "127.0.0.1";
        this.serverPort = 5200;
        this.onlineListmodel = new DefaultListModel<String>();
        initComponents();
    }

    private void initConnetion() {
        try {
            this.selector = SelectorProvider.provider().openSelector();
            this.socket = SocketChannel.open();
            this.socket.configureBlocking(false);
            this.socket.connect(new InetSocketAddress(this.serverAddress, this.serverPort));
            this.socket.register(selector, SelectionKey.OP_CONNECT);
            this.btnConn.setEnabled(true);
            this.run();
        } catch (IOException ex) {
            Logger.getLogger(mainWindow.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void closeConnetion() {
        try {
            this.btnConn.setText("Conectar");
            this.setConected(false);
            this.btnOptions.setEnabled(true);

            this.onlineListmodel.clear();
            this.listOnline.setModel(onlineListmodel);
            this.selector.close();
            this.socket.close();
            this.btnConn.setEnabled(true);
        } catch (IOException ex) {
            Logger.getLogger(mainWindow.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void run() {
        while (this.isConected()) {
            try {
                if (this.selector != null) {
                    this.selector.select(Timeout);

                    Iterator<?> selectedKeys = this.selector.selectedKeys().iterator();

                    while (selectedKeys.hasNext()) {
                        SelectionKey key = (SelectionKey) selectedKeys.next();
                        selectedKeys.remove();

                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isReadable()) {
                            processRead(key);
                        } else if (key.isWritable()) {
                            processWrite(key);
                        } else if (key.isConnectable()) {
                            processConnect(key);
                        }
                    }
                }
            } catch (IOException ex) {
                this.appendToPane(chatWindow, "Falha ao conectar ao servidor, favor verifique as configurações no botão \"Opções\"\n\n", Color.red, true, true, false);
                Logger.getLogger(mainWindow.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void handleResponse(byte[] data, int numRead) {
        short packetlen = Array.getShort(data, 0);
        this.appendToPane(chatWindow, "Recv Len: " + packetlen, Color.red, true, false, false);

        if (packetlen >= 4 && numRead >= packetlen) {
            short packetid = Array.getShort(data, 2);
            switch (packetid) {
                default:
                    this.appendToPane(chatWindow, "Unknow Packet ID: " + packetid + " Len: " + packetlen, Color.red, true, false, false);
                    break;
            }
        }
    }

    public void send(byte[] data) throws IOException, InterruptedException {
        if (!this.isConected()) {
            throw new IOException("not connected");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);

        synchronized (writeBuf) {
            // try direct write of what's in the buffer to free up space
            if (writeBuf.remaining() < buffer.remaining()) {
                writeBuf.flip();
                while (writeBuf.hasRemaining() && (socket.write(writeBuf)) > 0) {
                }
                writeBuf.compact();
            }

            // if didn't help, wait till some space appears
            if (writeBuf.remaining() < buffer.remaining()) {
                throw new IOException("send buffer full"); // TODO: add reallocation or buffers chain
            }
            writeBuf.put(buffer);

            // try direct write to decrease the latency
            writeBuf.flip();
            while (writeBuf.hasRemaining() && (socket.write(writeBuf)) > 0) {
            }
            writeBuf.compact();

            if (writeBuf.hasRemaining()) {
                SelectionKey key = socket.keyFor(selector);
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                selector.wakeup();
            }
        }
    }

    private void playSound() {
        try {
            InputStream inputStream = getClass().getResourceAsStream("/org/snaity/chatclient/Media/communication.wav");
            AudioStream audioStream = new AudioStream(inputStream);
            AudioPlayer.player.start(audioStream);
        } catch (IOException e) {
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panelOnline = new javax.swing.JScrollPane();
        listOnline = new javax.swing.JList<String>();
        panelChatScroll = new javax.swing.JScrollPane();
        chatWindow = new javax.swing.JTextPane();
        penalChatOptions = new javax.swing.JPanel();
        txtChatText = new javax.swing.JTextField();
        btnSendChat = new javax.swing.JButton();
        btnOptions = new javax.swing.JButton();
        btnConn = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Bate Papo");
        setLocationByPlatform(true);
        setName("mainForm"); // NOI18N
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        panelOnline.setBorder(javax.swing.BorderFactory.createTitledBorder("Pessoas Online"));

        listOnline.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        panelOnline.setViewportView(listOnline);

        panelChatScroll.setBorder(javax.swing.BorderFactory.createTitledBorder("Bate Papo"));
        panelChatScroll.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        panelChatScroll.setViewportView(chatWindow);

        penalChatOptions.setBorder(javax.swing.BorderFactory.createTitledBorder("Enviar / Opções"));

        txtChatText.setText("Mensagem");
        txtChatText.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                txtChatTextFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtChatTextFocusLost(evt);
            }
        });
        txtChatText.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                txtChatTextKeyPressed(evt);
            }
        });

        btnSendChat.setText("Enviar");
        btnSendChat.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSendChatActionPerformed(evt);
            }
        });

        btnOptions.setText("Opções");
        btnOptions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOptionsActionPerformed(evt);
            }
        });

        btnConn.setText("Conectar");
        btnConn.setEnabled(false);
        btnConn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnConnActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout penalChatOptionsLayout = new javax.swing.GroupLayout(penalChatOptions);
        penalChatOptions.setLayout(penalChatOptionsLayout);
        penalChatOptionsLayout.setHorizontalGroup(
            penalChatOptionsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(penalChatOptionsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(penalChatOptionsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(penalChatOptionsLayout.createSequentialGroup()
                        .addComponent(txtChatText)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSendChat)
                        .addGap(8, 8, 8))
                    .addGroup(penalChatOptionsLayout.createSequentialGroup()
                        .addComponent(btnOptions)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnConn)
                        .addContainerGap())))
        );
        penalChatOptionsLayout.setVerticalGroup(
            penalChatOptionsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(penalChatOptionsLayout.createSequentialGroup()
                .addGap(12, 12, 12)
                .addGroup(penalChatOptionsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnSendChat)
                    .addComponent(txtChatText))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 6, Short.MAX_VALUE)
                .addGroup(penalChatOptionsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnOptions)
                    .addComponent(btnConn))
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(penalChatOptions, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(panelOnline, javax.swing.GroupLayout.DEFAULT_SIZE, 170, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(panelChatScroll, javax.swing.GroupLayout.PREFERRED_SIZE, 531, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(panelChatScroll)
                    .addComponent(panelOnline, javax.swing.GroupLayout.DEFAULT_SIZE, 346, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(penalChatOptions, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened

        while (this.nickName == null
                || !this.nickName.matches("[a-zA-Z0-9]+")) {
            this.nickName = JOptionPane.showInputDialog(this, "Digite seu nickname (Apelido) sem espaços ou caracteres especiais");
        }

        this.setTitle("Bate Papo - " + nickName);

        if (!this.isConected()) {
            this.btnConn.setText("Conectar");
        }

        this.appendToPane(chatWindow, "Clique no botão \"Conectar\" abaixo para se conectar ao servidor\nou clique em \"Opções\" para configurar o endereço e porta do servidor\n\n", Color.darkGray, true, true, true);
        this.playSound();
        this.btnConn.setEnabled(true);
        //this.repaint();
        this.chatWindow.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                chatWindow.setEditable(false);
            }

            @Override
            public void focusLost(FocusEvent e) {
                chatWindow.setEditable(true);
            }
        });
    }//GEN-LAST:event_formWindowOpened

    private void txtChatTextFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtChatTextFocusGained
        if (this.txtChatText.getText().equals("Mensagem")) {
            this.txtChatText.setText("");
        }
    }//GEN-LAST:event_txtChatTextFocusGained

    private void txtChatTextFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtChatTextFocusLost
        if (this.txtChatText.getText().isEmpty()) {
            this.txtChatText.setText("Mensagem");
        }
    }//GEN-LAST:event_txtChatTextFocusLost

    private void sendMessage() {
        if (this.txtChatText.getText().equals("Mensagem")
                || this.txtChatText.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Digite uma mensagem antes de enviar");
        } else {
            if (this.isConected()) {
                Date date = new Date();
                String header = String.format("<%tT %<td/%<tm/%<ty> %s:", date, nickName);
                String chattext = "\n" + this.txtChatText.getText() + "\n\n";
                this.appendToPane(chatWindow, header, Color.RED, true, false, false);
                this.appendToPane(chatWindow, chattext, Color.BLACK, false, false, false);
                this.txtChatText.setText("");
            } else {
                JOptionPane.showMessageDialog(this, "Por favor conecte-se primero com servidor antes de usar o bate-papo");
            }
        }
    }

    private void appendToPane(JTextPane tp, String msg, Color c, boolean bold, boolean italic, boolean underline) {
        StyleContext sc = StyleContext.getDefaultStyleContext();
        AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c);

        aset = sc.addAttribute(aset, StyleConstants.FontFamily, "Lucida Console");
        aset = sc.addAttribute(aset, StyleConstants.Alignment, StyleConstants.ALIGN_JUSTIFIED);
        aset = sc.addAttribute(aset, StyleConstants.Bold, bold);
        aset = sc.addAttribute(aset, StyleConstants.Italic, italic);
        aset = sc.addAttribute(aset, StyleConstants.Underline, underline);

        int len = tp.getDocument().getLength();
        tp.setCaretPosition(len);
        tp.setCharacterAttributes(aset, false);
        tp.replaceSelection(msg);
    }

    private void btnSendChatActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSendChatActionPerformed
        this.sendMessage();
    }//GEN-LAST:event_btnSendChatActionPerformed

    private void txtChatTextKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtChatTextKeyPressed
        int keyCode = evt.getKeyCode();

        if (keyCode == 10) {
            this.sendMessage();
        }
    }//GEN-LAST:event_txtChatTextKeyPressed

    private void btnOptionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOptionsActionPerformed
        optionWindow opw = new optionWindow(this);
        opw.setVisible(true);
    }//GEN-LAST:event_btnOptionsActionPerformed

    private void btnConnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConnActionPerformed
        if (this.isConected()) {
            this.btnConn.setEnabled(false);
            this.closeConnetion();
            this.appendToPane(chatWindow, "Desconectado de " + this.serverAddress + ":" + this.serverPort + "\n", Color.BLUE, true, true, false);
        } else {
            this.btnConn.setEnabled(false);
            this.initConnetion();
        }
    }//GEN-LAST:event_btnConnActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnConn;
    private javax.swing.JButton btnOptions;
    private javax.swing.JButton btnSendChat;
    private javax.swing.JTextPane chatWindow;
    private javax.swing.JList<String> listOnline;
    private javax.swing.JScrollPane panelChatScroll;
    private javax.swing.JScrollPane panelOnline;
    private javax.swing.JPanel penalChatOptions;
    private javax.swing.JTextField txtChatText;
	private void processRead(SelectionKey key) throws IOException {
        this.readBuf.clear();
        SocketChannel sc = (SocketChannel) key.channel();

        int numRead;

        try {
            numRead = sc.read(readBuf);
        } catch (IOException ex) {
            key.cancel();
            sc.close();
            return;
        }

        if (numRead == -1) {
            key.channel().close();
            key.cancel();
            return;
        }

        this.handleResponse(readBuf.array(), numRead);
    }

    private void processWrite(SelectionKey key) {
        WritableByteChannel ch = (WritableByteChannel) key.channel();
        synchronized (writeBuf) {
            writeBuf.flip();

            int bytesOp = 0, bytesTotal = 0;
            try {
                while (writeBuf.hasRemaining() && (bytesOp = ch.write(writeBuf)) > 0) {
                    bytesTotal += bytesOp;

                }
            } catch (IOException ex) {
                Logger.getLogger(mainWindow.class
                        .getName()).log(Level.SEVERE, null, ex);
            }

            bytesOut.addAndGet(bytesTotal);

            if (writeBuf.remaining() == 0) {
                key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
            }

            if (bytesTotal > 0) {
                writeBuf.notify();
            } else if (bytesOp == -1) {
                try {
                    ch.close();

                } catch (IOException ex) {
                    Logger.getLogger(mainWindow.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            }

            writeBuf.compact();
        }
    }

    private void processConnect(SelectionKey key) {
        SocketChannel ch = (SocketChannel) key.channel();
        try {
            if (ch.finishConnect()) {
                key.interestOps(key.interestOps() ^ SelectionKey.OP_CONNECT);
                key.interestOps(key.interestOps() | SelectionKey.OP_READ);

                this.btnConn.setText("Desconectar");
                this.btnOptions.setEnabled(false);
                this.onlineListmodel.addElement(this.nickName);
                this.listOnline.setModel(onlineListmodel);
                this.setConected(true);
                this.appendToPane(chatWindow, "Conectado em " + this.serverAddress + ":" + this.serverPort + "\n", Color.BLUE, true, true, false);
            } else {
                this.appendToPane(chatWindow, "Falha ao conectar ao servidor, favor verifique as configurações no botão \"Opções\"\n\n", Color.red, true, true, false);
            }
        } catch (IOException ex) {
            this.appendToPane(chatWindow, "Falha ao conectar ao servidor, favor verifique as configurações no botão \"Opções\"\n\n", Color.red, true, true, false);
            Logger.getLogger(mainWindow.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
    }
}
