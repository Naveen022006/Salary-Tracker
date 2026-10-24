package com.local.salarytracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.data.CustomCategories
import com.local.salarytracker.data.CustomCategoryEntity
import com.local.salarytracker.model.Category
import com.local.salarytracker.ui.components.CategoryIcon
import com.local.salarytracker.ui.components.FieldShape
import com.local.salarytracker.ui.components.GroupedSection
import com.local.salarytracker.ui.components.Hairline
import com.local.salarytracker.ui.components.ScreenScaffold
import com.local.salarytracker.ui.components.appleFieldColors
import com.local.salarytracker.ui.components.appleSwitchColors
import com.local.salarytracker.ui.components.label
import com.local.salarytracker.ui.components.sheetCard
import com.local.salarytracker.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * Settings → Categories: add your own categories, remove them (their entries go back to Other), and hide built-in
 * ones you never use from the pickers. "Other" and "Not spending" always stay.
 */
@Composable
fun CategoriesPage(vm: AppViewModel) {
    val customs = vm.customCategories.collectAsStateWithLifecycle().value
    val hidden = vm.hiddenCategories.collectAsStateWithLifecycle().value
    var editing by remember { mutableStateOf<CustomCategoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler { vm.categoriesOpen.value = false }

    ScreenScaffold(
        title = "Categories",
        hasNavigation = true,
        showSettings = false,
        navigationIcon = {
            IconButton(onClick = { vm.categoriesOpen.value = false }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                GroupedSection(
                    header = "Your categories",
                    footer = "Tap one to change its look. Removing one moves its entries to Other.",
                ) {
                    customs.forEachIndexed { i, c ->
                        if (i > 0) Hairline(start = 60.dp)
                        CategoryRow(
                            icon = { CategoryIcon(Category.OTHER, 32.dp, custom = c.name) },
                            title = c.name,
                            onClick = { editing = c },
                            trailing = {
                                IconButton(onClick = { scope.launch { removing = c.name to vm.countInCustomCategory(c.name) } }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Remove ${c.name}", tint = AppColors.current.danger)
                                }
                            },
                        )
                    }
                    if (customs.isNotEmpty()) Hairline(start = 60.dp)
                    CategoryRow(
                        icon = {
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(AppColors.current.fill3), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = AppColors.current.accent)
                            }
                        },
                        title = "Add category",
                        titleColor = AppColors.current.accent,
                        onClick = { adding = true },
                    )
                }
            }
            item {
                GroupedSection(
                    header = "Built-in categories",
                    footer = "Turn off the ones you never use. They leave the pickers; old entries keep them. " +
                        "Other and Not spending always stay.",
                ) {
                    val all = Category.entries
                    all.forEachIndexed { i, c ->
                        if (i > 0) Hairline(start = 60.dp)
                        val fixed = c == Category.OTHER || c == Category.TRANSFER
                        val shown = c.name !in hidden
                        CategoryRow(
                            icon = { CategoryIcon(c, 32.dp) },
                            title = c.label,
                            onClick = if (fixed) null else { { vm.setCategoryHidden(c, shown) } },
                            trailing = {
                                if (fixed) {
                                    Text("Always", style = MaterialTheme.typography.bodyMedium, color = AppColors.current.secondaryLabel)
                                } else {
                                    Switch(
                                        checked = shown,
                                        onCheckedChange = { on -> vm.setCategoryHidden(c, !on) },
                                        colors = appleSwitchColors(),
                                        modifier = Modifier.semantics { contentDescription = "Show ${c.label}" },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        CategoryEditDialog(
            initialName = "",
            onDismiss = { adding = false },
            onSave = { n, e, col -> vm.saveCustomCategory(n, e, col).also { if (it) adding = false } },
        )
    }
    editing?.let { c ->
        CategoryEditDialog(
            initialName = c.name,
            initialEmoji = c.emoji,
            initialColor = c.color,
            nameLocked = true,
            onDismiss = { editing = null },
            onSave = { n, e, col -> vm.saveCustomCategory(n, e, col).also { if (it) editing = null } },
        )
    }
    removing?.let { (name, count) ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove $name?") },
            text = {
                Text(
                    if (count == 0) "No entries use it." else "$count ${if (count == 1) "entry moves" else "entries move"} to Other. No money is deleted.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteCustomCategory(name); removing = null }) {
                    Text("Remove", color = AppColors.current.danger)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
}

/** A 56dp row: 32dp icon, title, optional trailing control. The whole row is the tap target. */
@Composable
private fun CategoryRow(
    icon: @Composable () -> Unit,
    title: String,
    onClick: (() -> Unit)?,
    titleColor: Color = AppColors.current.label,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
            Spacer(Modifier.width(8.dp))
        }
    }
}

/**
 * New / change category: name, an emoji and a colour, with a live preview. [onSave] returns false when the name is
 * empty or is a built-in category name (then an error shows).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, color: Long) -> Boolean,
    initialEmoji: String = CustomCategories.EMOJI.first(),
    initialColor: Long = CustomCategories.COLORS[6],
    nameLocked: Boolean = false,
) {
    var name by remember { mutableStateOf(CustomCategories.cleanName(initialName)) }
    var emoji by remember { mutableStateOf(initialEmoji) }
    var color by remember { mutableLongStateOf(initialColor) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = sheetCard(),
        title = { Text(if (nameLocked) "Change $initialName" else "New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)).background(Color(color)), contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(CustomCategories.MAX_NAME); error = null },
                        label = { Text("Name") },
                        placeholder = { Text("For example: Gym") },
                        singleLine = true,
                        enabled = !nameLocked,
                        isError = error != null,
                        supportingText = error?.let { e -> { Text(e) } },
                        shape = FieldShape,
                        colors = appleFieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Icon", style = MaterialTheme.typography.labelMedium, color = AppColors.current.secondaryLabel)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CustomCategories.EMOJI.forEach { e ->
                        val on = e == emoji
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (on) AppColors.current.accent.copy(alpha = 0.18f) else Color.Transparent)
                                .then(if (on) Modifier.border(BorderStroke(1.5.dp, AppColors.current.accent), RoundedCornerShape(10.dp)) else Modifier)
                                .clickable(role = Role.RadioButton) { emoji = e }
                                .semantics { selected = on },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
                Text("Colour", style = MaterialTheme.typography.labelMedium, color = AppColors.current.secondaryLabel)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CustomCategories.COLORS.forEach { c ->
                        val on = c == color
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.RadioButton) { color = c }
                                .semantics { selected = on; contentDescription = "Colour" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c)), contentAlignment = Alignment.Center) {
                                if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clean = CustomCategories.cleanName(name)
                    error = when {
                        clean.isEmpty() -> "Type a name"
                        !onSave(clean, emoji, color) -> "That name is already a built-in category"
                        else -> null
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) { Text(if (nameLocked) "Save" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Cancel") } },
    )
}
