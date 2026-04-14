package de.kitshn.ui.dialog.select

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import co.touchlab.kermit.Logger
import de.kitshn.KITSHN_KEYWORD_FLAG_PREFIX
import de.kitshn.api.tandoor.TandoorClient
import de.kitshn.api.tandoor.TandoorRequestStateState
import de.kitshn.api.tandoor.model.TandoorKeyword
import de.kitshn.api.tandoor.rememberTandoorRequestState
import de.kitshn.handleTandoorRequestState
import de.kitshn.reachedBottom
import de.kitshn.removeIf
import de.kitshn.repo.KeywordRepository
import de.kitshn.ui.TandoorRequestErrorHandler
import de.kitshn.ui.component.alert.FullSizeAlertPane
import de.kitshn.ui.component.input.AlwaysDockedSearchBar
import de.kitshn.ui.component.input.iosKeyboardWorkaround.InputFieldWithIOSKeyboardWorkaround
import de.kitshn.ui.component.loading.LazyListAnimatedContainedLoadingIndicator
import de.kitshn.ui.layout.ResponsiveSideBySideLayout
import de.kitshn.ui.modifier.fullWidthAlertDialogPadding
import de.kitshn.ui.view.home.search.HOME_SEARCH_PAGING_SIZE
import kitshn.composeapp.generated.resources.Res
import kitshn.composeapp.generated.resources.action_add
import kitshn.composeapp.generated.resources.action_apply
import kitshn.composeapp.generated.resources.common_create_argument
import kitshn.composeapp.generated.resources.search_tags
import kitshn.composeapp.generated.resources.search_tags_filter
import kitshn.composeapp.generated.resources.search_tags_filter_empty
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

const val KEYWORD_PAGE_SIZE = HOME_SEARCH_PAGING_SIZE

@Composable
fun rememberSelectMultipleKeywordsDialogState(): SelectMultipleKeywordsDialogState {
    return remember {
        SelectMultipleKeywordsDialogState()
    }
}

class SelectMultipleKeywordsDialogState(
    val shown: MutableState<Boolean> = mutableStateOf(false)
) {
    val selectedKeywords = mutableStateListOf<TandoorKeyword>()

    fun open(selectedKeywords: List<TandoorKeyword>) {
        this.selectedKeywords.clear()
        this.selectedKeywords.addAll(selectedKeywords)

        this.shown.value = true
    }

    fun dismiss() {
        this.shown.value = false
    }
}

