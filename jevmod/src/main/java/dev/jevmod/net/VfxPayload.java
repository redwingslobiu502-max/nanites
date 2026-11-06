package dev.jevmod.net;

import dev.jevmod.JevMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One server -> client VFX command. The client keeps effects keyed by {@link #key} and refreshes them when the same key
 * arrives again (a continuous laser re-sends every tick), so motion stays smooth and nothing lingers when updates stop.
 *
 * @param kind     {@link #BEAM}, {@link #RING}, {@link #ORB}, {@link #SPHERE} or {@link #STOP}
 * @param anchor   entity id the effect follows, or -1
 * @param anchorMode {@link #ANCHOR_NONE}, {@link #ANCHOR_EYES} (beam starts at the eyes), {@link #ANCHOR_CENTER}
 * @param style    {@link #STYLE_SOLID}, {@link #STYLE_PULSE}, {@link #STYLE_ARC} (electric zig-zag)
 * @param extra    ring: max radius; sphere/orb: radius
 */
public record VfxPayload(byte kind, String key, int anchor, byte anchorMode,
                         double fx, double fy, double fz, double tx, double ty, double tz,
                         int color, float width, float intensity, int duration, byte style, float extra)
        implements CustomPacketPayload {

    public static final byte BEAM = 0, RING = 1, ORB = 2, SPHERE = 3, STOP = 4;
    public static final byte ANCHOR_NONE = 0, ANCHOR_EYES = 1, ANCHOR_CENTER = 2;
    public static final byte STYLE_SOLID = 0, STYLE_PULSE = 1, STYLE_ARC = 2;

    public static final Type<VfxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JevMod.MODID, "vfx"));

    public static final StreamCodec<FriendlyByteBuf, VfxPayload> CODEC = StreamCodec.of(VfxPayload::write, VfxPayload::read);

    private static void write(FriendlyByteBuf b, VfxPayload p) {
        b.writeByte(p.kind);
        b.writeUtf(p.key, 128);
        b.writeVarInt(p.anchor + 1);
        b.writeByte(p.anchorMode);
        b.writeDouble(p.fx);
        b.writeDouble(p.fy);
        b.writeDouble(p.fz);
        b.writeDouble(p.tx);
        b.writeDouble(p.ty);
        b.writeDouble(p.tz);
        b.writeInt(p.color);
        b.writeFloat(p.width);
        b.writeFloat(p.intensity);
        b.writeVarInt(p.duration);
        b.writeByte(p.style);
        b.writeFloat(p.extra);
    }

    private static VfxPayload read(FriendlyByteBuf b) {
        return new VfxPayload(b.readByte(), b.readUtf(128), b.readVarInt() - 1, b.readByte(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(),
                b.readInt(), b.readFloat(), b.readFloat(), b.readVarInt(), b.readByte(), b.readFloat());
    }

    public static VfxPayload stop(String key) {
        return new VfxPayload(STOP, key, -1, ANCHOR_NONE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, STYLE_SOLID, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
