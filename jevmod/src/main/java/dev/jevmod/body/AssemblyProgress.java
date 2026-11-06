package dev.jevmod.body;

/** Tick-based assembly clock; spatial work is sliced by height, never wall-clock sleeps. */
public record AssemblyProgress(int elapsed, int duration) {
    public AssemblyProgress {
        if (duration < 1 || elapsed < 0 || elapsed > duration) throw new IllegalArgumentException("Invalid assembly clock");
    }
    public AssemblyProgress advance() { return new AssemblyProgress(Math.min(duration,elapsed+1), duration); }
    public double fraction() { return (double) elapsed / duration; }
    public double height() { return 1.8 * fraction(); }
    public int completedLayers(int layers) { return (int)Math.floor(layers * fraction()); }
    public boolean finished() { return elapsed == duration; }
}
