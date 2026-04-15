package com.chemecador.secretaria.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chemecador.secretaria.SecretariaOverflowMenu
import com.chemecador.secretaria.SecretariaTopBarColor
import com.chemecador.secretaria.SecretariaTopBarContentColor
import com.chemecador.secretaria.noteslists.formatNotesListDate
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.action_accept
import secretaria.composeapp.generated.resources.action_cancel_request
import secretaria.composeapp.generated.resources.action_reject
import secretaria.composeapp.generated.resources.cancel
import secretaria.composeapp.generated.resources.delete
import secretaria.composeapp.generated.resources.friends_add_tab
import secretaria.composeapp.generated.resources.friends_cancel_request_message
import secretaria.composeapp.generated.resources.friends_cancel_request_title
import secretaria.composeapp.generated.resources.friends_code_hint
import secretaria.composeapp.generated.resources.friends_delete_message
import secretaria.composeapp.generated.resources.friends_delete_title
import secretaria.composeapp.generated.resources.friends_empty
import secretaria.composeapp.generated.resources.friends_error_action
import secretaria.composeapp.generated.resources.friends_error_already_friends
import secretaria.composeapp.generated.resources.friends_error_invalid_code
import secretaria.composeapp.generated.resources.friends_error_load
import secretaria.composeapp.generated.resources.friends_error_own_code
import secretaria.composeapp.generated.resources.friends_error_request_exists
import secretaria.composeapp.generated.resources.friends_error_user_not_found
import secretaria.composeapp.generated.resources.friends_friend_requests_empty
import secretaria.composeapp.generated.resources.friends_friends_tab
import secretaria.composeapp.generated.resources.friends_my_code
import secretaria.composeapp.generated.resources.friends_remove_success
import secretaria.composeapp.generated.resources.friends_request_accepted
import secretaria.composeapp.generated.resources.friends_request_cancelled
import secretaria.composeapp.generated.resources.friends_request_rejected
import secretaria.composeapp.generated.resources.friends_request_sent
import secretaria.composeapp.generated.resources.friends_requests_tab
import secretaria.composeapp.generated.resources.friends_retry
import secretaria.composeapp.generated.resources.friends_send_button
import secretaria.composeapp.generated.resources.friends_sent_requests_empty
import secretaria.composeapp.generated.resources.friends_sent_requests_title
import secretaria.composeapp.generated.resources.friends_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableStateOf(FriendsTab.FRIENDS) }
    var friendCode by rememberSaveable { mutableStateOf("") }
    var friendToDelete by remember { mutableStateOf<FriendSummary?>(null) }
    var outgoingRequestToCancel by remember { mutableStateOf<OutgoingFriendRequest?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.load()
    }

    val snackbarMessage = state.message?.let { message ->
        when (message) {
            FriendsMessage.LOAD_FAILED -> stringResource(Res.string.friends_error_load)
            FriendsMessage.ACTION_FAILED -> stringResource(Res.string.friends_error_action)
            FriendsMessage.INVALID_FRIEND_CODE -> stringResource(Res.string.friends_error_invalid_code)
            FriendsMessage.OWN_FRIEND_CODE -> stringResource(Res.string.friends_error_own_code)
            FriendsMessage.USER_NOT_FOUND -> stringResource(Res.string.friends_error_user_not_found)
            FriendsMessage.ALREADY_FRIENDS -> stringResource(Res.string.friends_error_already_friends)
            FriendsMessage.REQUEST_ALREADY_EXISTS -> stringResource(Res.string.friends_error_request_exists)
            FriendsMessage.REQUEST_SENT -> stringResource(Res.string.friends_request_sent)
            FriendsMessage.REQUEST_ACCEPTED -> stringResource(Res.string.friends_request_accepted)
            FriendsMessage.REQUEST_REJECTED -> stringResource(Res.string.friends_request_rejected)
            FriendsMessage.REQUEST_CANCELLED -> stringResource(Res.string.friends_request_cancelled)
            FriendsMessage.FRIEND_REMOVED -> stringResource(Res.string.friends_remove_success)
        }
    }

    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        if (state.message == FriendsMessage.REQUEST_SENT) {
            friendCode = ""
        }
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.friends_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecretariaTopBarColor,
                    titleContentColor = SecretariaTopBarContentColor,
                    navigationIconContentColor = SecretariaTopBarContentColor,
                    actionIconContentColor = SecretariaTopBarContentColor,
                ),
                actions = {
                    SecretariaOverflowMenu(
                        onOpenFriends = onOpenFriends,
                        onOpenSettings = onOpenSettings,
                        onLogout = onLogout,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                FriendsTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                when (tab) {
                                    FriendsTab.FRIENDS -> stringResource(Res.string.friends_friends_tab)
                                    FriendsTab.REQUESTS -> stringResource(Res.string.friends_requests_tab)
                                    FriendsTab.ADD -> stringResource(Res.string.friends_add_tab)
                                },
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    FriendsTab.FRIENDS -> Icons.Default.People
                                    FriendsTab.REQUESTS -> Icons.Default.Notifications
                                    FriendsTab.ADD -> Icons.Default.PersonAddAlt1
                                },
                                contentDescription = null,
                            )
                        },
                    )
                }
            }

            when {
                state.isLoading -> CenteredContainer {
                    CircularProgressIndicator()
                }

                state.contentError != null -> ErrorContent(onRetry = viewModel::refresh)

                else -> when (selectedTab) {
                    FriendsTab.FRIENDS -> FriendsListTab(
                        items = state.friends,
                        onDelete = { friendToDelete = it },
                    )

                    FriendsTab.REQUESTS -> RequestsTab(
                        items = state.incomingRequests,
                        isBusy = state.isWorking,
                        onAccept = viewModel::acceptFriendRequest,
                        onReject = viewModel::rejectFriendRequest,
                    )

                    FriendsTab.ADD -> AddFriendTab(
                        myCode = state.userCode,
                        friendCode = friendCode,
                        outgoingRequests = state.outgoingRequests,
                        isBusy = state.isWorking,
                        onFriendCodeChange = { friendCode = it },
                        onSend = { viewModel.sendFriendRequest(friendCode) },
                        onCancelRequest = { outgoingRequestToCancel = it },
                    )
                }
            }
        }
    }

    friendToDelete?.let { friend ->
        ConfirmationDialog(
            title = stringResource(Res.string.friends_delete_title),
            message = stringResource(Res.string.friends_delete_message, friend.name),
            confirmText = stringResource(Res.string.delete),
            dismissText = stringResource(Res.string.cancel),
            onConfirm = {
                viewModel.deleteFriend(friend.friendshipId)
                friendToDelete = null
            },
            onDismiss = { friendToDelete = null },
        )
    }

    outgoingRequestToCancel?.let {
        ConfirmationDialog(
            title = stringResource(Res.string.friends_cancel_request_title),
            message = stringResource(Res.string.friends_cancel_request_message),
            confirmText = stringResource(Res.string.action_cancel_request),
            dismissText = stringResource(Res.string.cancel),
            onConfirm = {
                viewModel.cancelFriendRequest(it.id)
                outgoingRequestToCancel = null
            },
            onDismiss = { outgoingRequestToCancel = null },
        )
    }
}

