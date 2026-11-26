package app.hissa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** One colour per category so the chart, legend and expense icons agree. */
private fun catColor(c: Cat): Color = listOf(
    0xFF7A8699, 0xFFE9692C, 0xFF12A18A, 0xFF2F80ED, 0xFFB8860B, 0xFF7C5CFF, 0xFFD6409F, 0xFFE5484D, 0xFF0EA5C6, 0xFF8A6D3B, 0xFF3D9970, 0xFFDC3545, 0xFFF59E0B,
)[c.ordinal % 13].let(::Color)

@Composable
private fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(8.dp)).background(color))
    }
}

val insightRanges = listOf("All time", "This month", "3 months")

/** 12400 cents -> "124", 1250000 -> "12.5k": short labels above chart bars. */
private fun compact(cents: Long): String {
    val v = cents / 100.0
    return when {
        v >= 100000 -> "%.1fL".format(v / 100000)
        v >= 1000 -> "%.1fk".format(v / 1000)
        else -> "%.0f".format(v)
    }
}

/** Spending charts for one group: totals, categories, trend, weekdays, people and the biggest expenses. */
fun LazyListScope.insightItems(
    g: Group, d: GroupData, model: HomeModel, me: String, range: Int, onRange: (Int) -> Unit, onExport: () -> Unit,
) {
    val all = d.expenses.filter { !it.isSettlement }
    if (all.isEmpty()) {
        item { EmptyState("Nothing to chart yet", "Add a few expenses and the charts appear here.") }
        return
    }
    val today = LocalDate.now()
    val from = when (range) { 1 -> today.withDayOfMonth(1); 2 -> today.minusMonths(3); else -> LocalDate.MIN }
    val spent = all.filter { runCatching { LocalDate.parse(it.date) >= from }.getOrDefault(true) }

    item { Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { Segmented(insightRanges, range, onRange) } }
    if (spent.isEmpty()) { item { EmptyState("Nothing in this period", "Try All time to see every expense.") }; return }

    val total = spent.sumOf { it.amountCents }
    val myShare = spent.sumOf { it.shareOf(me) }
    val myPaid = spent.sumOf { it.paidBy(me) }

    item {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val biggest = spent.maxBy { it.amountCents }
            val days = (java.time.temporal.ChronoUnit.DAYS.between(spent.minOf { LocalDate.parse(it.date) }, spent.maxOf { LocalDate.parse(it.date) }) + 1).coerceAtLeast(1)
            listOf(
                listOf("Group total" to money(total, g.currency), "Your share" to money(myShare, g.currency), "You paid" to money(myPaid, g.currency)),
                listOf("Expenses" to spent.size.toString(), "Per day" to money(total / days, g.currency), "Biggest" to money(biggest.amountCents, g.currency)),
            ).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { (label, v) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(v, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    // ── Where the money went: a donut and its legend. ──
    val byCat = spent.groupBy { Cat.of(it.category) }.mapValues { (_, l) -> l.sumOf { it.amountCents } }.entries.sortedByDescending { it.value }
    item { SectionTitle("Where it went") }
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            val cs = MaterialTheme.colorScheme
            val desc = byCat.joinToString(", ") { "${it.key.label} ${it.value * 100 / total} percent" }
            Box(Modifier.size(150.dp).semantics { contentDescription = "Spending by category: $desc" }, Alignment.Center) {
                Canvas(Modifier.size(150.dp)) {
                    val stroke = 26.dp.toPx()
                    val inset = stroke / 2
                    var start = -90f
                    byCat.forEach { (c, v) ->
                        val sweep = 360f * v / total
                        drawArc(
                            catColor(c), start + 1f, (sweep - 2f).coerceAtLeast(0.5f), false,
                            topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke),
                        )
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(compact(total), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                byCat.take(5).forEach { (c, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(catColor(c)))
                        Spacer(Modifier.width(8.dp))
                        Text(c.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${v * 100 / total}%", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
    item { Spacer(Modifier.height(12.dp)) }
    items(byCat, key = { "c${it.key.id}" }) { (c, v) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(catColor(c).copy(alpha = 0.16f)), Alignment.Center) { Icon(c.icon, null, Modifier.size(20.dp), tint = catColor(c)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(c.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(money(v, g.currency), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(6.dp))
                Bar(v.toFloat() / byCat.first().value, catColor(c), Modifier.fillMaxWidth().height(8.dp))
            }
        }
    }

    // ── Over time: running total as a line. ──
    val perDay = spent.groupBy { LocalDate.parse(it.date) }.mapValues { (_, l) -> l.sumOf { it.amountCents } }.toSortedMap()
    if (perDay.size >= 2) {
        item { SectionTitle("Spend per day") }
        item {
            val cs = MaterialTheme.colorScheme
            val last = perDay.lastKey()
            val days = (13 downTo 0).map { last.minusDays(it.toLong()) }.filter { it >= perDay.firstKey() }
            val peakDay = days.maxBy { perDay[it] ?: 0L }
            val peak = (perDay[peakDay] ?: 1L).coerceAtLeast(1L)
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    days.forEach { d ->
                        val v = perDay[d] ?: 0L
                        Box(
                            Modifier.weight(1f).height((100f * v / peak).coerceAtLeast(3f).dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (d == peakDay) cs.primary else cs.primary.copy(alpha = 0.35f)),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(dayLabel(days.first().toString()), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Text("Peak ${dayLabel(peakDay.toString())}: ${money(peak, g.currency)}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }

    // ── By month. ──
    val months = spent.groupBy { YearMonth.from(LocalDate.parse(it.date)) }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
    if (months.size >= 2) {
        item { SectionTitle("By month") }
        val last = months.keys.sorted().takeLast(6)
        val peak = last.maxOf { months.getValue(it) }.coerceAtLeast(1L)
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                last.forEach { ym ->
                    val v = months.getValue(ym)
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(compact(v), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.width(36.dp).height((96f * v / peak).coerceAtLeast(4f).dp)
                                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).background(MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(ym.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // ── Which days of the week cost the most. ──
    val byDow = spent.groupBy { LocalDate.parse(it.date).dayOfWeek }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
    if (spent.size >= 3) {
        item { SectionTitle("By day of the week") }
        item {
            val peak = byDow.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                DayOfWeek.entries.forEach { dow ->
                    val v = byDow[dow] ?: 0L
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(
                            Modifier.fillMaxWidth().height((80f * v / peak).coerceAtLeast(3f).dp).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(if (v == peak && v > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(dow.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // ── People: what each paid against their own share. ──
    item { SectionTitle("Paid vs share") }
    val people = d.members.map { m -> Triple(m, spent.sumOf { it.paidBy(m) }, spent.sumOf { it.shareOf(m) }) }.sortedByDescending { it.second }
    val top = people.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1L)
    items(people, key = { "p${it.first}" }) { (m, paid, share) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(model.fullName(m), m, 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(model.name(m), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Bar(paid.toFloat() / top, MaterialTheme.colorScheme.primary, Modifier.weight(1f).height(8.dp))
                    Text("paid ${money(paid, g.currency)}", Modifier.width(110.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Bar(share.toFloat() / top, MaterialTheme.colorScheme.outline, Modifier.weight(1f).height(8.dp))
                    Text("share ${money(share, g.currency)}", Modifier.width(110.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }

    // ── Biggest expenses. ──
    item { SectionTitle("Biggest expenses") }
    items(spent.sortedByDescending { it.amountCents }.take(5), key = { "b${it.id}" }) { e ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer), Alignment.Center) { Icon(Cat.of(e.category).icon, null, Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.description, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dayLabel(e.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(money(e.amountCents, g.currency), style = MaterialTheme.typography.titleSmall)
        }
    }

    item {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.padding(horizontal = 20.dp)) { SecondaryButton("Export as CSV", onExport) }
    }
}
