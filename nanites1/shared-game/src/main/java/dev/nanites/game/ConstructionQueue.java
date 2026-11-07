package dev.nanites.game;

import com.google.gson.JsonObject;
import dev.nanites.core.SafeData;
import java.util.*;

/** All methods are confined to the server thread. Validates before replacing last-known-good job. */
public final class ConstructionQueue {
    public interface World {
        Object resolve(String block);
        boolean valid(Blueprint.Pos p);
        boolean loaded(Blueprint.Pos p);
        String observe(Blueprint.Pos p);
        String describe(Object state);
        boolean place(Blueprint.Pos p,Object state);
    }
    private record Step(Blueprint.Pos pos,Object state) {}
    private static final class Job {
        final long revision;final Blueprint blueprint;final World world;final List<Step> steps;
        int cursor,placed,matched;String status="QUEUED",error="",observed="";Blueprint.Pos last;
        Job(long r,Blueprint b,World w,List<Step> s){revision=r;blueprint=b;world=w;steps=List.copyOf(s);}
        boolean active(){return Set.of("QUEUED","RUNNING","WAITING_CHUNK").contains(status);}
    }
    private final Map<UUID,Job> jobs=new LinkedHashMap<>();
    private final Map<UUID,Long> revisions=new HashMap<>();
    private final Thread serverThread=Thread.currentThread();
    private final int perTick,maximum;private final long nanos;private int rotation;
    public ConstructionQueue(int perTick,int maximum,long nanos){if(perTick<1||maximum<1||nanos<1)throw new IllegalArgumentException("Limites de construção positivos");this.perTick=perTick;this.maximum=maximum;this.nanos=nanos;}
    private void thread(){if(Thread.currentThread()!=serverThread)throw new IllegalStateException("Construction requires server thread");}
    public long revision(UUID id){thread();return revisions.getOrDefault(id,0L);}
    public JsonObject submit(UUID id,String source,java.util.function.Function<Blueprint.Origin,World> bind) {
        thread();Blueprint b=Blueprint.parse(source,maximum);World w=bind.apply(b.origin());List<Step> steps=new ArrayList<>();Map<String,Object> states=new HashMap<>();
        for(var v:b.voxels()){var p=b.absolute(v);if(!w.valid(p))throw new IllegalArgumentException("Coordenada fora do mundo: "+p);Object state=states.computeIfAbsent(v.block(),w::resolve);if(state==null)throw new IllegalArgumentException("Estado inexistente: "+v.block());steps.add(new Step(p,state));}
        long r=Math.incrementExact(revision(id));Job next=new Job(r,b,w,steps);jobs.put(id,next);revisions.put(id,r);return status(id);
    }
    public JsonObject cancel(UUID id){thread();Job j=jobs.get(id);if(j!=null&&j.active()){j.status="CANCELLED";revisions.put(id,Math.incrementExact(revision(id)));}return status(id);}
    public void forget(UUID id){cancel(id);jobs.remove(id);revisions.remove(id);}
    public JsonObject status(UUID id) {
        thread();JsonObject o=new JsonObject();o.addProperty("revision",revision(id));Job j=jobs.get(id);
        if(j==null){o.addProperty("status","IDLE");return o;}
        o.addProperty("job_revision",j.revision);o.addProperty("status",j.status);o.addProperty("processed",j.cursor);o.addProperty("total",j.steps.size());o.addProperty("placed",j.placed);o.addProperty("already_matched",j.matched);o.addProperty("error",j.error);
        o.add("origin",SafeData.GSON.toJsonTree(j.blueprint.origin()));
        if(j.last!=null){o.add("last_position",SafeData.GSON.toJsonTree(j.last));o.addProperty("observed",j.world.loaded(j.last)?j.world.observe(j.last):"CHUNK_UNLOADED");}
        if(j.active()&&j.cursor<j.steps.size())o.add("next_position",SafeData.GSON.toJsonTree(j.steps.get(j.cursor).pos()));
        o.addProperty("rollback",false);return o;
    }
    /** Global budget, round robin between owners. No chunk tickets; unloaded work waits. */
    public void tick(){
        thread();List<Job> active=jobs.values().stream().filter(Job::active).toList();if(active.isEmpty())return;
        long start=System.nanoTime();int remaining=perTick,blocked=0;int index=Math.floorMod(rotation++,active.size());
        while(remaining>0&&System.nanoTime()-start<nanos&&blocked<active.size()) {
            Job j=active.get(index);index=(index+1)%active.size();
            if(!j.active()){blocked++;continue;}
            Step step=j.steps.get(j.cursor);
            try {
                if(!j.world.loaded(step.pos())){j.status="WAITING_CHUNK";blocked++;continue;}
                blocked=0;remaining--;j.status="RUNNING";j.last=step.pos();String desired=j.world.describe(step.state());
                String before=j.world.observe(step.pos());
                if(before.equals(desired))j.matched++;
                else {boolean accepted=j.world.place(step.pos(),step.state());j.observed=j.world.observe(step.pos());if(!accepted||!j.observed.equals(desired))throw new IllegalStateException("setBlock não confirmou estado; observado="+j.observed+", desejado="+desired);j.placed++;}
                j.cursor++;if(j.cursor==j.steps.size())j.status="COMPLETED";
            }catch(RuntimeException e){j.status="FAILED";j.error=e.getClass().getSimpleName()+": "+e.getMessage();}
        }
    }
}
