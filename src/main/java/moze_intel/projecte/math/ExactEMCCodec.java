package moze_intel.projecte.math;

import java.math.BigInteger;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

/** Versioned, lossless rational encoding, bounded before network allocation. */
public final class ExactEMCCodec {
    public static final int VERSION = 1;
    public static final int MAX_TERM_BYTES = 8192;
    public static final String BALANCE_KEY = "transmutationEmcExact";
    private ExactEMCCodec() {}

    public static ExactEMC validate(ExactEMC value) {
        if (value == null) throw new NullPointerException("EMC");
        if (value.getNumerator().bitLength() > (MAX_TERM_BYTES - 1) * 8
            || value.getDenominator().bitLength() > (MAX_TERM_BYTES - 1) * 8)
            throw new IllegalArgumentException("EMC exceeds serialization resource limit");
        return value;
    }

    public static ExactEMC validateBalance(ExactEMC value) {
        validate(value);
        if (value.signum() < 0) throw new IllegalArgumentException("Negative EMC balance");
        return value;
    }

    public static void writeNBT(NBTTagCompound parent, String key, ExactEMC value) {
        validate(value);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("version", VERSION);
        tag.setByteArray("numerator", value.getNumerator().toByteArray());
        tag.setByteArray("denominator", value.getDenominator().toByteArray());
        parent.setTag(key, tag);
    }

    public static ExactEMC readNBT(NBTTagCompound parent, String key) {
        if (!parent.hasKey(key, 10)) throw new IllegalArgumentException("Missing EMC compound: " + key);
        NBTTagCompound tag = parent.getCompoundTag(key);
        if (!tag.hasKey("version", 3) || tag.getInteger("version") != VERSION
            || !tag.hasKey("numerator", 7) || !tag.hasKey("denominator", 7))
            throw new IllegalArgumentException("Invalid/unsupported EMC NBT");
        return decode(tag.getByteArray("numerator"), tag.getByteArray("denominator"));
    }

    public static ExactEMC readBalance(NBTTagCompound tag) {
        if (tag.hasKey(BALANCE_KEY)) return validateBalance(readNBT(tag, BALANCE_KEY));
        if (!tag.hasKey("transmutationEmc")) return ExactEMC.ZERO;
        if (!tag.hasKey("transmutationEmc", 99)) throw new IllegalArgumentException("Invalid legacy EMC NBT");
        return validateBalance(ExactEMC.fromLegacyDouble(tag.getDouble("transmutationEmc")));
    }

    public static void writeBalance(NBTTagCompound tag, ExactEMC value) {
        writeNBT(tag, BALANCE_KEY, validateBalance(value));
        tag.removeTag("transmutationEmc"); // Never retain a stale authoritative double.
    }

    public static void write(ByteBuf buf, ExactEMC value) {
        validate(value);
        byte[] numerator = value.getNumerator().toByteArray();
        byte[] denominator = value.getDenominator().toByteArray();
        buf.writeByte(VERSION);
        buf.writeInt(numerator.length);
        buf.writeBytes(numerator);
        buf.writeInt(denominator.length);
        buf.writeBytes(denominator);
    }

    public static ExactEMC read(ByteBuf buf) {
        if (!buf.isReadable() || buf.readUnsignedByte() != VERSION)
            throw new IllegalArgumentException("Unsupported EMC protocol");
        byte[] numerator = readTerm(buf);
        byte[] denominator = readTerm(buf);
        return decode(numerator, denominator);
    }

    public static int encodedSize(ExactEMC value) {
        validate(value);
        return 9 + value.getNumerator().toByteArray().length + value.getDenominator().toByteArray().length;
    }

    private static byte[] readTerm(ByteBuf buf) {
        if (buf.readableBytes() < 4) throw new IllegalArgumentException("Truncated EMC length");
        int length = buf.readInt();
        if (length < 1 || length > MAX_TERM_BYTES || length > buf.readableBytes())
            throw new IllegalArgumentException("Invalid EMC length");
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return bytes;
    }

    private static ExactEMC decode(byte[] numerator, byte[] denominator) {
        if (numerator.length < 1 || denominator.length < 1
            || numerator.length > MAX_TERM_BYTES || denominator.length > MAX_TERM_BYTES)
            throw new IllegalArgumentException("Invalid EMC terms");
        BigInteger n = new BigInteger(numerator), d = new BigInteger(denominator);
        if (d.signum() <= 0) throw new IllegalArgumentException("Nonpositive EMC denominator");
        return validate(ExactEMC.of(n, d));
    }
}
