package moze_intel.projecte.rendering;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.item.ItemStack;
import moze_intel.projecte.gameObjs.ObjHandler;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Item-local tooltip fonts; the ordinary GUI/NEI tooltip layout remains in charge. */
@SideOnly(Side.CLIENT)
public final class TomeFontRenderers {
    private static FontRenderer unreadable;
    private static FontRenderer awakened;

    private TomeFontRenderers() {}

    public static FontRenderer current() {
        if (unreadable == null) {
            unreadable = new UnreadableFont();
            awakened = new AwakenedFont();
        }
        return Minecraft.getMinecraft().currentScreen instanceof GuiInventory
            || Minecraft.getMinecraft().currentScreen instanceof GuiContainerCreative ? awakened : unreadable;
    }

    private abstract static class TomeFont extends FontRenderer {
        TomeFont() {
            super(Minecraft.getMinecraft().gameSettings, new ResourceLocation("textures/font/ascii.png"),
                Minecraft.getMinecraft().renderEngine, false);
        }

        @Override
        public int getStringWidth(String text) {
            return Minecraft.getMinecraft().fontRenderer.getStringWidth(text);
        }

        @Override
        public int getCharWidth(char character) {
            return Minecraft.getMinecraft().fontRenderer.getCharWidth(character);
        }

        @Override
        public int drawStringWithShadow(String text, int x, int y, int color) {
            return drawString(text, x, y, color);
        }

        boolean isTitle(String text) {
            return clean(text).startsWith(clean(new ItemStack(ObjHandler.tome).getDisplayName()));
        }

        String clean(String text) {
            return text == null ? "" : text.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "");
        }
    }

    /** Only the title is obfuscated; clues and all other tooltip lines stay readable. */
    private static final class UnreadableFont extends TomeFont {
        @Override
        public int drawString(String text, int x, int y, int color) {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            if (!isTitle(text)) return font.drawString(text, x, y, color);
            String plain = clean(text);
            long frame = Minecraft.getSystemTime() / 45L;
            int cursor = x;
            for (int i = 0; i < plain.length(); i++) {
                char source = plain.charAt(i);
                int width = font.getCharWidth(source);
                if (!Character.isWhitespace(source)) {
                    // ASCII glyphs support vanilla obfuscation even when the source is CJK.
                    String glyph = "\u00a7k" + (char) ('!' + Math.floorMod(frame + i * 31L, 94));
                    int shade = ((frame + i) & 1) == 0 ? 0xFFFFFF : 0x181818;
                    font.drawString(glyph, cursor, y, shade);
                    if ((frame + i) % 3 == 0)
                        font.drawString(glyph, cursor + 1, y - 1, 0xA0A0A0);
                }
                cursor += width;
            }
            return cursor;
        }
    }

    /** Three translucent purple echoes plus a legible breathing foreground. */
    private static final class AwakenedFont extends TomeFont {
        @Override
        public int drawString(String text, int x, int y, int color) {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            if (!isTitle(text)) return font.drawString(text, x, y, color);
            String plain = clean(text);
            long time = Minecraft.getSystemTime();
            double breath = (Math.sin(time / 1050.0) + 1.0) * 0.5;
            int alpha = 22 + (int) (breath * 36);
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glPushMatrix();
            try {
                for (int layer = 3; layer >= 1; layer--) {
                    GL11.glPushMatrix();
                    GL11.glTranslatef((float) (Math.sin(time / 1700.0 + layer) * layer * 0.45),
                        (float) (Math.cos(time / 1900.0 + layer) * layer * 0.35), 0);
                    font.drawString(plain, x, y, ((alpha / layer) << 24) | 0xB14AFF);
                    GL11.glPopMatrix();
                }
                int brightness = 185 + (int) (breath * 55);
                font.drawString(plain, x, y, 0xFF000000 | (brightness << 16) | (135 << 8) | 255);
                // Sparse slow-moving runic interference, leaving the riddle readable.
                long phase = time / 900L;
                int cursor = x;
                for (int i = 0; i < plain.length(); i++) {
                    char c = plain.charAt(i);
                    if (!Character.isWhitespace(c) && Math.floorMod(i * 37L + phase, 79) == 0)
                        font.drawString("\u00a7k" + (char) ('!' + Math.floorMod(phase + i, 94)),
                            cursor, y, (48 << 24) | 0xF0C4FF);
                    cursor += font.getCharWidth(c);
                }
            } finally {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
            return x + font.getStringWidth(plain);
        }
    }
}
