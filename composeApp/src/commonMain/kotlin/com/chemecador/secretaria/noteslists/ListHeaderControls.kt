package com.chemecador.secretaria.noteslists

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.notes_lists_search_clear
import secretaria.composeapp.generated.resources.order_by
import secretaria.composeapp.generated.resources.sort_date_asc
import secretaria.composeapp.generated.resources.sort_date_desc
import secretaria.composeapp.generated.resources.sort_name_asc
import secretaria.composeapp.generated.resources.sort_name_desc

/**
 * Header controls shared by the lists and the notes screens. They live next to [SortOption] rather
 * than in either screen because both use exactly the same sort menu and search field.
 */

private val SORT_MENU_OPTIONS = listOf(
    SortOption.NAME_ASC,
    SortOption.NAME_DESC,
    SortOption.DATE_ASC,
    SortOption.DATE_DESC,
)

/** The current option is only visible in the menu now that the header shows an icon, not a label. */
@Composable
internal fun SortMenuButton(
    selected: SortOption,
    onSortSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(Res.string.order_by),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SORT_MENU_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    trailingIcon = {
                        if (option == selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        onSortSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Always on screen: hiding it behind the magnifier made it easy to miss. It never grabs focus on
 * its own, so opening a screen does not pop the keyboard.
 */
@Composable
internal fun PersistentSearchField(
    query: String,
    hint: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )

        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.notes_lists_search_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun SortOption.label(): String =
    when (this) {
        SortOption.NAME_ASC -> stringResource(Res.string.sort_name_asc)
        SortOption.NAME_DESC -> stringResource(Res.string.sort_name_desc)
        SortOption.DATE_ASC -> stringResource(Res.string.sort_date_asc)
        SortOption.DATE_DESC,
        SortOption.CUSTOM,
        -> stringResource(Res.string.sort_date_desc)
    }
