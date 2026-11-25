package app.hissa

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable data class Profile(val id: String, val name: String = "", val email: String? = null, @SerialName("upi_id") val upiId: String? = null)
@Serializable data class Group(
    val id: String, val name: String, val currency: String = "INR",
    @SerialName("invite_code") val inviteCode: String = "",
    /** Small JPEG as a base64 data URL, or null. */
    val image: String? = null,
    @SerialName("created_by") val createdBy: String = "",
)
@Serializable data class MemberRow(@SerialName("group_id") val groupId: String, @SerialName("user_id") val userId: String)
@Serializable data class SplitRow(@SerialName("user_id") val userId: String, @SerialName("share_cents") val shareCents: Long)
@Serializable data class PayerRow(@SerialName("user_id") val userId: String, @SerialName("paid_cents") val paidCents: Long)
@Serializable data class Expense(
    val id: String, val description: String,
    @SerialName("group_id") val groupId: String = "",
    @SerialName("amount_cents") val amountCents: Long,
    /** The biggest payer (kept for older rows and quick labels); see [payers] for everyone who paid. */
    @SerialName("paid_by") val paidBy: String,
    @SerialName("expense_date") val date: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("is_settlement") val isSettlement: Boolean = false,
    val category: String? = null,
    val notes: String? = null,
    @SerialName("expense_splits") val splits: List<SplitRow> = emptyList(),
    @SerialName("expense_payers") val payerRows: List<PayerRow> = emptyList(),
) {
    /** Everyone who paid, and how much. Falls back to the single payer on older rows. */
    val payers: List<PayerRow> get() = payerRows.ifEmpty { listOf(PayerRow(paidBy, amountCents)) }
    fun paidBy(uid: String): Long = payers.firstOrNull { it.userId == uid }?.paidCents ?: 0L
    fun shareOf(uid: String): Long = splits.firstOrNull { it.userId == uid }?.shareCents ?: 0L

    /** What [uid] gained (+) or lost (-) on this expense: paid minus own share. */
    fun netFor(uid: String): Long = paidBy(uid) - shareOf(uid)

    /** "Asha", "Asha and Ravi" or "3 people" for the line under an expense. */
    fun payerNames(name: (String) -> String): String = payers.sortedByDescending { it.paidCents }.let { p ->
        when (p.size) { 1 -> name(p[0].userId); 2 -> "${name(p[0].userId)} and ${name(p[1].userId)}"; else -> "${p.size} people" }
    }
}
@Serializable data class Balance(
    @SerialName("user_id") val userId: String,
    @SerialName("net_cents") val netCents: Long,
)

@Serializable data class HomeData(
    val groups: List<Group>,
    val members: Map<String, List<String>>,
    val names: Map<String, String>,
    val myNet: Map<String, Long>,
    val recent: List<Expense>,
    /** UPI ids people saved in their profile, for the "Pay with UPI" button. */
    val upis: Map<String, String> = emptyMap(),
    /** True when this is the copy saved on the phone because the network was unreachable. */
    val offline: Boolean = false,
)

@Serializable data class RecurringRow(
    val id: String, val description: String,
    @SerialName("amount_cents") val amountCents: Long,
    val every: String,
    @SerialName("next_date") val nextDate: String,
)

data class GroupData(val members: List<String>, val expenses: List<Expense>, val net: Map<String, Long>)

object Api {
    /** Null until a Supabase URL + anon key are known (baked in at build time, or typed in the app). */
    var client by mutableStateOf<SupabaseClient?>(null)
        private set
    var serverUrl by mutableStateOf("")
        private set
    val sb: SupabaseClient get() = client!!
    private lateinit var prefs: SharedPreferences

    /** Debug-only sample-data mode (see [Demo]); never true in a normal launch. */
    var demo = false
    val me: String? get() = if (demo) Demo.ME else client?.auth?.currentUserOrNull()?.id
    val email: String get() = if (demo) "aarav@example.com" else client?.auth?.currentUserOrNull()?.email.orEmpty()

