package dev.hyo.deepline.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ripple
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RouteWelcome = "welcome"
private const val RouteIdentity = "identity"
private const val RoutePhoneLogin = "phone_login"
private const val RoutePhoneVerify = "phone_verify"
private const val RouteChats = "chats"
private const val RouteChat = "chat"
private const val RouteAddFriend = "add_friend"
private const val RouteSettings = "settings"
private const val RouteDevices = "devices"
private const val RouteCreateGroup = "create_group"
private const val RouteGroupInfo = "group_info"

@Composable
fun DeeplineAndroidApp(
  launchConfig: DeeplineLaunchConfig = DeeplineLaunchConfig(),
) {
  DeeplineTheme {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val model = remember { DeeplineAppModel(context, launchConfig) }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route.orEmpty()

    LaunchedEffect(model.identityReady) {
      if (model.identityReady) {
        model.bootstrapIfNeeded()
      }
    }

    // Auto-refresh conversations when on chat list
    LaunchedEffect(model.identityReady, currentRoute) {
      if (model.identityReady && currentRoute == RouteChats) {
        while (true) {
          kotlinx.coroutines.delay(2_000)
          model.refreshConversations()
        }
      }
    }

    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.surface,
    ) {
      Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
        if (model.identityReady && currentRoute in setOf(RouteChats, RouteSettings)) {
          DeeplineBottomBar(
            currentRoute = currentRoute,
            onNavigate = { route -> navController.navigate(route) },
          )
        }
      },
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        NavHost(
          navController = navController,
          startDestination = if (model.identityReady) RouteChats else RouteWelcome,
        ) {
          composable(RouteWelcome) {
            WelcomeScreen(
              onContinue = { navController.navigate(RouteIdentity) },
              onPhoneLogin = { navController.navigate(RoutePhoneLogin) },
            )
          }
          composable(RoutePhoneLogin) {
            PhoneLoginScreen(
              onBack = { navController.popBackStack() },
              phoneAuthMessage = model.phoneAuthMessage,
              onSendOtp = { phoneNumber, countryCode ->
                scope.launch {
                  if (model.sendPhoneOtp(phoneNumber, countryCode)) {
                    navController.navigate(RoutePhoneVerify)
                  }
                }
              },
            )
          }
          composable(RoutePhoneVerify) {
            PhoneVerifyScreen(
              phoneAuthMessage = model.phoneAuthMessage,
              onBack = {
                model.clearPhoneAuth()
                navController.popBackStack()
              },
              onVerify = { otpCode ->
                scope.launch {
                  val conversationId = model.verifyPhoneOtp(otpCode)
                  if (conversationId != null) {
                    navController.navigate("$RouteChat/$conversationId") {
                      popUpTo(RouteWelcome) { inclusive = true }
                    }
                  }
                }
              },
            )
          }
          composable(RouteIdentity) {
            IdentitySetupScreen(
              deviceName = model.deviceName,
              onComplete = { name, device ->
                scope.launch {
                  val conversationId = model.completeIdentity(name, device)
                  if (conversationId != null) {
                    navController.navigate("$RouteChat/$conversationId") {
                      popUpTo(RouteWelcome) { inclusive = true }
                    }
                  }
                }
              },
            )
          }
          composable(RouteChats) {
            ChatListScreen(
              chats = model.chats,
              onOpenChat = { conversationId -> navController.navigate("$RouteChat/$conversationId") },
              onAddFriend = { navController.navigate(RouteAddFriend) },
              onCreateGroup = { navController.navigate(RouteCreateGroup) },
              onRefresh = { scope.launch { model.refreshConversations() } },
            )
          }
          composable(
            route = "$RouteChat/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
          ) { backStackEntry ->
            val conversationId = requireNotNull(backStackEntry.arguments?.getString("conversationId"))
            val isGroup = model.isGroup(conversationId)
            val memberCount = if (isGroup) model.currentGroupMembers(conversationId).size else 2
            ChatRoomScreen(
              title = model.chatTitle(conversationId),
              messages = model.currentMessages(conversationId),
              isGroup = isGroup,
              memberCount = memberCount,
              onBack = { navController.popBackStack() },
              onLoad = {
                scope.launch {
                  model.loadMessages(conversationId)
                  if (isGroup) model.loadGroupMembers(conversationId)
                }
              },
              onSend = { text -> scope.launch { model.sendMessage(conversationId, text) } },
              onOpenGroupInfo = if (isGroup) {
                { navController.navigate("$RouteGroupInfo/$conversationId") }
              } else null,
            )
          }
          composable(RouteCreateGroup) {
            CreateGroupScreen(
              onBack = { navController.popBackStack() },
              onCreate = { title, memberIds ->
                scope.launch {
                  val conversationId = model.createGroup(title, memberIds)
                  if (conversationId != null) {
                    navController.navigate("$RouteChat/$conversationId") {
                      popUpTo(RouteChats)
                    }
                  }
                }
              },
            )
          }
          composable(
            route = "$RouteGroupInfo/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
          ) { backStackEntry ->
            val conversationId = requireNotNull(backStackEntry.arguments?.getString("conversationId"))
            GroupInfoScreen(
              title = model.chatTitle(conversationId),
              members = model.currentGroupMembers(conversationId),
              myRole = model.myRoleIn(conversationId),
              onBack = { navController.popBackStack() },
              onLoad = { scope.launch { model.loadGroupMembers(conversationId) } },
              onRemoveMember = { userId ->
                scope.launch { model.removeGroupMembers(conversationId, listOf(userId)) }
              },
              onLeave = {
                scope.launch {
                  if (model.leaveGroup(conversationId)) {
                    navController.popBackStack(RouteChats, inclusive = false)
                  }
                }
              },
              onUpdateTitle = { newTitle ->
                scope.launch { model.updateGroupTitle(conversationId, newTitle) }
              },
            )
          }
          composable(RouteAddFriend) {
            AddFriendScreen(
              latestInviteCode = model.latestInviteCode,
              onBack = { navController.popBackStack() },
              onGenerateInvite = { scope.launch { model.createInvite() } },
              onInviteImported = { inviteCode ->
                scope.launch {
                  val conversationId = model.importInvite(inviteCode)
                  if (conversationId != null) {
                    navController.navigate("$RouteChat/$conversationId") {
                      popUpTo(RouteChats)
                    }
                  }
                }
              },
            )
          }
          composable(RouteSettings) {
            SettingsScreen(
              displayName = model.displayName.ifEmpty { "Deepline User" },
              deviceName = model.deviceName,
              userId = model.userId,
              serverBaseUrl = model.serverBaseUrl,
              onSaveServerUrl = { value ->
                model.saveServerBaseUrl(value)
                scope.launch { model.refreshConversations() }
              },
              onOpenDevices = { navController.navigate(RouteDevices) },
            )
          }
          composable(RouteDevices) {
            DevicesScreen(
              deviceName = model.deviceName,
              onBack = { navController.popBackStack() },
            )
          }
        }

        // Loading overlay
        AnimatedVisibility(
          visible = model.isBusy,
          modifier = Modifier.align(Alignment.Center),
          enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.9f),
          exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.9f),
        ) {
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp,
            tonalElevation = 4.dp,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                "Syncing...",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }

        // Error snackbar
        AnimatedVisibility(
          visible = model.errorMessage != null,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp),
          enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
          exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
        ) {
          model.errorMessage?.let { message ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.errorContainer,
              shadowElevation = 8.dp,
            ) {
              Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                  contentAlignment = Alignment.Center,
                ) {
                  Text("!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                  text = message,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }
      }
    }
  }
}

