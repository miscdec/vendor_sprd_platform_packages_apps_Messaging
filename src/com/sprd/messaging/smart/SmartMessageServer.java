/**
 * Created by SPRD on 2019/02/13.
 */
package com.sprd.messaging.smart;

import java.util.ArrayList;

public final class SmartMessageServer {

    private static SmartMessageServer smartMessageServer = null;

    private ArrayList<SmartMessageObserver> list = null;

    private SmartMessageServer(){
        list = new ArrayList<SmartMessageObserver>();
    }

    public static SmartMessageServer getInstance(){
        synchronized (SmartMessageServer.class){
            if(null == smartMessageServer){
                smartMessageServer = new SmartMessageServer();
            }
        }
        return smartMessageServer;
    }

    public void addRegister(SmartMessageObserver observer){
        list.add(observer);
    }

    public void removeRegister(SmartMessageObserver observer){
        if(!list.isEmpty()){
            list.remove(observer);
        }
    }

    public void notifyObserver(String partId){
        for(int i = 0; i < list.size(); i++){
            list.get(i).updateSmartMessage(partId);
        }
    }

}

