package com.sokoul.iptv.ui.components

import com.sokoul.iptv.ui.theme.Space
import com.sokoul.iptv.ui.theme.LocalLayout
import com.sokoul.iptv.ui.theme.SokoulColors
import com.sokoul.iptv.ui.theme.Radius
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class CategoryItem(
    val id: String, val name: String, val locked: Boolean = false,
    /** Nombre d'éléments du groupe, quand on le connaît (affiché à droite). */
    val count: Int? = null,
    /** Icône des groupes spéciaux (Favoris, Récents, Tout). */
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
)

/** Colonne de groupes (TV / tablette). Un clic sélectionne, l'appui long ouvre les options. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CategoryColumn(
    items: List<CategoryItem>,
    selected: String,
    onSelect: (CategoryItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((CategoryItem) -> Unit)? = null,
) {
    val state = rememberLazyListState()
    LaunchedEffect(items.isNotEmpty()) {
        val i = items.indexOfFirst { it.id == selected }
        if (i > 0) state.scrollToItem((i - 3).coerceAtLeast(0))
    }
    LazyColumn(
        modifier.focusRestorer(),
        state = state,
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items, key = { it.id }) { c ->
            val isSel = c.id == selected
            FocusSurface(style = FocusStyle.Row,
                onClick = { onSelect(c) },
                onLongClick = onLongClick?.let { { it(c) } },
                modifier = Modifier.fillMaxWidth(),
                color = if (isSel) SokoulColors.AccentTint else Color.Transparent,
                shape = Radius.Card,
            ) { _ ->
                Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    c.icon?.let { Icon(it, null, Modifier.padding(end = 10.dp).size(18.dp), tint = if (isSel) SokoulColors.Accent else SokoulColors.TextSecondary) }
                    Text(
                        c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSel) SokoulColors.Accent else SokoulColors.TextPrimary,
                    )
                    if (c.locked) Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = SokoulColors.TextMuted)
                    else c.count?.let { Text(it.toString(), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = SokoulColors.TextMuted) }
                }
            }
        }
    }
}

/** Bouton « groupe » + liste verticale en fenêtre : utilisable même avec des centaines de groupes. */
@Composable
fun CategoryPickerButton(
    items: List<CategoryItem>,
    selected: String,
    onSelect: (CategoryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val current = items.firstOrNull { it.id == selected }?.name ?: "Groupe"
    SokoulButton(current.take(28), { open = true }, modifier, icon = Icons.AutoMirrored.Filled.List)
    if (open) {
        SokoulDialog({ open = false }, DialogPlacement.Side) {
            val focus = remember { FocusRequester() }
            val start = items.indexOfFirst { it.id == selected }.coerceAtLeast(0)
            val state = rememberLazyListState((start - 4).coerceAtLeast(0))
            run {
                DialogHeader("Groupes", "${items.size} groupes")
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 720.dp), state = state, contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(items, key = { _, c -> c.id }) { i, c ->
                        val isSel = c.id == selected
                        FocusSurface(style = FocusStyle.Row,
                            onClick = { open = false; onSelect(c) },
                            modifier = Modifier.fillMaxWidth().then(if (i == start) Modifier.focusRequester(focus) else Modifier),
                            color = if (isSel) SokoulColors.AccentTint else Color.Transparent,
                        ) { _ ->
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isSel) SokoulColors.Accent else SokoulColors.TextPrimary)
                                if (c.locked) Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = SokoulColors.TextMuted)
                            }
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        }
    }
}

/** Puces horizontales (téléphone). */
@Composable
fun CategoryChips(
    items: List<CategoryItem>,
    selected: String,
    onSelect: (CategoryItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((CategoryItem) -> Unit)? = null,
) {
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = LocalLayout.current.margin, vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        items(items, key = { it.id }) { c ->
            val isSel = c.id == selected
            SokoulChip(
                c.name, { onSelect(c) }, icon = if (c.locked) Icons.Default.Lock else null, selected = isSel,
                onLongClick = onLongClick?.let { { it(c) } },
            )
        }
    }
}
