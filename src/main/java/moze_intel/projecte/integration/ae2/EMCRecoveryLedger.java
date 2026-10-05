package moze_intel.projecte.integration.ae2;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import moze_intel.projecte.events.TickEvents;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.math.ExactEMC;
import net.minecraft.entity.player.EntityPlayer;

/** AppliedE persistent recovery v4.
 * Authoritative queues and receipts, NEVER a claim of atomicity with vanilla/AE save files.
 * Full snapshots are fsynced then atomically renamed. Any uncertain IO poisons this session.
 * External transfers need operator review after restart; no automatic refund or replay.
 */
public final class EMCRecoveryLedger {
    private static EMCRecoveryLedger instance;
    private static RuntimeException fatal;
    private static final int MAX_RECORDS = 100000;
    private static final long MAX_FILE = 64L * 1024 * 1024;
    private final File file;
    private NBTTagCompound data;
    private final Set<String> warned = new HashSet<>();
    private EMCRecoveryLedger(File file, NBTTagCompound data) { this.file = file; this.data = data; }
    public static EMCRecoveryLedger get() {
        TickEvents.requireServerThread();
        if (fatal != null) throw fatal;
        if (instance != null) return instance;
        try {
            MinecraftServer server = MinecraftServer.getServer();
            WorldServer world = server == null ? null : server.worldServerForDimension(0);
            if (world == null) throw new IOException("Overworld unavailable");
            File file = world.getSaveHandler().getMapFileFromName("projecte_appliede_ledger_v4");
            if (file == null) throw new IOException("Save handler has no map file path");
            if (new File(file.getPath() + ".pending").exists())
                throw new IOException("Interrupted ledger save: inspect .pending and main file, do not auto-replay");
            NBTTagCompound data;
            if (file.exists()) {
                if (file.length() <= 0 || file.length() > MAX_FILE) throw new IOException("Ledger file size invalid");
                try (InputStream in = new FileInputStream(file)) { data = CompressedStreamTools.readCompressed(in); }
                validate(data);
            } else {
                data = new NBTTagCompound(); data.setInteger("Version", 4);
                data.setString("World", UUID.randomUUID().toString());
                data.setTag("Queues", new NBTTagCompound()); data.setTag("Receipts", new NBTTagCompound());
                data.setTag("Knowledge", new NBTTagCompound()); data.setTag("Audits", new NBTTagCompound());
            }
            EMCRecoveryLedger ledger = new EMCRecoveryLedger(file, data);
            // Never automatically replay a queue across restart: AE CPU and destination save are independent.
            NBTTagCompound next = copy(data), queues = next.getCompoundTag("Queues");
            for (String key : keys(queues)) {
                NBTTagCompound q = queues.getCompoundTag(key);
                if (q.getTagList("Items", 10).tagCount() > 0 || q.getBoolean("External")) {
                    q.setBoolean("Hold", true); q.setString("Reason", "restart_external_save_uncertainty");
                }
            }
            NBTTagCompound audits = next.getCompoundTag("Audits");
            for (String key : keys(audits)) audits.getCompoundTag(key).setBoolean("Review", true);
            NBTTagCompound knowledge = next.getCompoundTag("Knowledge");
            for (String key : keys(knowledge)) {
                NBTTagCompound task = knowledge.getCompoundTag(key);
                task.setBoolean("WasReadyBeforeRestart", task.getBoolean("Ready"));
                task.setBoolean("Ready", false); task.setBoolean("Review", true);
            }
            ledger.persist(next); instance = ledger; return ledger;
        } catch (Exception failure) { throw poison(failure); }
    }
    private static RuntimeException poison(Exception failure) {
        fatal = new IllegalStateException("AppliedE ledger is unavailable/uncertain; stop server and inspect backups", failure);
        return fatal;
    }
    public static void clear() { instance = null; fatal = null; }
    private static NBTTagCompound copy(NBTTagCompound tag) { return (NBTTagCompound) tag.copy(); }
    @SuppressWarnings("unchecked") private static Set<String> keys(NBTTagCompound tag) {
        return new HashSet<String>(tag.func_150296_c());
    }
    private static void validate(NBTTagCompound tag) throws IOException {
        if (tag.getInteger("Version") != 4) throw new IOException("Unknown ledger version");
        try { UUID.fromString(tag.getString("World")); }
        catch (IllegalArgumentException ex) { throw new IOException("Invalid world identity", ex); }
        for (String section : new String[] {"Queues", "Receipts", "Knowledge", "Audits"}) {
            if (!tag.hasKey(section, 10) || keys(tag.getCompoundTag(section)).size() > MAX_RECORDS)
                throw new IOException("Invalid ledger section: " + section);
            for (String id : keys(tag.getCompoundTag(section)))
                if (!tag.getCompoundTag(section).hasKey(id, 10)) throw new IOException("Invalid record type");
        }
        for (String id : keys(tag.getCompoundTag("Queues"))) {
            NBTTagCompound q = tag.getCompoundTag("Queues").getCompoundTag(id);
            UUID.fromString(q.getString("Id")); UUID.fromString(q.getString("Owner"));
            if (!q.hasKey("Items", 9)) throw new IOException("Missing authoritative queue payload");
            validateItems(q.getTagList("Items", 10), true);
        }
        for (String id : keys(tag.getCompoundTag("Knowledge"))) {
            UUID.fromString(id);
            NBTTagCompound task = tag.getCompoundTag("Knowledge").getCompoundTag(id);
            UUID.fromString(task.getString("Owner"));
            if (!task.hasKey("Item", 10) || ItemStack.loadItemStackFromNBT(task.getCompoundTag("Item")) == null)
                throw new IOException("Invalid durable knowledge payload");
        }
        for (String id : keys(tag.getCompoundTag("Audits"))) {
            UUID.fromString(id);
            NBTTagCompound audit = tag.getCompoundTag("Audits").getCompoundTag(id);
            if (!audit.hasKey("Owners", 10) || keys(audit.getCompoundTag("Owners")).isEmpty())
                throw new IOException("Invalid balance audit owners");
            for (String owner : keys(audit.getCompoundTag("Owners"))) UUID.fromString(owner);
            String state = audit.getString("State");
            if (!state.equals("PREPARED") && !state.equals("COMMITTED") && !state.equals("FAILED_REVIEW"))
                throw new IOException("Invalid balance audit state");
        }
        for (String id : keys(tag.getCompoundTag("Receipts"))) {
            UUID.fromString(id);
            NBTTagCompound r = tag.getCompoundTag("Receipts").getCompoundTag(id);
            UUID.fromString(r.getString("Owner"));
            if (!r.hasKey("Items", 9)) throw new IOException("Missing receipt payload");
            validateItems(r.getTagList("Items", 10), false);
            String state = r.getString("State");
            if (!state.equals("READY") && !state.equals("DONE")) throw new IOException("Invalid receipt state");
        }
    }
    public static void validateItems(NBTTagList list, boolean emptyOK) {
        if (list.tagCount() > 256 || !emptyOK && list.tagCount() == 0)
            throw new IllegalArgumentException("Invalid recovery entry count");
        long total = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
            long count = entry.getLong("EMCOutputCount");
            if (!entry.hasKey("EMCOutputCount", 4) || entry.getByte("Count") != 1 || stack == null
                    || count <= 0 || count > 65536 - total
                    || stack.getItem() == AE2Integration.itemEMCRecoveryBundle
                    || stack.getItem() == AE2Integration.itemEMCTransmutationPattern
                    || stack.getItem() == AE2Integration.itemEMCResource && ItemEMCResource.tier(stack) == 0)
                throw new IllegalArgumentException("Invalid recovery payload; quarantine instead of dropping entries");
            total += count;
        }
    }
    private void persist(NBTTagCompound next) {
        if (fatal != null) throw fatal;
        try {
            validate(next);
            File dir = file.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create ledger directory");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(next, bytes);
            if (bytes.size() > MAX_FILE) throw new IOException("Ledger capacity exceeded");
            Path pending = Paths.get(file.getPath() + ".pending");
            try (FileOutputStream out = new FileOutputStream(pending.toFile())) {
                out.write(bytes.toByteArray()); out.flush(); out.getFD().sync();
            }
            // No non-atomic fallback; fail closed on unsupported filesystems / sharing violations.
            Files.move(pending, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            data = next;
        } catch (Exception failure) { throw poison(failure); }
    }
    private static String location(TileMEEMCLink tile) {
        return tile.getWorldObj().provider.dimensionId + ":" + tile.xCoord + ":" + tile.yCoord + ":" + tile.zCoord;
    }
    /** Location's authoritative slot persists across replacement. Old tile NBT never re-imports an exported queue. */
    public NBTTagCompound attach(TileMEEMCLink tile, UUID owner, NBTTagList legacy) {
        String key = location(tile); NBTTagCompound queues = data.getCompoundTag("Queues");
        if (queues.hasKey(key, 10)) {
            NBTTagCompound q = queues.getCompoundTag(key);
            if (!q.getString("Owner").equals(owner.toString())) {
                if (q.getTagList("Items", 10).tagCount() != 0 || q.getBoolean("Hold"))
                    throw new IllegalStateException("Previous owner's queue is reserved at this position");
                NBTTagCompound next = copy(data), newQ = next.getCompoundTag("Queues").getCompoundTag(key);
                newQ.setString("Owner", owner.toString()); persist(next);
            }
            return copy(data.getCompoundTag("Queues").getCompoundTag(key));
        }
        if (keys(queues).size() >= MAX_RECORDS) throw new IllegalStateException("Queue ledger full");
        validateItems(legacy, true);
        NBTTagCompound next = copy(data), q = new NBTTagCompound();
        q.setString("Id", UUID.randomUUID().toString()); q.setString("Owner", owner.toString());
        q.setTag("Items", legacy.copy());
        // First-time legacy migration must be approved: an old CPU save cannot prove these outputs are unpaid.
        q.setBoolean("Hold", legacy.tagCount() > 0); q.setString("Reason", "legacy_queue_import_review");
        next.getCompoundTag("Queues").setTag(key, q); persist(next); return copy(q);
    }
    public boolean held(TileMEEMCLink tile) {
        return data.getCompoundTag("Queues").getCompoundTag(location(tile)).getBoolean("Hold");
    }
    public NBTTagList queue(TileMEEMCLink tile) {
        return (NBTTagList) data.getCompoundTag("Queues").getCompoundTag(location(tile)).getTagList("Items", 10).copy();
    }
    public void storeQueue(TileMEEMCLink tile, NBTTagList list) {
        validateItems(list, true);
        if (held(tile)) throw new IllegalStateException("Queue requires recovery review");
        NBTTagCompound next = copy(data), q = next.getCompoundTag("Queues").getCompoundTag(location(tile));
        q.setTag("Items", list.copy()); q.setBoolean("External", true); persist(next);
    }
    /** Write uncertainty BEFORE invoking external storage. An exception leaves the queue held. */
    public void beginDelivery(TileMEEMCLink tile, NBTTagCompound offered) {
        if (held(tile)) throw new IllegalStateException("Queue held");
        NBTTagCompound next = copy(data), q = next.getCompoundTag("Queues").getCompoundTag(location(tile));
        q.setBoolean("Hold", true); q.setString("Reason", "delivery_in_flight");
        q.setTag("LastOffer", offered.copy()); persist(next);
    }
    public void endDelivery(TileMEEMCLink tile, NBTTagList remainder, long accepted) {
        validateItems(remainder, true);
        NBTTagCompound next = copy(data), q = next.getCompoundTag("Queues").getCompoundTag(location(tile));
        if (!q.getString("Reason").equals("delivery_in_flight")) throw new IllegalStateException("No pending delivery");
        q.setTag("Items", remainder.copy()); q.setLong("LastAccepted", accepted);
        q.setBoolean("External", true); q.setBoolean("Hold", false); q.setString("Reason", ""); persist(next);
    }
    /** Receipt creation and source queue removal are ONE authoritative ledger snapshot. */
    public ItemStack export(TileMEEMCLink tile, UUID owner) {
        if (held(tile)) throw new IllegalStateException("Held queue cannot be exported");
        NBTTagList items = queue(tile); validateItems(items, false);
        if (keys(data.getCompoundTag("Receipts")).size() >= MAX_RECORDS) throw new IllegalStateException("Receipt ledger full");
        String id = UUID.randomUUID().toString(); NBTTagCompound next = copy(data), r = new NBTTagCompound();
        r.setString("Owner", owner.toString()); r.setString("State", "READY"); r.setTag("Items", items.copy());
        r.setString("Source", location(tile)); next.getCompoundTag("Receipts").setTag(id, r);
        next.getCompoundTag("Queues").getCompoundTag(location(tile)).setTag("Items", new NBTTagList());
        persist(next); return token(id, owner);
    }
    public ItemStack token(String id, UUID owner) {
        NBTTagCompound r = data.getCompoundTag("Receipts").getCompoundTag(id);
        if (!r.getString("Owner").equals(owner.toString()) || !r.getString("State").equals("READY"))
            throw new IllegalArgumentException("Unknown, foreign or consumed receipt");
        ItemStack token = new ItemStack(AE2Integration.itemEMCRecoveryBundle);
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("RecoveryVersion", 4);
        tag.setString("World", data.getString("World")); tag.setString("Receipt", id);
        token.setTagCompound(tag); return token;
    }
    public boolean redeem(TileMEEMCLink tile, UUID owner, ItemStack token) {
        if (held(tile) || token == null || token.stackSize != 1 || !token.hasTagCompound()) return false;
        NBTTagCompound tag = token.getTagCompound();
        if (tag.getInteger("RecoveryVersion") != 4 || !data.getString("World").equals(tag.getString("World"))) return false;
        String id = tag.getString("Receipt");
        NBTTagCompound r = data.getCompoundTag("Receipts").getCompoundTag(id);
        if (!r.getString("Owner").equals(owner.toString()) || !r.getString("State").equals("READY")) return false;
        // Destination must be empty: no accidental merge overflow, receipt queue can be checked as a unit.
        if (queue(tile).tagCount() != 0) return false;
        NBTTagCompound next = copy(data), q = next.getCompoundTag("Queues").getCompoundTag(location(tile));
        q.setTag("Items", r.getTagList("Items", 10).copy()); q.setBoolean("External", true);
        next.getCompoundTag("Receipts").getCompoundTag(id).setString("State", "DONE");
        next.getCompoundTag("Receipts").getCompoundTag(id).setString("Destination", location(tile));
        persist(next); token.stackSize = 0; token.setTagCompound(null); return true;
    }
    public List<String> list(UUID owner) {
        List<String> result = new ArrayList<>();
        for (String id : keys(data.getCompoundTag("Receipts"))) {
            NBTTagCompound r = data.getCompoundTag("Receipts").getCompoundTag(id);
            if (r.getString("Owner").equals(owner.toString())) result.add(id + " " + r.getString("State"));
        }
        Collections.sort(result); return result;
    }
    /** Operator approval is an explicit assertion after comparing CPU/player/storage saves, not automatic recovery. */
    public void approveQueue(TileMEEMCLink tile, boolean discard) {
        if (!held(tile)) throw new IllegalArgumentException("Queue is not quarantined; approval/discard is unnecessary");
        NBTTagCompound next = copy(data), q = next.getCompoundTag("Queues").getCompoundTag(location(tile));
        if (!next.getCompoundTag("Queues").hasKey(location(tile), 10)) throw new IllegalArgumentException("Queue not attached");
        if (discard) q.setTag("Items", new NBTTagList());
        q.setBoolean("Hold", false); q.setBoolean("External", false); q.setString("Reason", "operator_reviewed");
        persist(next);
    }
    public String status(TileMEEMCLink tile) {
        NBTTagCompound q = data.getCompoundTag("Queues").getCompoundTag(location(tile));
        return "Queue " + q.getString("Id") + " entries=" + q.getTagList("Items", 10).tagCount()
            + " hold=" + q.getBoolean("Hold") + " reason=" + q.getString("Reason") + " lastAccepted=" + q.getLong("LastAccepted");
    }
    /** Knowledge intent is durable BEFORE ordinary item balance mutation; drain waits for explicit commit marker. */
    public String prepareKnowledge(UUID owner, ItemStack item) {
        if (keys(data.getCompoundTag("Knowledge")).size() >= MAX_RECORDS) throw new IllegalStateException("Knowledge outbox full");
        NBTTagCompound next = copy(data), task = new NBTTagCompound(); String id = UUID.randomUUID().toString();
        ItemStack single = item.copy(); single.stackSize = 1;
        task.setString("Owner", owner.toString()); task.setTag("Item", single.writeToNBT(new NBTTagCompound()));
        task.setBoolean("Ready", false); next.getCompoundTag("Knowledge").setTag(id, task); persist(next); return id;
    }
    public void commitKnowledge(String id) {
        NBTTagCompound next = copy(data);
        NBTTagCompound task = next.getCompoundTag("Knowledge").getCompoundTag(id);
        task.setBoolean("Ready", true); task.setBoolean("Review", false); persist(next);
    }
    public void cancelKnowledge(String id) {
        NBTTagCompound next = copy(data); next.getCompoundTag("Knowledge").removeTag(id); persist(next);
    }
    public void drainKnowledge() {
        int budget = 16;
        for (String id : keys(data.getCompoundTag("Knowledge"))) {
            if (budget-- <= 0) break;
            NBTTagCompound task = data.getCompoundTag("Knowledge").getCompoundTag(id);
            if (!task.getBoolean("Ready")) continue; // Uncertain mutation: operator decides via knowledge command.
            EntityPlayer player = EMCInventoryHandler.resolveOnlinePlayer(UUID.fromString(task.getString("Owner")));
            if (player == null) continue;
            ItemStack learned = ItemStack.loadItemStackFromNBT(task.getCompoundTag("Item"));
            if (learned == null) throw new IllegalStateException("Unregistered knowledge item in durable outbox");
            try {
                moze_intel.projecte.utils.PEGeneralPurposeUtils.addKnowledgeSafe(learned.copy(), player);
                Transmutation.syncIncremental(player, learned, false);
                AE2Integration.notifyHandlersForPlayer(player.getUniqueID());
            } catch (RuntimeException failure) {
                if (warned.add(id)) moze_intel.projecte.utils.PELogger.logWarn("Durable knowledge task will retry: " + id);
                continue;
            }
            cancelKnowledge(id); warned.remove(id);
        }
    }
    /** Audit before mutating players; external AE item transfer cannot be proven from player save alone. */
    public String beginBalances(List<EntityPlayer> players, List<ExactEMC> before, List<ExactEMC> after) {
        for (String id : keys(data.getCompoundTag("Audits"))) {
            NBTTagCompound a = data.getCompoundTag("Audits").getCompoundTag(id);
            if (!a.getBoolean("Review")) continue;
            for (EntityPlayer player : players) if (a.getCompoundTag("Owners").hasKey(player.getUniqueID().toString()))
                throw new IllegalStateException("Player EMC transfer audit requires operator review: " + id);
        }
        if (keys(data.getCompoundTag("Audits")).size() >= MAX_RECORDS) throw new IllegalStateException("Audit ledger full");
        NBTTagCompound next = copy(data), a = new NBTTagCompound(), owners = new NBTTagCompound();
        for (int i = 0; i < players.size(); i++) {
            NBTTagCompound pair = new NBTTagCompound(); pair.setString("Before", before.get(i).toString());
            pair.setString("After", after.get(i).toString()); owners.setTag(players.get(i).getUniqueID().toString(), pair);
        }
        a.setTag("Owners", owners); a.setString("State", "PREPARED");
        String id = UUID.randomUUID().toString(); next.getCompoundTag("Audits").setTag(id, a); persist(next); return id;
    }
    public void finishBalances(String id, String state) {
        NBTTagCompound next = copy(data), a = next.getCompoundTag("Audits").getCompoundTag(id);
        a.setString("State", state); a.setBoolean("Review", !state.equals("COMMITTED")); persist(next);
    }
    public List<String> audits() {
        List<String> lines = new ArrayList<>();
        for (String id : keys(data.getCompoundTag("Audits"))) {
            NBTTagCompound a = data.getCompoundTag("Audits").getCompoundTag(id);
            lines.add(id + " " + a.getString("State") + " review=" + a.getBoolean("Review") + " " + a.getCompoundTag("Owners"));
        }
        return lines;
    }
    public void acknowledge(String id) {
        if (!data.getCompoundTag("Audits").hasKey(id, 10)) throw new IllegalArgumentException("Unknown audit");
        NBTTagCompound next = copy(data); next.getCompoundTag("Audits").removeTag(id); persist(next);
    }
    public List<String> knowledgeTasks() {
        List<String> lines = new ArrayList<>();
        for (String id : keys(data.getCompoundTag("Knowledge"))) lines.add(id + " " + data.getCompoundTag("Knowledge").getCompoundTag(id));
        return lines;
    }
    public void reviewKnowledge(String id, boolean approve) {
        if (!data.getCompoundTag("Knowledge").hasKey(id, 10)) throw new IllegalArgumentException("Unknown knowledge intent");
        if (approve) commitKnowledge(id); else cancelKnowledge(id);
    }
    /** Legacy payload has no authenticity. Operator must inspect world backup FIRST. No automatic acceptance. */
    public ItemStack importLegacy(UUID owner, ItemStack old) {
        if (old == null || old.stackSize != 1 || old.getItem() != AE2Integration.itemEMCRecoveryBundle
                || !old.hasTagCompound() || old.getTagCompound().getInteger("RecoveryVersion") != 1
                || !owner.toString().equals(old.getTagCompound().getString("OwnerUUID")))
            throw new IllegalArgumentException("Not an owner-matching legacy v1 bundle");
        NBTTagList items = old.getTagCompound().getTagList("AppliedEOutputsV1", 10); validateItems(items, false);
        if (keys(data.getCompoundTag("Receipts")).size() >= MAX_RECORDS) throw new IllegalStateException("Receipt ledger full");
        NBTTagCompound next = copy(data), r = new NBTTagCompound(); String id = UUID.randomUUID().toString();
        r.setString("Owner", owner.toString()); r.setString("State", "READY"); r.setTag("Items", items.copy());
        r.setString("Source", "operator_approved_legacy_import"); next.getCompoundTag("Receipts").setTag(id, r);
        persist(next); return token(id, owner);
    }
    /** Audited balance wrapper. Postcommit IO failure stays PREPARED/COMMITTED with explicit uncertainty. */
    public static void commitBalances(List<EntityPlayer> players, List<ExactEMC> before, List<ExactEMC> after) {
        EMCRecoveryLedger ledger = get(); String id = ledger.beginBalances(players, before, after);
        try { Transmutation.commitEmcBalances(players, before, after); }
        catch (RuntimeException failure) { ledger.finishBalances(id, "FAILED_REVIEW"); throw failure; }
        ledger.finishBalances(id, "COMMITTED");
    }
}
