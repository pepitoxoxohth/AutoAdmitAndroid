package com.xoclassic.app;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/** One foreground lane and one paced maintenance lane; session-sensitive work is exclusive. */
public final class ForegroundExecutor extends AbstractExecutorService {
    private final ExecutorService foreground=Executors.newSingleThreadExecutor(),background=Executors.newSingleThreadExecutor();
    private static final ReentrantReadWriteLock gate=new ReentrantReadWriteLock(true);
    private static final ReentrantLock foregroundLane=new ReentrantLock(true),backgroundLane=new ReentrantLock(true);
    private static final ThreadLocal<Boolean> MAINTENANCE=ThreadLocal.withInitial(()->false);
    public static boolean isBackgroundThread(){return MAINTENANCE.get();}
    private Runnable guarded(Runnable command,boolean read,boolean maintenance){return ()->{Lock lane=maintenance?backgroundLane:foregroundLane,lock=read?gate.readLock():gate.writeLock();boolean laneHeld=false,gateHeld=false;try{lane.lockInterruptibly();laneHeld=true;lock.lockInterruptibly();gateHeld=true;MAINTENANCE.set(maintenance);command.run();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}finally{MAINTENANCE.remove();if(gateHeld)lock.unlock();if(laneHeld)lane.unlock();}};}
    @Override public void execute(Runnable command){foreground.execute(guarded(command,false,false));}
    public void executeRead(Runnable command){foreground.execute(guarded(command,true,false));}
    public void executeBackground(Runnable command){background.execute(guarded(command,true,true));}
    @Override public void shutdown(){foreground.shutdown();background.shutdown();}
    @Override public List<Runnable> shutdownNow(){List<Runnable> pending=new ArrayList<>(foreground.shutdownNow());pending.addAll(background.shutdownNow());return pending;}
    @Override public boolean isShutdown(){return foreground.isShutdown()&&background.isShutdown();}
    @Override public boolean isTerminated(){return foreground.isTerminated()&&background.isTerminated();}
    @Override public boolean awaitTermination(long timeout,TimeUnit unit)throws InterruptedException{long budget=unit.toNanos(timeout),start=System.nanoTime();return foreground.awaitTermination(budget,TimeUnit.NANOSECONDS)&&background.awaitTermination(Math.max(0,budget-(System.nanoTime()-start)),TimeUnit.NANOSECONDS);}
}