@Composable
private fun DeeplineBottomBar(
  currentRoute: String,
  onNavigate: (String) -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 8.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .height(72.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
      // Chats tab
      BottomNavItem(
        icon = "💬",
        label = "Chats",
        selected = currentRoute == RouteChats,
        onClick = { onNavigate(RouteChats) },
        modifier = Modifier.weight(1f),
      )
      // Settings tab
      BottomNavItem(
        icon = "⚙️",
        label = "Settings",
        selected = currentRoute == RouteSettings,
        onClick = { onNavigate(RouteSettings) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun BottomNavItem(
  icon: String,
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.92f else 1f,
    animationSpec = tween(100),
    label = "nav_scale",
  )
  val alpha by animateFloatAsState(
    targetValue = if (selected) 1f else 0.7f,
    animationSpec = tween(150),
    label = "nav_alpha",
  )

  Box(
    modifier = modifier
      .fillMaxHeight()
      .clickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary),
        onClick = onClick,
      )
      .scale(scale),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.alpha(alpha),
    ) {
      Box(
        modifier = Modifier
          .size(if (selected) 44.dp else 40.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(
            if (selected)
              MaterialTheme.colorScheme.primaryContainer
            else
              Color.Transparent
          ),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          icon,
          fontSize = if (selected) 20.sp else 22.sp,
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected)
          MaterialTheme.colorScheme.primary
        else
          MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

// Pressable modifier with scale animation
@Composable
private fun Modifier.pressable(
  onClick: () -> Unit,
  enabled: Boolean = true,
): Modifier {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.97f else 1f,
    animationSpec = tween(100),
    label = "press_scale",
  )

  return this
    .scale(scale)
    .clickable(
      interactionSource = interactionSource,
      indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary),
      enabled = enabled,
      onClick = onClick,
    )
}

@Composable
private fun WelcomeScreen(
  onContinue: () -> Unit,
  onPhoneLogin: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(32.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Animated Logo
      Box(
        modifier = Modifier
          .size(100.dp)
          .shadow(16.dp, CircleShape)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "D",
          fontSize = 48.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimary,
        )
      }

      Spacer(modifier = Modifier.height(40.dp))

      Text(
        text = "Deepline",
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "Secure. Private. Encrypted.",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "End-to-end encrypted messaging\nfor everyone",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 26.sp,
      )

      Spacer(modifier = Modifier.height(48.dp))

      // Phone login button (primary)
      Button(
        onClick = onPhoneLogin,
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
        ),
        elevation = ButtonDefaults.buttonElevation(
          defaultElevation = 4.dp,
          pressedElevation = 8.dp,
        ),
      ) {
        Text(
          "Continue with Phone",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Device-only option (secondary)
      FilledTonalButton(
        onClick = onContinue,
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp),
        shape = RoundedCornerShape(16.dp),
      ) {
        Text(
          "Continue Without Phone",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Medium,
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "Device-only mode for testing",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      )
    }

    // Version info
    Text(
      text = "v0.1.0 • End-to-end encrypted",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 24.dp),
    )
  }
}

