package projecte.emc;

import static org.junit.Assert.*;
import java.math.BigInteger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import moze_intel.projecte.math.ExactEMC;
import moze_intel.projecte.math.ExactEMCCodec;
import moze_intel.projecte.emc.SimpleGraphMapper;
import moze_intel.projecte.emc.arithmetics.ExactEMCArithmetic;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

public class ExactEMCCodecTest {
    @Test
    public void fractionAndHugeBalanceRoundTrip() {
        ExactEMC value = ExactEMC.of(BigInteger.TEN.pow(100).subtract(BigInteger.valueOf(64)), BigInteger.valueOf(3));
        ByteBuf buf = Unpooled.buffer();
        try {
            ExactEMCCodec.write(buf, value);
            assertEquals(value, ExactEMCCodec.read(buf));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble("transmutationEmc", 42);
        ExactEMCCodec.writeBalance(tag, value);
        assertFalse(tag.hasKey("transmutationEmc"));
        assertEquals(value, ExactEMCCodec.readBalance(tag));
    }
    @Test
    public void oldFiniteDoubleMigrationPreservesActualValue() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble("transmutationEmc", 0.1);
        assertEquals(ExactEMC.fromLegacyDouble(0.1), ExactEMCCodec.readBalance(tag));
    }
    @Test(expected = IllegalArgumentException.class)
    public void oversizedNetworkTermIsRejectedBeforeAllocation() {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeByte(ExactEMCCodec.VERSION);
            buf.writeInt(Integer.MAX_VALUE);
            ExactEMCCodec.read(buf);
        } finally { buf.release(); }
    }
    @Test(expected = IllegalArgumentException.class)
    public void damagedNewFieldDoesNotFallbackToDouble() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble("transmutationEmc", 42);
        tag.setString(ExactEMCCodec.BALANCE_KEY, "corrupt");
        ExactEMCCodec.readBalance(tag);
    }
    @Test
    public void mappingKeepsFractionExact() {
        SimpleGraphMapper<String, ExactEMC> mapper = new SimpleGraphMapper<>(ExactEMCArithmetic.INSTANCE);
        mapper.setValueBefore("ingot", ExactEMC.of(2048));
        mapper.addConversion(9, "nugget", java.util.Collections.singletonList("ingot"));
        assertEquals(ExactEMC.of(2048).divide(9), mapper.generateValues().get("nugget"));
    }
}