@Composable
fun SelectMultipleKeywordsDialog(
    keywordRepo: KeywordRepository,
    client: TandoorClient,
    state: SelectMultipleKeywordsDialogState,
    prepend: @Composable () -> Unit = {},
    hideKeywordCreation: Boolean = false,
    onSubmit: (keywords: List<TandoorKeyword>) -> Unit
) {
    if(!state.shown.value) return

    AlertDialog(
        modifier = Modifier.fullWidthAlertDialogPadding(),
        onDismissRequest = {
            state.dismiss()
        },
        icon = {
            Icon(Icons.Rounded.Tag, stringResource(Res.string.search_tags_filter))
        },
        title = {
            Text(stringResource(Res.string.search_tags_filter))
        },
        text = {
            Column {
                prepend()

                BoxWithConstraints {
                    ResponsiveSideBySideLayout(
                        showDivider = true,

                        leftMinWidth = 200.dp,
                        rightMinWidth = 200.dp,

                        maxHeight = 800.dp,

                        leftLayout = { enoughSpace ->
                            Box(
                                Modifier.height(
                                    if(enoughSpace)
                                        this@BoxWithConstraints.maxHeight
                                    else
                                        (this@BoxWithConstraints.maxHeight - 32.dp) / 2f
                                ),
                            ) {
                                KeywordSearchBar(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(),
                                    keywordRepo = keywordRepo,
                                    client = client,
                                    hideKeywordCreation = hideKeywordCreation,
                                    selectedKeywords = state.selectedKeywords
                                ) { keyword, keywordId, value ->
                                    if(value) {
                                        state.selectedKeywords.add(0, keyword)
                                    } else {
                                        state.selectedKeywords.removeIf { it.id == keywordId }
                                    }
                                }
                            }
                        }
                    ) {
                        Box(
                            Modifier.fillMaxHeight()
                        ) {
                            if(state.selectedKeywords.isEmpty()) {
                                FullSizeAlertPane(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = stringResource(Res.string.search_tags_filter_empty),
                                    text = stringResource(Res.string.search_tags_filter_empty)
                                )
                            } else {
                                LazyColumn(
                                    Modifier.clip(RoundedCornerShape(16.dp))
                                ) {
                                    items(
                                        state.selectedKeywords.size,
                                        key = { state.selectedKeywords[it].id }) {
                                        val keyword = state.selectedKeywords[it]

                                        KeywordCheckedListItem(
                                            checked = true,
                                            keyword = keyword
                                        ) {
                                            state.selectedKeywords.remove(keyword)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                state.dismiss()
                onSubmit(state.selectedKeywords)
            }) {
                Text(stringResource(Res.string.action_apply))
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    )
}

@Composable
fun KeywordCheckedListItem(
    modifier: Modifier = Modifier,
    checked: Boolean,
    keyword: TandoorKeyword,
    onCheckedChange: (value: Boolean) -> Unit
) {
    ListItem(
        modifier = modifier
            .alpha(
                if(keyword.name.startsWith(KITSHN_KEYWORD_FLAG_PREFIX)) {
                    0.5f
                } else {
                    1f
                }
            )
            .clickable {
                onCheckedChange(!checked)
            },
        leadingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = {
                    onCheckedChange(it)
                }
            )
        },
        headlineContent = {
            Text(keyword.label)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordSearchBar(
    client: TandoorClient,
    keywordRepo: KeywordRepository,
    modifier: Modifier = Modifier,
    selectedKeywords: List<TandoorKeyword>,
    hideKeywordCreation: Boolean = false,
    onCheckedChange: (keyword: TandoorKeyword, keywordId: Int, value: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(query) {
        delay(250)
        search = query
    }

    val selectedKeywordIds by remember(selectedKeywords) {
        derivedStateOf { selectedKeywords.map { it.id }.toSet() }
    }

    val searchRequestState = rememberTandoorRequestState()
    val extendedSearchRequestState = rememberTandoorRequestState()

    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    var nextPageExists by rememberSaveable { mutableStateOf(false) }

    val searchResults = remember { mutableStateListOf<TandoorKeyword>() }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(search) {
        isLoading = true
        try {
            val results = if (search.isBlank()) {
                keywordRepo.getAll(limit = KEYWORD_PAGE_SIZE)
            } else {
                keywordRepo.search(search, limit = KEYWORD_PAGE_SIZE)
            }
            searchResults.clear()
            searchResults.addAll(results)
        } finally {
            isLoading = false
        }
    }

    var showKeywordCreationItem by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(searchResults.toList(), query) {
        showKeywordCreationItem = if(query.length > 2) {
            when(searchResults.size) {
                0 -> true
                else -> searchResults.first().name.lowercase() != query.lowercase()
            }
        } else {
            false
        }
    }

    val createKeywordRequestState = rememberTandoorRequestState()
    fun createKeyword(name: String) = coroutineScope.launch {
        createKeywordRequestState.wrapRequest {
            val keyword = keywordRepo.ensureCreated(client, name, name)
            query = ""
            onCheckedChange(keyword, keyword.id, true)
        }
    }

    AlwaysDockedSearchBar(
        modifier = modifier,
        colors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            dividerColor = Color.Transparent
        ),
        inputField = {
            SearchBarDefaults.InputFieldWithIOSKeyboardWorkaround(
                query = query,
                onQueryChange = { query = it },
                onSearch = {
                    keyboardController?.hide()
                    search = it
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, stringResource(Res.string.search_tags))
                },
                placeholder = { Text(stringResource(Res.string.search_tags)) },
                expanded = true,
                onExpandedChange = { }
            )
        }
    ) {
        LazyColumn {
            if(showKeywordCreationItem && !hideKeywordCreation) {
                item {
                    ListItem(
                        modifier = Modifier.clickable { createKeyword(query) },
                        leadingContent = {
                            IconButton(onClick = { createKeyword(query) }) {
                                Icon(Icons.Rounded.Add, stringResource(Res.string.action_add))
                            }
                        },
                        headlineContent = {
                            Text(stringResource(Res.string.common_create_argument, query))
                        }
                    )
                }
            }

            items(searchResults.size, key = { searchResults[it].id }) {
                val keyword = searchResults[it]
                KeywordCheckedListItem(
                    checked = selectedKeywordIds.contains(keyword.id),
                    keyword = keyword
                ) {
                    keyboardController?.hide()
                    onCheckedChange(keyword, keyword.id, !selectedKeywordIds.contains(keyword.id))
                }
            }

            LazyListAnimatedContainedLoadingIndicator(
                nextPageExists = true,
                extendedRequestState = extendedSearchRequestState
            )
        }
    }

    TandoorRequestErrorHandler(state = createKeywordRequestState)}