    /** 0 follow the phone, 1 light, 2 dark. */
    var themeMode by mutableStateOf(0)
        private set
    fun setTheme(mode: Int) { themeMode = mode; prefs.edit().putInt("theme", mode).apply() }

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("hissa", Context.MODE_PRIVATE)
        themeMode = prefs.getInt("theme", 0)
        val url = prefs.getString("url", null) ?: BuildConfig.SUPABASE_URL
        val key = prefs.getString("key", null) ?: BuildConfig.SUPABASE_KEY
        if (url.startsWith("https://") && key.length > 20) connect(url, key)
    }

    fun configure(url: String, key: String) {
        val u = url.trim().trimEnd('/')
        val k = key.trim()
        require(u.startsWith("https://")) { "The URL must start with https://" }
        require(k.length > 20) { "That key looks too short" }
        prefs.edit().putString("url", u).putString("key", k).apply()
        connect(u, k)
    }

    private fun connect(url: String, key: String) {
        serverUrl = url
        client = createSupabaseClient(url, key) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    // ----- auth -----
    suspend fun signIn(e: String, p: String) { sb.auth.signInWith(Email) { email = e.trim(); password = p } }

    /** Returns true if signed in straight away, false if the project wants email confirmation first. */
    suspend fun signUp(n: String, e: String, p: String): Boolean {
        val created = sb.auth.signUpWith(Email) {
            email = e.trim(); password = p
            data = buildJsonObject { put("name", n.trim()) }
        }
        if (created?.identities?.isEmpty() == true) throw Exception("already registered")
        return sb.auth.currentSessionOrNull() != null
    }

    /** Finishes sign-up with the code from the "Confirm sign up" email (this signs the user in). */
    suspend fun confirmSignup(email: String, code: String) {
        sb.auth.verifyEmailOtp(OtpType.Email.SIGNUP, email.trim(), code.trim())
    }
    suspend fun resendSignupCode(email: String) = sb.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())

    /** Emails a recovery code (needs the Supabase "Reset Password" email template to include {{ .Token }}). */
    suspend fun sendResetCode(email: String) = sb.auth.resetPasswordForEmail(email.trim())

    /** Checks the code (this signs the user in) and then sets the new password. */
    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        sb.auth.verifyEmailOtp(OtpType.Email.RECOVERY, email.trim(), code.trim())
        // Signing in flips the screen; finish the update even if this screen has already left.
        withContext(NonCancellable) { sb.auth.updateUser { password = newPassword } }
    }

    suspend fun changePassword(newPassword: String) { if (!demo) sb.auth.updateUser { password = newPassword } }
    suspend fun signOut() { if (!demo) sb.auth.signOut() }

    // ----- reads -----
    suspend fun groups() = sb.from("groups").select { order("created_at", Order.DESCENDING) }.decodeList<Group>()
    suspend fun profiles() = sb.from("profiles").select().decodeList<Profile>()
    private suspend fun allMembers() = sb.from("group_members").select().decodeList<MemberRow>()
    private val withSplits = Columns.raw("*, expense_splits(user_id, share_cents), expense_payers(user_id, paid_cents)")

    private suspend fun recentExpenses() = sb.from("expenses").select(withSplits) {
        order("created_at", Order.DESCENDING)
        limit(1000)
    }.decodeList<Expense>()

    private suspend fun groupExpenses(gid: String) = sb.from("expenses").select(withSplits) {
        filter { eq("group_id", gid) }
        order("expense_date", Order.DESCENDING)
        order("created_at", Order.DESCENDING)
    }.decodeList<Expense>()

    suspend fun balances(gid: String) =
        sb.postgrest.rpc("group_balances", buildJsonObject { put("p_group_id", gid) }).decodeList<Balance>()

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun cacheKey() = "home_cache_" + (me ?: "")

    suspend fun loadHome(): HomeData = if (demo) Demo.home() else try {
        coroutineScope {
            val groups = groups()
            val profiles = async { profiles() }
            val members = async { allMembers().groupBy({ it.groupId }, { it.userId }) }
            val recent = async { recentExpenses() }
            val nets = groups.map { g ->
                async { g.id to (balances(g.id).firstOrNull { it.userId == me }?.netCents ?: 0L) }
            }.awaitAll().toMap()
            val ps = profiles.await()
            HomeData(
                groups, members.await(),
                ps.associate { it.id to it.name.ifBlank { "Friend" } },
                nets, recent.await(),
                ps.mapNotNull { p -> p.upiId?.takeIf { it.isNotBlank() }?.let { p.id to it } }.toMap(),
            ).also { runCatching { prefs.edit().putString(cacheKey(), json.encodeToString(HomeData.serializer(), it)).apply() } }
        }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (e: Exception) {
        // No network: show the copy saved on this phone, read-only.
        val saved = prefs.getString(cacheKey(), null)?.let { runCatching { json.decodeFromString(HomeData.serializer(), it) }.getOrNull() }
        saved?.copy(offline = true) ?: throw e
    }

    suspend fun loadGroup(gid: String): GroupData = if (demo) Demo.group(gid) else try {
        coroutineScope {
            val members = async { allMembers().filter { it.groupId == gid }.map { it.userId } }
            val expenses = async { groupExpenses(gid) }
            val net = async { balances(gid).associate { it.userId to it.netCents } }
            GroupData(members.await(), expenses.await(), net.await())
        }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (e: Exception) {
        val saved = prefs.getString(cacheKey(), null)?.let { runCatching { json.decodeFromString(HomeData.serializer(), it) }.getOrNull() } ?: throw e
        val list = saved.recent.filter { it.groupId == gid }.sortedByDescending { it.date }
        val ms = saved.members[gid].orEmpty()
        GroupData(ms, list, ms.associateWith { u -> list.sumOf { it.netFor(u) } })
    }

    // ----- realtime -----
    /** Channel + a flow that emits whenever an expense, split or member row changes anywhere visible to this user. */
    fun liveChannel(name: String): Pair<RealtimeChannel, Flow<Unit>> {
        val ch = sb.channel(name)
        val flows = listOf("expenses", "expense_splits", "expense_payers", "group_members", "groups").map { t ->
            ch.postgresChangeFlow<PostgresAction>(schema = "public") { table = t }.map { }
        }
        return ch to merge(*flows.toTypedArray())
    }
    suspend fun closeChannel(ch: RealtimeChannel) = sb.realtime.removeChannel(ch)

    // ----- writes -----
    suspend fun deleteExpense(id: String) { if (demo) return Demo.deleteExpense(id); sb.from("expenses").delete { filter { eq("id", id) } } }

    suspend fun createGroup(name: String, currency: String = "INR") {
        if (demo) return Demo.createGroup(name.trim(), currency)
        sb.postgrest.rpc("create_group", buildJsonObject { put("p_name", name.trim()); put("p_currency", currency) })
    }
    suspend fun updateGroup(gid: String, name: String, image: String?) {
        if (demo) return Demo.updateGroup(gid, name.trim(), image)
        sb.postgrest.rpc("update_group", buildJsonObject { put("p_group_id", gid); put("p_name", name.trim()); put("p_image", image ?: "") })
    }
    suspend fun leaveGroup(gid: String) {
        if (demo) return Demo.leaveGroup(gid)
        sb.postgrest.rpc("leave_group", buildJsonObject { put("p_group_id", gid) })
    }
    suspend fun deleteGroup(gid: String) {
        if (demo) return Demo.deleteGroup(gid)
        sb.postgrest.rpc("delete_group", buildJsonObject { put("p_group_id", gid) })
    }

    /** "Simplify group debts" per group, kept on this phone. */
    private val simplifyState = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    fun simplifyDebts(gid: String): Boolean = simplifyState.getOrPut(gid) { prefs.getBoolean("simplify_$gid", true) }
    fun setSimplifyDebts(gid: String, on: Boolean) { simplifyState[gid] = on; prefs.edit().putBoolean("simplify_$gid", on).apply() }

    /** "Use this split by default" per group, kept on this phone: MODE|id,id|id:value,id:value. */
    fun splitDefault(gid: String): String? = prefs.getString("split_$gid", null)
    fun setSplitDefault(gid: String, v: String?) { prefs.edit().apply { if (v == null) remove("split_$gid") else putString("split_$gid", v) }.apply() }

    /** Invite code from a tapped link, waiting for the signed-in home screen to offer joining. */
    var pendingInvite by mutableStateOf<String?>(null)

    /** The link people tap to join: your own https page if you set INVITE_BASE, else the app's own hissa:// link. */
    fun inviteLink(code: String) = (BuildConfig.INVITE_BASE.ifBlank { "hissa://join/" }.let { if (it.endsWith("/") || it.endsWith("=")) it else "$it/" }) + code

    /** Pulls the code out of hissa://join/CODE, https://host/join/CODE or ...?code=CODE. */
    fun codeFromLink(uri: android.net.Uri?): String? =
        uri?.getQueryParameter("code") ?: uri?.pathSegments?.lastOrNull()?.takeIf { it != "join" && it.length >= 6 }

    fun extractCode(text: String) = extractInviteCode(text)

    suspend fun joinGroup(code: String) {
        if (demo) return Demo.joinGroup(code.trim())
        sb.postgrest.rpc("join_group", buildJsonObject { put("p_code", code.trim()) })
    }

    private fun rows(m: Map<String, Long>, key: String): JsonArray = buildJsonArray {
        m.forEach { (u, c) -> if (c > 0) add(buildJsonObject { put("user_id", u); put(key, c) }) }
    }

    /** Everything the add / edit screen collects. [payers] and [shares] both add up to [cents]. */
    data class ExpenseInput(
        val desc: String, val cents: Long, val payers: Map<String, Long>, val shares: Map<String, Long>,
        val date: java.time.LocalDate = java.time.LocalDate.now(), val category: String? = null, val notes: String? = null,
    )

    suspend fun addExpense(gid: String, x: ExpenseInput, settlement: Boolean = false) {
        if (demo) return Demo.addExpense(gid, x, settlement)
        sb.postgrest.rpc("add_expense2", buildJsonObject {
            put("p_group_id", gid); put("p_description", x.desc); put("p_amount_cents", x.cents)
            put("p_payers", rows(x.payers, "paid_cents")); put("p_splits", rows(x.shares, "share_cents"))
            put("p_expense_date", x.date.toString()); put("p_is_settlement", settlement)
            put("p_category", x.category ?: ""); put("p_notes", x.notes ?: "")
        })
    }

    /** A payment of [cents] from [from] to [to]. */
    suspend fun settle(gid: String, from: String, to: String, cents: Long) =
        addExpense(gid, ExpenseInput("Settle up", cents, mapOf(from to cents), mapOf(to to cents)), true)

    suspend fun editExpense(id: String, x: ExpenseInput) {
        if (demo) return Demo.editExpense(id, x)
        sb.postgrest.rpc("edit_expense", buildJsonObject {
            put("p_expense_id", id); put("p_description", x.desc); put("p_amount_cents", x.cents)
            put("p_payers", rows(x.payers, "paid_cents")); put("p_splits", rows(x.shares, "share_cents"))
            put("p_expense_date", x.date.toString()); put("p_category", x.category ?: ""); put("p_notes", x.notes ?: "")
        })
    }

    // ----- recurring expenses -----
    suspend fun addRecurring(gid: String, x: ExpenseInput, every: String, next: java.time.LocalDate) {
        if (demo) return Demo.addRecurring(gid, x, every, next)
        sb.postgrest.rpc("add_recurring", buildJsonObject {
            put("p_group_id", gid); put("p_description", x.desc); put("p_amount_cents", x.cents)
            put("p_payers", rows(x.payers, "paid_cents")); put("p_splits", rows(x.shares, "share_cents"))
            put("p_category", x.category ?: ""); put("p_every", every); put("p_next_date", next.toString())
        })
    }
    suspend fun recurring(gid: String): List<RecurringRow> =
        if (demo) Demo.recurring(gid) else sb.from("recurring_expenses").select { filter { eq("group_id", gid) }; order("next_date", Order.ASCENDING) }.decodeList<RecurringRow>()
    suspend fun deleteRecurring(id: String) { if (demo) Demo.deleteRecurring(id) else sb.postgrest.rpc("delete_recurring", buildJsonObject { put("p_id", id) }) }
    /** Creates any repeats that came due; returns how many were made. Quietly does nothing on older servers. */
    suspend fun runRecurring(gid: String): Int = if (demo) 0 else runCatching {
        sb.postgrest.rpc("run_recurring", buildJsonObject { put("p_group_id", gid) }).data.trim().toIntOrNull() ?: 0
    }.getOrDefault(0)

    // ----- profile -----
    suspend fun setUpi(upi: String) {
        if (demo) return
        val id = me ?: return
        sb.from("profiles").update({ set("upi_id", upi.trim().ifEmpty { null }) }) { filter { eq("id", id) } }
    }
    suspend fun myUpi(): String = if (demo) "aarav@upi" else me?.let { id ->
        sb.from("profiles").select { filter { eq("id", id) } }.decodeList<Profile>().firstOrNull()?.upiId.orEmpty()
    }.orEmpty()
}
