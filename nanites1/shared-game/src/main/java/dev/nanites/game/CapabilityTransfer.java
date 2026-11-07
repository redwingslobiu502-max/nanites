package dev.nanites.game;

/** Simulation is not a reservation. Account every actually extracted unit, compensate or retain escrow. */
public final class CapabilityTransfer {
    private CapabilityTransfer() {}
    public interface Port {
        int extract(int amount,boolean simulate);
        int receive(int amount,boolean simulate);
    }
    @FunctionalInterface public interface Escrow {void hold(int amount);}
    public record Result(int simulated,int extracted,int moved,int refunded,int held) {}
    private static int bounded(int n,int max){if(n<0||n>max)throw new IllegalStateException("Capability violates quantity contract");return n;}
    public static Result move(Port from,Port to,int amount,boolean preview,Escrow escrow){
        if(amount<1)throw new IllegalArgumentException("Positive quantity required");
        int offered=bounded(from.extract(amount,true),amount);
        int accepted=bounded(to.receive(offered,true),offered);
        if(preview||accepted==0)return new Result(accepted,0,0,0,0);
        int extracted=bounded(from.extract(accepted,false),accepted),moved=0,refunded=0;
        try{moved=bounded(to.receive(extracted,false),extracted);}
        catch(RuntimeException failure){escrow.hold(extracted);throw failure;}
        int remainder=extracted-moved;
        if(remainder>0){try{refunded=bounded(from.receive(remainder,false),remainder);}catch(RuntimeException failure){escrow.hold(remainder);throw failure;}}
        int held=remainder-refunded;if(held>0)escrow.hold(held);
        return new Result(accepted,extracted,moved,refunded,held);
    }
}
