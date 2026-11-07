package dev.nanites.game;

import dev.nanites.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/** One bounded, coalescing writer. Main thread submits detached runtime copies, never disk writes. */
final class AsyncSnapshots implements AutoCloseable {
    private final SnapshotStore store;
    private final BiConsumer<UUID,String> error;
    private final Map<UUID,NaniteSession> latest = new LinkedHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r,"nanites-snapshots"); t.setDaemon(true); return t;
    });
    private boolean running, closed;
    AsyncSnapshots(SnapshotStore store, BiConsumer<UUID,String> error) { this.store=store; this.error=error; }
    synchronized NaniteSession pending(UUID id) {
        var s=latest.get(id); return s==null?null:s.fork(id.toString());
    }
    synchronized void submit(UUID id,NaniteSession s) {
        if(closed)throw new IllegalStateException("Snapshot writer stopped");
        if(!latest.containsKey(id)&&latest.size()>=128)throw new IllegalStateException("Snapshot queue quota; save pending");
        latest.put(id,s.fork(id.toString()));
        if(!running){running=true;worker.execute(this::drain);}
    }
    private void drain() {
        Set<UUID> failed=new HashSet<>();
        while(true){UUID id;NaniteSession s;
            synchronized(this){var entry=latest.entrySet().stream().filter(e->!failed.contains(e.getKey())).findFirst();
                if(entry.isEmpty()){running=false;return;} id=entry.get().getKey();s=entry.get().getValue();}
            try{store.write(id.toString(),s.save(id.toString()));
                synchronized(this){latest.remove(id,s);}
            }catch(Exception e){failed.add(id);error.accept(id,"Falha salvar snapshot (cópia em memória retida): "+e.getMessage());}
        }
    }
    @Override public void close() {
        synchronized(this){closed=true;if(!running&&!latest.isEmpty()){running=true;worker.execute(this::drain);}worker.shutdown();}
        try{if(!worker.awaitTermination(10,TimeUnit.SECONDS))throw new IllegalStateException("Snapshot flush timeout; backup required");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Snapshot flush interrupted",e);}
        synchronized(this){if(!latest.isEmpty())throw new IllegalStateException("Snapshots unsaved: "+latest.size()+"; inspect disk/backup");}
    }
}
