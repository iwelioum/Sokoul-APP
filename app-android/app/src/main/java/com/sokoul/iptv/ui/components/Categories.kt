package com.sokoul.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.data.db.CategoryWithPrefs
import com.sokoul.iptv.data.repo.SpecialCategory
import com.sokoul.iptv.ui.theme.SokoulColors

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
            FocusSurface(
                onClick = { onSelect(c) },
                onLongClick = onLongClick?.let { { it(c) } },
                modifier = Modifier.fillMaxWidth(),
                color = if (isSel) SokoulColors.AccentSoft else Color.Transparent,
                focusedColor = SokoulColors.SurfaceHigher,
                shape = RoundedCornerShape(10.dp),
                focusScale = 1.02f,
                borderWidth = 2.dp,
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

/** Puces horizontales (téléphone). */
@Composable
fun CategoryChips(
    items: List<CategoryItem>,
    selected: String,
    onSelect: (CategoryItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((CategoryItem) -> Unit)? = null,
) {
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { c ->
            val isSel = c.id == selected
            FocusSurface(
                onClick = { onSelect(c) },
                onLongClick = onLongClick?.let { { it(c) } },
                color = if (isSel) SokoulColors.Accent else SokoulColors.SurfaceHigh,
                focusedColor = if (isSel) SokoulColors.Accent else SokoulColors.SurfaceHigher,
                shape = RoundedCornerShape(50),
                focusScale = 1.04f,
            ) { _ ->
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (c.locked) Icon(Icons.Default.Lock, null, Modifier.size(13.dp).padding(end = 2.dp), tint = Color.White)
                    Text(c.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = Color.White)
                }
            }
        }
    }
}
