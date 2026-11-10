package com.xoclassic.app;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owned by one optional background GET only. Never attached to login or posting requests. */
public final class ReadCancellation {
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile HttpURLConnection connection;
    private volatile boolean started;
    public void begin()throws IOException{check();started=true;check();}
    public boolean hasStarted(){return started;}
    public boolean isCancelled(){return cancelled.get();}
    public void check()throws IOException{if(isCancelled())throw new IOException("Background author lookup cancelled.");}
    public void attach(HttpURLConnection value)throws IOException{connection=value;check();}
    public void detach(HttpURLConnection value){if(connection==value)connection=null;}
    public void cancel(){
        if(!cancelled.compareAndSet(false,true))return;
        HttpURLConnection active=connection;if(active!=null){Thread closer=new Thread(()->{try{active.disconnect();}catch(RuntimeException ignored){}},"cancel-author-read");closer.setDaemon(true);closer.start();}
    }
}