@Composable
private fun IdentitySetupScreen(
  deviceName: String,
  onComplete: (String, String) -> Unit,
) {
  var displayName by rememberSaveable { mutableStateOf("") }
  var localDeviceName by rememberSaveable { mutableStateOf(deviceName) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(24.dp),
  ) {
    Spacer(modifier = Modifier.height(32.dp))

    // Progress indicator
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
    ) {
      repeat(3) { index ->
        Box(
          modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(if (index == 0) 24.dp else 8.dp, 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
              if (index == 0)
                MaterialTheme.colorScheme.primary
              else
                MaterialTheme.colorScheme.outlineVariant
            ),
        )
      }
    }

    Spacer(modifier = Modifier.height(40.dp))

    Text(
      text = "Create your profile",
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Your identity keys are stored securely on this device and never leave it.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      lineHeight = 22.sp,
    )

    Spacer(modifier = Modifier.height(40.dp))

    // Avatar preview
    Box(
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .size(88.dp)
        .shadow(8.dp, CircleShape)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = displayName.firstOrNull()?.uppercase() ?: "?",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
      "Display Name",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )

    OutlinedTextField(
      value = displayName,
      onValueChange = { displayName = it },
      placeholder = { Text("How others will see you") },
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      singleLine = true,
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
      ),
    )

    Spacer(modifier = Modifier.height(20.dp))

    Text(
      "Device Name",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )

    OutlinedTextField(
      value = localDeviceName,
      onValueChange = { localDeviceName = it },
      placeholder = { Text("e.g., My Phone") },
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      singleLine = true,
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
      ),
    )

    Spacer(modifier = Modifier.weight(1f))

    Button(
      onClick = { onComplete(displayName, localDeviceName) },
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp),
      shape = RoundedCornerShape(16.dp),
      enabled = displayName.isNotBlank(),
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      ),
    ) {
      Text(
        "Continue",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
    }

    Spacer(modifier = Modifier.height(16.dp))
  }
}

