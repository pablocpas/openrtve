package es.openrtve.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.openrtve.R
import es.openrtve.data.Favorites
import es.openrtve.domain.CatalogItem
import es.openrtve.ui.RootReselectionEffect

/**
 * Pestaña "Mi lista": lo guardado con "+" desde las fichas, lo último primero.
 * Al tocar se abre la ficha; el menú de cada tarjeta lo quita (con deshacer).
 */
@Composable
fun MyListScreen(
    favorites: Favorites,
    onOpenItem: (CatalogItem) -> Unit,
    onRemove: (CatalogItem) -> Unit,
    reselectRequest: Int = 0,
) {
    val items by favorites.items.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    RootReselectionEffect(reselectRequest) { gridState.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Título grande dentro del contenido, como en Explorar.
        Text(
            text = stringResource(R.string.favorites_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 4.dp),
        )
        if (items.isEmpty()) {
            EmptyPanel(stringResource(R.string.favorites_empty))
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    MyListCard(item, onOpen = { onOpenItem(item) }, onRemove = { onRemove(item) }, Modifier.animateItem())
                }
            }
        }
    }
}

/** Como la tarjeta de "Seguir viendo": imagen y título abren la ficha; el "⋮" lleva el menú. */
@Composable
private fun MyListCard(item: CatalogItem, onOpen: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val open = stringResource(R.string.continue_open_details)
    Column(modifier) {
        Artwork(
            url = item.imageUrl,
            aspectRatio = 16f / 9f,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .clickable(onClickLabel = open, role = Role.Button, onClick = onOpen),
        )
        Row(verticalAlignment = Alignment.Top) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 6.dp)
                    .clickable(onClickLabel = open, onClick = onOpen),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MyListCardMenu(onRemove)
        }
    }
}

@Composable
private fun MyListCardMenu(onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.continue_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorites_remove)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = { expanded = false; onRemove() },
            )
        }
    }
}
