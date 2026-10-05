package moze_intel.projecte.integration.ae2;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
/** AppliedE persistent recovery v4. Owner list/token retrieval is safe; admin review NEVER refunds automatically. */
public final class CommandEMCRecovery extends CommandBase {
    @Override public String getCommandName() { return "emcrecovery"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public String getCommandUsage(ICommandSender sender) {
        return "/emcrecovery list | token <id> | status x y z | approve x y z CONFIRM | discard x y z CONFIRM | audits | ack <id> CONFIRM | knowledge | knowledge-approve <id> CONFIRM | knowledge-discard <id> CONFIRM | import-legacy CONFIRM";
    }
    private static void say(ICommandSender sender, String text) { sender.addChatMessage(new ChatComponentText(text)); }
    @Override public void processCommand(ICommandSender sender, String[] args) {
        try {
            if (args.length == 0) { say(sender, getCommandUsage(sender)); return; }
            EMCRecoveryLedger ledger = EMCRecoveryLedger.get();
            if (args[0].equals("list") && args.length == 1) {
                EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                java.util.List<String> lines = ledger.list(player.getUniqueID());
                for (int i = 0; i < Math.min(50, lines.size()); i++) say(sender, lines.get(i));
                say(sender, "Receipts=" + lines.size() + "; displayed at most 50"); return;
            }
            if (args[0].equals("token") && args.length == 2) {
                EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                ItemStack token = ledger.token(args[1], player.getUniqueID());
                if (!player.inventory.addItemStackToInventory(token)) throw new IllegalArgumentException("Inventory full; no receipt consumed");
                player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges(); return;
            }
            if (!sender.canCommandSenderUseCommand(2, getCommandName())) throw new IllegalArgumentException("Operator permission required");
            if (args[0].equals("audits") && args.length == 1) {
                java.util.List<String> lines = ledger.audits();
                for (int i = 0; i < Math.min(50, lines.size()); i++) say(sender, lines.get(i));
                say(sender, "Audits=" + lines.size() + "; displayed at most 50; inspect ledger backup for full history"); return;
            }
            if (args[0].equals("ack") && args.length == 3 && args[2].equals("CONFIRM")) { ledger.acknowledge(args[1]); say(sender, "Audit acknowledged. No balances changed."); return; }
            if (args[0].equals("knowledge") && args.length == 1) {
                java.util.List<String> lines = ledger.knowledgeTasks();
                for (int i = 0; i < Math.min(50, lines.size()); i++) say(sender, lines.get(i)); return;
            }
            if ((args[0].equals("knowledge-approve") || args[0].equals("knowledge-discard")) && args.length == 3 && args[2].equals("CONFIRM")) {
                ledger.reviewKnowledge(args[1], args[0].equals("knowledge-approve")); return;
            }
            if (args[0].equals("import-legacy") && args.length == 2 && args[1].equals("CONFIRM")) {
                EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                ItemStack replacement = ledger.importLegacy(player.getUniqueID(), player.getHeldItem());
                player.inventory.setInventorySlotContents(player.inventory.currentItem, replacement);
                player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges(); return;
            }
            if ((args[0].equals("status") && args.length == 4)
                    || ((args[0].equals("approve") || args[0].equals("discard")) && args.length == 5 && args[4].equals("CONFIRM"))) {
                int x = Integer.parseInt(args[1]), y = Integer.parseInt(args[2]), z = Integer.parseInt(args[3]);
                if (!sender.getEntityWorld().blockExists(x, y, z)) throw new IllegalArgumentException("Target chunk not loaded");
                TileEntity raw = sender.getEntityWorld().getTileEntity(x, y, z);
                if (!(raw instanceof TileMEEMCLink)) throw new IllegalArgumentException("Target is not an EMC link");
                TileMEEMCLink tile = (TileMEEMCLink) raw; tile.attachRecoveryLedger();
                if (!args[0].equals("status")) { ledger.approveQueue(tile, args[0].equals("discard")); tile.reloadRecoveryLedger(); }
                say(sender, ledger.status(tile)); return;
            }
            say(sender, getCommandUsage(sender));
        } catch (Exception failure) { say(sender, "Recovery refused: " + failure.getMessage()); }
    }
}