@Composable
private fun ChatListScreen(
  chats: List<DeeplineChat>,
  onOpenChat: (String) -> Unit,
  onAddFriend: () -> Unit,
  onCreateGroup: () -> Unit,
  onRefresh: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
      .statusBarsPadding(),
  ) {
    // Header
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 1.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Chats",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          // Sync button
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .pressable(onClick = onRefresh),
            contentAlignment = Alignment.Center,
          ) {
            Text("🔄", fontSize = 18.sp)
          }
          // Create group button
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.tertiaryContainer)
              .pressable(onClick = onCreateGroup),
            contentAlignment = Alignment.Center,
          ) {
            Text("👥", fontSize = 18.sp)
          }
          // Add friend button
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary)
              .pressable(onClick = onAddFriend),
            contentAlignment = Alignment.Center,
          ) {
            Text("+", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Medium)
          }
        }
      }
    }

    if (chats.isEmpty()) {
      // Empty state
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Box(
            modifier = Modifier
              .size(80.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
          ) {
            Text("💬", fontSize = 36.sp)
          }
          Text(
            "No conversations yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            "Tap + to add a friend and start chatting",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = onAddFriend,
            shape = RoundedCornerShape(12.dp),
          ) {
            Text("Add Friend")
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
      ) {
        items(chats) { chat ->
          ChatListItem(
            chat = chat,
            onClick = { onOpenChat(chat.id) },
          )
        }
      }
    }
  }
}

@Composable
private fun ChatListItem(
  chat: DeeplineChat,
  onClick: () -> Unit,
) {
  val isGroup = chat.protocolType.name.contains("GROUP")

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .pressable(onClick = onClick),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Avatar
      Box(
        modifier = Modifier
          .size(56.dp)
          .shadow(4.dp, CircleShape)
          .clip(CircleShape)
          .background(
            if (isGroup)
              MaterialTheme.colorScheme.tertiaryContainer
            else
              MaterialTheme.colorScheme.primaryContainer
          ),
        contentAlignment = Alignment.Center,
      ) {
        if (isGroup) {
          // Group avatar
          Text("👥", fontSize = 24.sp)
        } else {
          Text(
            text = chat.title.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isGroup)
              MaterialTheme.colorScheme.onTertiaryContainer
            else
              MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = chat.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (isGroup) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(MaterialTheme.colorScheme.tertiaryContainer)
                  .padding(horizontal = 6.dp, vertical = 2.dp),
              ) {
                Text(
                  "Group",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
              }
            }
          }
          Text(
            text = "now",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = chat.preview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          // Encryption badge
          Box(
            modifier = Modifier
              .padding(start = 8.dp)
              .size(18.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Text("🔒", fontSize = 10.sp)
          }
        }
      }
    }
  }
}

