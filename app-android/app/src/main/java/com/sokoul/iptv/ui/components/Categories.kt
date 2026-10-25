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
import androidx.compose.material.icons.filled.Lock
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
import com.sokoul.iptv.data.db.CategoryWithPrefs
import com.sokoul.iptv.data.repo.SpecialCategory

data class CategoryItem(val id: String, val name: String, val locked: Boolean = false)

fun buildCategoryItems(categories: List<CategoryWithPrefs>, withRecent: Boolean = true): List<CategoryItem> = buildList {
    add(CategoryItem(SpecialCategory.FAVORITES, "★ Favoris"))
    if (withRecent) add(CategoryItem(SpecialCategory.RECENT, "Récents"))
    add(CategoryItem(SpecialCategory.ALL, "Tout"))
    categories.filter { !it.hidden }.forEach { add(CategoryItem(it.categoryId, it.name, it.locked)) }
}

/** Colonne de groupes (TV / tablette). Un clic sélectionne, l'appui long ouvre les options. */
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
                    Text(
                        c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSel) SokoulColors.Accent else SokoulColors.TextPrimary,
                    )
                    if (c.locked) Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = SokoulColors.TextMuted)
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
    AppButton(current.take(28), { open = true }, modifier, icon = Icons.AutoMirrored.Filled.List)
    if (open) {
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val focus = remember { FocusRequester() }
            val start = items.indexOfFirst { it.id == selected }.coerceAtLeast(0)
            val state = rememberLazyListState((start - 4).coerceAtLeast(0))
            Column(Modifier.widthIn(min = 360.dp, max = 560.dp).clip(Radius.Panel).background(SokoulColors.SurfaceHigh).padding(vertical = 16.dp)) {
                Text("Groupes (${items.size})", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.heightIn(max = 480.dp), state = state, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
