/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.snaity.chatserver;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.snaity.chatserver.Network.*;
import org.snaity.chatserver.ThreadWorker.Worker;

/**
 *
 * @author Adriano
 */
public class App {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            Worker worker = new Worker();
            new Thread(worker).start();
            new Thread(new Server(worker)).start();
        } catch (IOException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