@Composable
private fun ChatRoomScreen(
  title: String,
  messages: List<DeeplineMessage>,
  isGroup: Boolean,
  memberCount: Int,
  onBack: () -> Unit,
  onLoad: () -> Unit,
  onSend: (String) -> Unit,
  onOpenGroupInfo: (() -> Unit)? = null,
) {
  var draft by rememberSaveable { mutableStateOf("") }
  val listState = rememberLazyListState()

  LaunchedEffect(title) {
    onLoad()
    while (true) {
      kotlinx.coroutines.delay(3_000)
      onLoad()
    }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .imePadding(),
  ) {
    // Header
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 2.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Back button
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressable(onClick = onBack),
          contentAlignment = Alignment.Center,
        ) {
          Text("←", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
        }

        // Avatar
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
              if (isGroup)
                MaterialTheme.colorScheme.tertiaryContainer
              else
                MaterialTheme.colorScheme.primaryContainer
            ),
          contentAlignment = Alignment.Center,
        ) {
          if (isGroup) {
            Text("👥", fontSize = 20.sp)
          } else {
            Text(
              text = title.firstOrNull()?.uppercase() ?: "?",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text("🔒", fontSize = 10.sp)
            Text(
              text = if (isGroup) "$memberCount members • encrypted" else "encrypted",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        // Sync button
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressable(onClick = onLoad),
          contentAlignment = Alignment.Center,
        ) {
          Text("🔄", fontSize = 18.sp)
        }

        // Menu button / Group info
        if (isGroup && onOpenGroupInfo != null) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .pressable(onClick = onOpenGroupInfo),
            contentAlignment = Alignment.Center,
          ) {
            Text("ℹ️", fontSize = 18.sp)
          }
        } else {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .pressable(onClick = { }),
            contentAlignment = Alignment.Center,
          ) {
            Text("⋮", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }

    // Messages
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      state = listState,
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (messages.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 64.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(64.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
              ) {
                Text("🔐", fontSize = 28.sp)
              }
              Text(
                "Messages are end-to-end encrypted",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                "Send a message to start the conversation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
              )
            }
          }
        }
      } else {
        items(messages) { message ->
          MessageBubble(
            message = message,
            showAvatar = isGroup && !message.isMine && !message.isSystem,
          )
        }
      }
    }

    // Input area
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 8.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Attachment button
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pressable(onClick = { /* TODO: File picker */ }),
          contentAlignment = Alignment.Center,
        ) {
          Text("📎", fontSize = 20.sp)
        }

        // Text input
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
          if (draft.isEmpty()) {
            Text(
              "Message",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
          }
          BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
              color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
          )
        }

        // Send button
        val canSend = draft.isNotBlank()
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
              if (canSend)
                MaterialTheme.colorScheme.primary
              else
                MaterialTheme.colorScheme.surfaceVariant
            )
            .then(
              if (canSend) {
                Modifier.pressable(onClick = {
                  val outgoing = draft
                  draft = ""
                  onSend(outgoing)
                })
              } else {
                Modifier
              }
            ),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            "➤",
            fontSize = 18.sp,
            color = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun MessageBubble(
  message: DeeplineMessage,
  showAvatar: Boolean = false,
) {
  val isMe = message.isMine
  val isSystem = message.isSystem

  if (isSystem) {
    // System message
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
      ) {
        Text(
          text = message.body,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
    return
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
  ) {
    if (!isMe && showAvatar) {
      Box(
        modifier = Modifier
          .padding(end = 8.dp)
          .size(32.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = message.author.firstOrNull()?.uppercase() ?: "?",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    } else if (!isMe && !showAvatar) {
      Spacer(modifier = Modifier.width(40.dp))
    }

    Column(
      modifier = Modifier.widthIn(max = 280.dp),
      horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
    ) {
      Surface(
        shape = RoundedCornerShape(
          topStart = 18.dp,
          topEnd = 18.dp,
          bottomStart = if (isMe) 18.dp else 4.dp,
          bottomEnd = if (isMe) 4.dp else 18.dp,
        ),
        color = if (isMe)
          MaterialTheme.colorScheme.primary
        else
          MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 1.dp,
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
          Text(
            text = message.body,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isMe)
              MaterialTheme.colorScheme.onPrimary
            else
              MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.End),
          ) {
            Text(
              text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
              style = MaterialTheme.typography.labelSmall,
              color = if (isMe)
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
              else
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isMe) {
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                "✓✓",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AddFriendScreen(
  latestInviteCode: String,
  onBack: () -> Unit,
  onGenerateInvite: () -> Unit,
  onInviteImported: (String) -> Unit,
) {
  var inviteCode by rememberSaveable { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    // Header
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 2.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressable(onClick = onBack),
          contentAlignment = Alignment.Center,
        ) {
          Text("←", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          "Add Friend",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      // Share invite section
      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 2.dp,
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Text("🔗", fontSize = 18.sp)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                "Share your invite code",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
            }

            if (latestInviteCode.isNotBlank()) {
              Spacer(modifier = Modifier.height(16.dp))
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = latestInviteCode,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                  )
                  Box(
                    modifier = Modifier
                      .size(36.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primary)
                      .pressable(onClick = { /* Copy to clipboard */ }),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text("📋", fontSize = 16.sp)
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
              onClick = onGenerateInvite,
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text("Generate New Code")
            }
          }
        }
      }

      // Import invite section
      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 2.dp,
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Text("📥", fontSize = 18.sp)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                "Enter invite code",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
              value = inviteCode,
              onValueChange = { inviteCode = it },
              placeholder = { Text("Paste invite code here") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
              onClick = { onInviteImported(inviteCode) },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              enabled = inviteCode.isNotBlank(),
            ) {
              Text("Add Contact")
            }
          }
        }
      }

      // QR Code section (placeholder)
      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text("📱", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              "QR Code Scanning",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              "Coming soon",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsScreen(
  displayName: String,
  deviceName: String,
  userId: String?,
  serverBaseUrl: String,
  onSaveServerUrl: (String) -> Unit,
  onOpenDevices: () -> Unit,
) {
  var editableServerUrl by rememberSaveable(serverBaseUrl) { mutableStateOf(serverBaseUrl) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
      .statusBarsPadding(),
  ) {
    // Header
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 1.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 16.dp),
      ) {
        Text(
          text = "Settings",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Profile card
      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 2.dp,
          modifier = Modifier.pressable(onClick = { }),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = displayName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text("🔒", fontSize = 12.sp)
                Text(
                  "End-to-end encrypted",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
            Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      // Settings sections
      item {
        Text(
          "ACCOUNT",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
      }

      item {
        SettingsItem(
          icon = "📱",
          title = "Devices",
          subtitle = deviceName,
          onClick = onOpenDevices,
        )
      }

      item {
        SettingsItem(
          icon = "🔔",
          title = "Notifications",
          subtitle = "Sounds, vibration",
          onClick = { },
        )
      }

      item {
        SettingsItem(
          icon = "🎨",
          title = "Appearance",
          subtitle = "Theme, chat background",
          onClick = { },
        )
      }

      item {
        Text(
          "ADVANCED",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
        )
      }

      // Server URL
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 1.dp,
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("🌐", fontSize = 20.sp)
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                "Server URL",
                style = MaterialTheme.typography.titleMedium,
              )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
              value = editableServerUrl,
              onValueChange = { editableServerUrl = it },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
              onClick = { onSaveServerUrl(editableServerUrl) },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text("Save & Reconnect")
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
          "Deepline v0.1.0",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun SettingsItem(
  icon: String,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 1.dp,
    modifier = Modifier.pressable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        Text(icon, fontSize = 18.sp)
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          title,
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun DevicesScreen(
  deviceName: String,
  onBack: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    // Header
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 2.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressable(onClick = onBack),
          contentAlignment = Alignment.Center,
        ) {
          Text("←", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          "Devices & Security",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text(
          "LINKED DEVICES",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
      }

      // Current device
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          shadowElevation = 2.dp,
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary),
              contentAlignment = Alignment.Center,
            ) {
              Text("📱", fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  deviceName,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                  Text(
                    "This device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                  )
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                "Active now",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
              )
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          "SECURITY",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
      }

      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 1.dp,
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("🔐", fontSize = 24.sp)
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                "End-to-end Encryption",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              "Your messages are secured with end-to-end encryption. Only you and your contacts can read them. Not even Deepline can access your conversations.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 22.sp,
            )
          }
        }
      }

      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 1.dp,
          modifier = Modifier.pressable(onClick = { }),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
              contentAlignment = Alignment.Center,
            ) {
              Text("🔑", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                "Safety Number",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                "Verify your contacts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

@Composable
private fun CreateGroupScreen(
  onBack: () -> Unit,
  onCreate: (title: String, memberIds: List<String>) -> Unit,
) {
  var groupTitle by rememberSaveable { mutableStateOf("") }
  var memberIdsInput by rememberSaveable { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    // Header
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 2.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressable(onClick = onBack),
          contentAlignment = Alignment.Center,
        ) {
          Text("←", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          "Create Group",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      item {
        // Group avatar preview
        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier = Modifier
              .size(88.dp)
              .shadow(8.dp, CircleShape)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Text("👥", fontSize = 36.sp)
          }
        }
      }

      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 2.dp,
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
          ) {
            Text(
              "Group Name",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(bottom = 8.dp),
            )

            OutlinedTextField(
              value = groupTitle,
              onValueChange = { groupTitle = it },
              placeholder = { Text("e.g., Family, Work Team") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              singleLine = true,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
              "Member User IDs",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(bottom = 8.dp),
            )

            OutlinedTextField(
              value = memberIdsInput,
              onValueChange = { memberIdsInput = it },
              placeholder = { Text("Comma-separated user IDs") },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              singleLine = false,
              minLines = 2,
            )

            Text(
              "Enter user IDs separated by commas. You can get user IDs from invite codes.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 8.dp),
            )
          }
        }
      }

      item {
        Button(
          onClick = {
            val memberIds = memberIdsInput
              .split(",")
              .map { it.trim() }
              .filter { it.isNotEmpty() }
            onCreate(groupTitle, memberIds)
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
          shape = RoundedCornerShape(16.dp),
          enabled = groupTitle.isNotBlank(),
        ) {
          Text(
            "Create Group",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("🔐", fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                "Group Encryption",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                "Groups use MLS protocol for end-to-end encryption. Currently using placeholder codec for development.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PhoneLoginScreen(
  onBack: () -> Unit,
  phoneAuthMessage: String?,
  onSendOtp: (phoneNumber: String, countryCode: String) -> Unit,
) {
  var phoneNumber by rememberSaveable { mutableStateOf("") }
  var countryCode by rememberSaveable { mutableStateOf("+1") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(24.dp),
  ) {
    // Header
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .pressable(onClick = onBack),
        contentAlignment = Alignment.Center,
      ) {
        Text("<-", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
      }
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Phone icon
    Box(
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .size(80.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Text("Phone", fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
      text = "Enter your phone number",
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "We'll send you a verification code to confirm your identity.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      lineHeight = 22.sp,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Country code selector
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OutlinedTextField(
        value = countryCode,
        onValueChange = { countryCode = it },
        label = { Text("Code") },
        modifier = Modifier.width(100.dp),
        shape = RoundedCornerShape(14.dp),
        singleLine = true,
      )

      OutlinedTextField(
        value = phoneNumber,
        onValueChange = { phoneNumber = it },
        label = { Text("Phone Number") },
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(14.dp),
        singleLine = true,
      )
    }

    if (phoneAuthMessage != null) {
      Spacer(modifier = Modifier.height(16.dp))
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
      ) {
        Text(
          text = phoneAuthMessage,
          modifier = Modifier.padding(12.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Spacer(modifier = Modifier.weight(1f))

    Button(
      onClick = { onSendOtp(phoneNumber.trim(), countryCode.trim()) },
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp),
      shape = RoundedCornerShape(16.dp),
      enabled = phoneNumber.isNotBlank() && countryCode.isNotBlank(),
    ) {
      Text(
        "Send Code",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
    }

    Spacer(modifier = Modifier.height(16.dp))
  }
}

@Composable
private fun PhoneVerifyScreen(
  phoneAuthMessage: String?,
  onBack: () -> Unit,
  onVerify: (otpCode: String) -> Unit,
) {
  var otpCode by rememberSaveable { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(24.dp),
  ) {
    // Header
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .pressable(onClick = onBack),
        contentAlignment = Alignment.Center,
      ) {
        Text("<-", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
      }
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Lock icon
    Box(
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .size(80.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Text("Lock", fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
      text = "Enter verification code",
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Enter the 6-digit code sent to your phone.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      lineHeight = 22.sp,
    )

    if (phoneAuthMessage != null) {
      Spacer(modifier = Modifier.height(12.dp))
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
      ) {
        Text(
          text = phoneAuthMessage,
          modifier = Modifier.padding(12.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
      }
    }

    Spacer(modifier = Modifier.height(32.dp))

    OutlinedTextField(
      value = otpCode,
      onValueChange = { if (it.length <= 6) otpCode = it },
      label = { Text("6-digit code") },
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      singleLine = true,
      textStyle = MaterialTheme.typography.headlineMedium.copy(
        textAlign = TextAlign.Center,
        letterSpacing = 8.sp,
      ),
    )

    Spacer(modifier = Modifier.weight(1f))

    Button(
      onClick = { onVerify(otpCode.trim()) },
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp),
      shape = RoundedCornerShape(16.dp),
      enabled = otpCode.length == 6,
    ) {
      Text(
        "Verify",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
    }

    Spacer(modifier = Modifier.height(16.dp))
  }
}

@Composable
private fun GroupInfoScreen(
  title: String,
  members: List<dev.hyo.deepline.shared.model.GroupMember>,
  myRole: dev.hyo.deepline.shared.model.GroupRole?,
  onBack: () -> Unit,
  onLoad: () -> Unit,
  onRemoveMember: (String) -> Unit,
  onLeave: () -> Unit,
  onUpdateTitle: (String) -> Unit,
) {
  var editingTitle by rememberSaveable { mutableStateOf(false) }
  var newTitle by rememberSaveable { mutableStateOf(title) }

  LaunchedEffect(Unit) {
    onLoad()
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    // Header
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 2.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressable(onClick = onBack),
          contentAlignment = Alignment.Center,
        ) {
          Text("←", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          "Group Info",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Group header card
      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 2.dp,
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Box(
              modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Text("👥", fontSize = 36.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (editingTitle) {
              OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
              )
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                FilledTonalButton(
                  onClick = { editingTitle = false; newTitle = title },
                ) {
                  Text("Cancel")
                }
                Button(
                  onClick = {
                    onUpdateTitle(newTitle)
                    editingTitle = false
                  },
                ) {
                  Text("Save")
                }
              }
            } else {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  title,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.SemiBold,
                )
                if (myRole in setOf(
                    dev.hyo.deepline.shared.model.GroupRole.OWNER,
                    dev.hyo.deepline.shared.model.GroupRole.ADMIN
                  )
                ) {
                  Box(
                    modifier = Modifier
                      .size(32.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.surfaceVariant)
                      .pressable(onClick = { editingTitle = true }),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text("✏️", fontSize = 14.sp)
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text("🔒", fontSize = 14.sp)
              Text(
                "${members.size} members • encrypted",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }

      // Members section
      item {
        Text(
          "MEMBERS",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
      }

      items(members) { member ->
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 1.dp,
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                member.userId.takeLast(2).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                "User ${member.userId.takeLast(4)}",
                style = MaterialTheme.typography.titleMedium,
              )
              Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                      when (member.role) {
                        dev.hyo.deepline.shared.model.GroupRole.OWNER -> MaterialTheme.colorScheme.primary
                        dev.hyo.deepline.shared.model.GroupRole.ADMIN -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                      }
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                  Text(
                    member.role.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (member.role) {
                      dev.hyo.deepline.shared.model.GroupRole.OWNER -> MaterialTheme.colorScheme.onPrimary
                      dev.hyo.deepline.shared.model.GroupRole.ADMIN -> MaterialTheme.colorScheme.onTertiary
                      else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                  )
                }
              }
            }
            // Remove button (only for OWNER/ADMIN and not for OWNER members)
            if (myRole in setOf(
                dev.hyo.deepline.shared.model.GroupRole.OWNER,
                dev.hyo.deepline.shared.model.GroupRole.ADMIN
              ) &&
              member.role != dev.hyo.deepline.shared.model.GroupRole.OWNER &&
              (myRole == dev.hyo.deepline.shared.model.GroupRole.OWNER ||
                member.role == dev.hyo.deepline.shared.model.GroupRole.MEMBER)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.errorContainer)
                  .pressable(onClick = { onRemoveMember(member.userId) }),
                contentAlignment = Alignment.Center,
              ) {
                Text("✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.onErrorContainer)
              }
            }
          }
        }
      }

      // Leave group button
      item {
        Spacer(modifier = Modifier.height(16.dp))
        Button(
          onClick = onLeave,
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
          ),
        ) {
          Text(
            "Leave Group",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}