private enum class FriendsTab {
    FRIENDS,
    REQUESTS,
    ADD,
}

@Composable
private fun FriendsListTab(
    items: List<FriendSummary>,
    onDelete: (FriendSummary) -> Unit,
) {
    if (items.isEmpty()) {
        CenteredContainer {
            Text(
                text = stringResource(Res.string.friends_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.friendshipId }) { friend ->
            FriendCard(
                title = friend.name,
                trailing = {
                    IconButton(onClick = { onDelete(friend) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.delete),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun RequestsTab(
    items: List<IncomingFriendRequest>,
    isBusy: Boolean,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    if (items.isEmpty()) {
        CenteredContainer {
            Text(
                text = stringResource(Res.string.friends_friend_requests_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { request ->
            FriendCard(
                title = request.senderName,
                subtitle = formatNotesListDate(request.requestedAt),
                trailing = {
                    Row {
                        IconButton(
                            enabled = !isBusy,
                            onClick = { onReject(request.id) },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.action_reject),
                            )
                        }
                        IconButton(
                            enabled = !isBusy,
                            onClick = { onAccept(request.id) },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(Res.string.action_accept),
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun AddFriendTab(
    myCode: String?,
    friendCode: String,
    outgoingRequests: List<OutgoingFriendRequest>,
    isBusy: Boolean,
    onFriendCodeChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelRequest: (OutgoingFriendRequest) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(Res.string.friends_my_code),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    text = myCode.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }

        item {
            OutlinedTextField(
                value = friendCode,
                onValueChange = { newValue ->
                    onFriendCodeChange(newValue.filter(Char::isDigit))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.friends_code_hint)) },
                singleLine = true,
                enabled = !isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        item {
            TextButton(
                enabled = !isBusy,
                onClick = onSend,
            ) {
                Text(stringResource(Res.string.friends_send_button))
            }
        }

        item {
            Text(
                text = stringResource(Res.string.friends_sent_requests_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (outgoingRequests.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.friends_sent_requests_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(outgoingRequests, key = { it.id }) { request ->
                FriendCard(
                    title = request.receiverCode,
                    subtitle = formatNotesListDate(request.requestedAt),
                    trailing = {
                        IconButton(
                            enabled = !isBusy,
                            onClick = { onCancelRequest(request) },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.action_cancel_request),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FriendCard(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                subtitle?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing()
        }
    }
}

@Composable
private fun ErrorContent(
    onRetry: () -> Unit,
) {
    CenteredContainer {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(Res.string.friends_error_load),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(Res.string.friends_retry))
            }
        }
    }
}

@Composable
private fun CenteredContainer(
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
    )
}
