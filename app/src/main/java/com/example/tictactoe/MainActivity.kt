package com.example.tictactoe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Game Domain & State Models
// ---------------------------------------------------------------------------

enum class AppScreen {
    HOME,          // Screen 1: Select 1 Player (with AI) or 2 Player (Pass and Play)
    PLAYER_SETUP,  // Screen 2: Enter Player Name(s), AI name fixed as Tony
    GAME_BOARD     // Screen 3: Game Board with Top Left Back, Top Right Reset, Bottom Left Hint, Bottom Right Stats
}

enum class GameMode {
    ONE_PLAYER, // 1 Player vs Tony (AI)
    TWO_PLAYER  // 2 Player (Pass and Play)
}

enum class Player {
    X, O
}

sealed class GameStatus {
    data class Ongoing(val currentTurn: Player) : GameStatus()
    data object BotThinking : GameStatus()
    data class Won(val winner: Player, val winningIndices: List<Int>) : GameStatus()
    data object Draw : GameStatus()
}

// In-memory match history record (lost when app closes)
data class MatchStat(
    val matchNumber: Int,
    val mode: GameMode,
    val player1Name: String,
    val player2Name: String,
    val winnerName: String?, // null if draw
    val loserName: String?,  // null if draw
    val isDraw: Boolean,
    val totalSteps: Int,
    val player1Steps: Int,
    val player2Steps: Int
)

// ---------------------------------------------------------------------------
// MainActivity Entry Point
// ---------------------------------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TicTacToeTheme {
                TicTacToeAppRoot()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Material 3 Theme Definition
// ---------------------------------------------------------------------------

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1), // Modern Indigo
    onPrimary = Color.White,
    secondary = Color(0xFFEC4899), // Pink / Coral
    onSecondary = Color.White,
    tertiary = Color(0xFF10B981), // Emerald
    background = Color(0xFF0F172A), // Slate 900
    surface = Color(0xFF1E293B), // Slate 800
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    secondary = Color(0xFFDB2777),
    onSecondary = Color.White,
    tertiary = Color(0xFF059669),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

@Composable
fun TicTacToeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}

// ---------------------------------------------------------------------------
// Application Root & Navigation State Container
// ---------------------------------------------------------------------------

@Composable
fun TicTacToeAppRoot() {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var selectedMode by remember { mutableStateOf(GameMode.ONE_PLAYER) }

    // Player Names (Tony is fixed for AI opponent)
    var player1Name by remember { mutableStateOf("Player 1") }
    var player2Name by remember { mutableStateOf("Player 2") }
    val aiFixedName = "Tony"

    // In-memory match stats list (persists during app session only)
    val sessionMatchStats = remember { mutableStateListOf<MatchStat>() }

    // Intercept back button when inside setup or game screen
    BackHandler(enabled = currentScreen != AppScreen.HOME) {
        currentScreen = when (currentScreen) {
            AppScreen.GAME_BOARD -> AppScreen.PLAYER_SETUP
            AppScreen.PLAYER_SETUP -> AppScreen.HOME
            AppScreen.HOME -> AppScreen.HOME
        }
    }

    when (currentScreen) {
        AppScreen.HOME -> {
            HomeScreen(
                onSelectMode = { mode ->
                    selectedMode = mode
                    if (mode == GameMode.ONE_PLAYER) {
                        player2Name = aiFixedName
                    } else if (player2Name == aiFixedName) {
                        player2Name = "Player 2"
                    }
                    currentScreen = AppScreen.PLAYER_SETUP
                }
            )
        }
        AppScreen.PLAYER_SETUP -> {
            PlayerSetupScreen(
                mode = selectedMode,
                player1Name = player1Name,
                player2Name = player2Name,
                aiFixedName = aiFixedName,
                onPlayer1NameChange = { player1Name = it },
                onPlayer2NameChange = { player2Name = it },
                onBack = { currentScreen = AppScreen.HOME },
                onStartGame = {
                    val finalP1 = player1Name.trim().ifEmpty { "Player 1" }
                    val finalP2 = if (selectedMode == GameMode.ONE_PLAYER) {
                        aiFixedName
                    } else {
                        player2Name.trim().ifEmpty { "Player 2" }
                    }
                    player1Name = finalP1
                    player2Name = finalP2
                    currentScreen = AppScreen.GAME_BOARD
                }
            )
        }
        AppScreen.GAME_BOARD -> {
            GameBoardScreen(
                mode = selectedMode,
                player1Name = player1Name,
                player2Name = if (selectedMode == GameMode.ONE_PLAYER) aiFixedName else player2Name,
                sessionMatchStats = sessionMatchStats,
                onBack = { currentScreen = AppScreen.PLAYER_SETUP }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// SCREEN 1: Home Screen (Mode Selection)
// ---------------------------------------------------------------------------

@Composable
fun HomeScreen(
    onSelectMode: (GameMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Branding Banner
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "Tic Tac Toe Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Tic Tac Toe",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Select game mode to continue",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Option 1: 1 Player (with AI Tony)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectMode(GameMode.ONE_PLAYER) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "1 Player AI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "1 Player",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Play vs AI Opponent (Tony)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Select",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Option 2: 2 Player (Pass and Play)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectMode(GameMode.TWO_PLAYER) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "2 Player",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "2 Player",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pass & play with a friend",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Select",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SCREEN 2: Player Setup Screen (Tony is fixed for AI)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSetupScreen(
    mode: GameMode,
    player1Name: String,
    player2Name: String,
    aiFixedName: String,
    onPlayer1NameChange: (String) -> Unit,
    onPlayer2NameChange: (String) -> Unit,
    onBack: () -> Unit,
    onStartGame: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (mode == GameMode.ONE_PLAYER) "1 Player Setup" else "2 Player Setup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Enter Player Details",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (mode == GameMode.ONE_PLAYER)
                    "Enter your name. Your AI opponent's name is locked as Tony."
                else
                    "Enter names for both players to start match.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Player 1 Name Field (Plays 'X')
            OutlinedTextField(
                value = player1Name,
                onValueChange = onPlayer1NameChange,
                label = { Text("Player 1 Name (X)") },
                placeholder = { Text("e.g. Alex") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Player 1",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Player 2 / AI Tony Field (Plays 'O')
            if (mode == GameMode.ONE_PLAYER) {
                // Fixed AI Name Card: Tony
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = "Tony AI",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Opponent (O)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = aiFixedName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "AI (FIXED)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            } else {
                // Two Player Mode: Customizable Player 2 Name
                OutlinedTextField(
                    value = player2Name,
                    onValueChange = onPlayer2NameChange,
                    label = { Text("Player 2 Name (O)") },
                    placeholder = { Text("e.g. Jordan") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Player 2",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStartGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start Game"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start Game",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SCREEN 3: Game Board Screen
// - Top Left: Back button
// - Top Right: Reset button
// - Middle: Game board & Status banner
// - Bottom Left: "Hint" (Ask for suggestion) button with 5s cooldown timer
// - Bottom Right: "Check Stats" button
// ---------------------------------------------------------------------------

@Composable
fun GameBoardScreen(
    mode: GameMode,
    player1Name: String,
    player2Name: String,
    sessionMatchStats: MutableList<MatchStat>,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // 3x3 Board state
    var board by remember { mutableStateOf(List<Player?>(9) { null }) }
    var gameStatus by remember { mutableStateOf<GameStatus>(GameStatus.Ongoing(Player.X)) }

    // Step counters for current match
    var player1Steps by remember { mutableIntStateOf(0) }
    var player2Steps by remember { mutableIntStateOf(0) }

    // Hint state: index of hinted cell and 5-second countdown timer
    var hintedCellIndex by remember { mutableStateOf<Int?>(null) }
    var hintCooldownSeconds by remember { mutableIntStateOf(0) }
    var hintMessage by remember { mutableStateOf<String?>(null) }

    // Check stats modal visibility
    var showStatsDialog by remember { mutableStateOf(false) }

    // Board evaluation
    fun evaluateBoard(currentBoard: List<Player?>): Pair<Player?, List<Int>> {
        val winPatterns = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (pattern in winPatterns) {
            val (a, b, c) = pattern
            if (currentBoard[a] != null && currentBoard[a] == currentBoard[b] && currentBoard[a] == currentBoard[c]) {
                return Pair(currentBoard[a], pattern)
            }
        }
        return Pair(null, emptyList())
    }

    // AI logic for Tony
    fun calculateTonyMove(currentBoard: List<Player?>): Int {
        val availableSpots = currentBoard.indices.filter { currentBoard[it] == null }
        if (availableSpots.isEmpty()) return -1

        // 1. Can Tony ('O') win?
        for (spot in availableSpots) {
            val testBoard = currentBoard.toMutableList()
            testBoard[spot] = Player.O
            val (winner, _) = evaluateBoard(testBoard)
            if (winner == Player.O) return spot
        }

        // 2. Can Player 1 ('X') win? Block them!
        for (spot in availableSpots) {
            val testBoard = currentBoard.toMutableList()
            testBoard[spot] = Player.X
            val (winner, _) = evaluateBoard(testBoard)
            if (winner == Player.X) return spot
        }

        // 3. Center preference
        if (currentBoard[4] == null) return 4

        // 4. Strategic corner
        val corners = listOf(0, 2, 6, 8).filter { currentBoard[it] == null }
        if (corners.isNotEmpty()) return corners.random()

        return availableSpots.random()
    }

    // AI Hint calculator for the human player asking for advice
    fun calculateBestHintMove(currentBoard: List<Player?>, forPlayer: Player): Int {
        val availableSpots = currentBoard.indices.filter { currentBoard[it] == null }
        if (availableSpots.isEmpty()) return -1

        val opponent = if (forPlayer == Player.X) Player.O else Player.X

        // 1. Winning move
        for (spot in availableSpots) {
            val test = currentBoard.toMutableList()
            test[spot] = forPlayer
            val (winner, _) = evaluateBoard(test)
            if (winner == forPlayer) return spot
        }

        // 2. Blocking move
        for (spot in availableSpots) {
            val test = currentBoard.toMutableList()
            test[spot] = opponent
            val (winner, _) = evaluateBoard(test)
            if (winner == opponent) return spot
        }

        // 3. Center
        if (currentBoard[4] == null) return 4

        // 4. Corners
        val corners = listOf(0, 2, 6, 8).filter { currentBoard[it] == null }
        if (corners.isNotEmpty()) return corners.first()

        return availableSpots.first()
    }

    // Record completed match into session stats
    fun recordMatchOutcome(winner: Player?) {
        val totalSteps = player1Steps + player2Steps
        val matchNum = sessionMatchStats.size + 1
        if (winner != null) {
            val winnerName = if (winner == Player.X) player1Name else player2Name
            val loserName = if (winner == Player.X) player2Name else player1Name
            sessionMatchStats.add(
                MatchStat(
                    matchNumber = matchNum,
                    mode = mode,
                    player1Name = player1Name,
                    player2Name = player2Name,
                    winnerName = winnerName,
                    loserName = loserName,
                    isDraw = false,
                    totalSteps = totalSteps,
                    player1Steps = player1Steps,
                    player2Steps = player2Steps
                )
            )
        } else {
            sessionMatchStats.add(
                MatchStat(
                    matchNumber = matchNum,
                    mode = mode,
                    player1Name = player1Name,
                    player2Name = player2Name,
                    winnerName = null,
                    loserName = null,
                    isDraw = true,
                    totalSteps = totalSteps,
                    player1Steps = player1Steps,
                    player2Steps = player2Steps
                )
            )
        }
    }

    // Reset current board
    fun resetCurrentBoard() {
        board = List(9) { null }
        gameStatus = GameStatus.Ongoing(Player.X)
        player1Steps = 0
        player2Steps = 0
        hintedCellIndex = null
        hintMessage = null
    }

    // Trigger Tony's move with realistic delay
    fun triggerTonyTurn(boardAfterUser: List<Player?>) {
        coroutineScope.launch {
            gameStatus = GameStatus.BotThinking
            delay(650L) // Realistic simulated thinking delay

            val tonyMove = calculateTonyMove(boardAfterUser)
            if (tonyMove != -1) {
                val nextBoard = boardAfterUser.toMutableList()
                nextBoard[tonyMove] = Player.O
                board = nextBoard
                player2Steps++

                val (winner, winPattern) = evaluateBoard(nextBoard)
                if (winner != null) {
                    gameStatus = GameStatus.Won(winner, winPattern)
                    recordMatchOutcome(winner)
                } else if (nextBoard.none { it == null }) {
                    gameStatus = GameStatus.Draw
                    recordMatchOutcome(null)
                } else {
                    gameStatus = GameStatus.Ongoing(Player.X)
                }
            }
        }
    }

    // User taps on a board cell
    fun onCellClicked(index: Int) {
        if (board[index] != null) return
        val current = gameStatus
        if (current !is GameStatus.Ongoing) return

        val currentPlayer = current.currentTurn
        val updated = board.toMutableList()
        updated[index] = currentPlayer
        board = updated

        // Clear active hint when player moves
        hintedCellIndex = null
        hintMessage = null

        if (currentPlayer == Player.X) {
            player1Steps++
        } else {
            player2Steps++
        }

        val (winner, winPattern) = evaluateBoard(updated)
        if (winner != null) {
            gameStatus = GameStatus.Won(winner, winPattern)
            recordMatchOutcome(winner)
            return
        }

        if (updated.none { it == null }) {
            gameStatus = GameStatus.Draw
            recordMatchOutcome(null)
            return
        }

        if (mode == GameMode.TWO_PLAYER) {
            val nextTurn = if (currentPlayer == Player.X) Player.O else Player.X
            gameStatus = GameStatus.Ongoing(nextTurn)
        } else {
            // 1 Player: Player 1 moved, now Tony plays
            triggerTonyTurn(updated)
        }
    }

    // "Ask for suggestion" / Hint button handler with 5-second timer
    fun onHintClicked() {
        if (hintCooldownSeconds > 0) return
        val current = gameStatus
        if (current !is GameStatus.Ongoing) return

        val bestSpot = calculateBestHintMove(board, current.currentTurn)
        if (bestSpot != -1) {
            hintedCellIndex = bestSpot
            val row = (bestSpot / 3) + 1
            val col = (bestSpot % 3) + 1
            hintMessage = "Tony suggests: Row $row, Col $col"

            // Start 5-second cooldown timer
            coroutineScope.launch {
                hintCooldownSeconds = 5
                while (hintCooldownSeconds > 0) {
                    delay(1000L)
                    hintCooldownSeconds--
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---------------------------------------------------------------
            // Top Section: Back Button (Top Left) & Reset Button (Top Right)
            // ---------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top Left: Back Button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Center Title
                Text(
                    text = "$player1Name vs $player2Name",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Top Right: Reset Button
                IconButton(
                    onClick = { resetCurrentBoard() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Game",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ---------------------------------------------------------------
            // Middle Section: Status Banner & Game Board (3x3 Grid)
            // ---------------------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Status Banner
                StatusBanner(
                    gameStatus = gameStatus,
                    player1Name = player1Name,
                    player2Name = player2Name
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Optional Hint Message Banner
                if (hintMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = "Hint",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = hintMessage ?: "",
                                color = Color(0xFFFBBF24),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 3x3 Game Board Grid
                val winningIndices = (gameStatus as? GameStatus.Won)?.winningIndices ?: emptyList()
                BoardGrid(
                    board = board,
                    winningIndices = winningIndices,
                    hintedIndex = hintedCellIndex,
                    isInteractive = gameStatus is GameStatus.Ongoing,
                    onCellClick = { onCellClicked(it) }
                )
            }

            // ---------------------------------------------------------------
            // Bottom Section:
            // - Bottom Left: "Ask for suggestion" / Hint button (with 5s timer)
            // - Bottom Right: "Check Stats" button
            // ---------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bottom Left: Ask for suggestion (Hint) Button
                val isHintEnabled = gameStatus is GameStatus.Ongoing && hintCooldownSeconds == 0 && board.contains(null)
                Button(
                    onClick = { onHintClicked() },
                    enabled = isHintEnabled,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF59E0B),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFF59E0B).copy(alpha = 0.35f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Hint",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hintCooldownSeconds > 0) "Hint (${hintCooldownSeconds}s)" else "Hint",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Bottom Right: Check Stats Button
                Button(
                    onClick = { showStatsDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Stats",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Check Stats",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    // Stats Dialog (Shows session stats: matches, who won/lost, steps played)
    if (showStatsDialog) {
        SessionStatsDialog(
            statsList = sessionMatchStats,
            onDismiss = { showStatsDialog = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Status Banner Component
// ---------------------------------------------------------------------------

@Composable
fun StatusBanner(
    gameStatus: GameStatus,
    player1Name: String,
    player2Name: String
) {
    val (text, bg, fg, showSpinner) = when (gameStatus) {
        is GameStatus.Ongoing -> {
            val current = gameStatus.currentTurn
            val name = if (current == Player.X) "$player1Name's Turn (X)" else "$player2Name's Turn (O)"
            val bg = if (current == Player.X) Color(0xFF6366F1).copy(alpha = 0.15f) else Color(0xFFEC4899).copy(alpha = 0.15f)
            val fg = if (current == Player.X) Color(0xFF818CF8) else Color(0xFFF472B6)
            Quadruple(name, bg, fg, false)
        }
        is GameStatus.BotThinking -> {
            Quadruple(
                "$player2Name is thinking...",
                Color(0xFFEC4899).copy(alpha = 0.15f),
                Color(0xFFF472B6),
                true
            )
        }
        is GameStatus.Won -> {
            val winnerName = if (gameStatus.winner == Player.X) player1Name else player2Name
            Quadruple(
                "$winnerName Wins! 🎉",
                Color(0xFF10B981).copy(alpha = 0.2f),
                Color(0xFF34D399),
                false
            )
        }
        is GameStatus.Draw -> {
            Quadruple(
                "It's a Draw! 🤝",
                Color(0xFFF59E0B).copy(alpha = 0.2f),
                Color(0xFFFBBF24),
                false
            )
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = fg,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else if (gameStatus is GameStatus.Won) {
                Icon(
                    imageVector = Icons.Outlined.EmojiEvents,
                    contentDescription = "Won",
                    tint = fg,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = fg,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// ---------------------------------------------------------------------------
// 3x3 Reactive Board Grid
// ---------------------------------------------------------------------------

@Composable
fun BoardGrid(
    board: List<Player?>,
    winningIndices: List<Int>,
    hintedIndex: Int?,
    isInteractive: Boolean,
    onCellClick: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (row in 0..2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (col in 0..2) {
                        val index = row * 3 + col
                        BoardCell(
                            player = board[index],
                            isWinningCell = winningIndices.contains(index),
                            isHinted = hintedIndex == index,
                            enabled = isInteractive && board[index] == null,
                            modifier = Modifier.weight(1f),
                            onClick = { onCellClick(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoardCell(
    player: Player?,
    isWinningCell: Boolean,
    isHinted: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cellBgColor by animateColorAsState(
        targetValue = when {
            isWinningCell -> Color(0xFF10B981).copy(alpha = 0.25f)
            isHinted -> Color(0xFFF59E0B).copy(alpha = 0.25f)
            player != null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.background.copy(alpha = 0.6f)
        },
        animationSpec = tween(300),
        label = "cellBg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isWinningCell -> Color(0xFF10B981)
            isHinted -> Color(0xFFF59E0B)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        },
        animationSpec = tween(300),
        label = "cellBorder"
    )

    val scale by animateFloatAsState(
        targetValue = if (isWinningCell || isHinted) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "cellScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(cellBgColor)
            .border(
                width = if (isWinningCell || isHinted) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = player,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(120))
            },
            label = "cellMark"
        ) { targetPlayer ->
            when (targetPlayer) {
                Player.X -> MarkX()
                Player.O -> MarkO()
                null -> {
                    if (isHinted) {
                        Text(
                            text = "HINT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFF59E0B)
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Canvas-Rendered 'X' and 'O' Marks
// ---------------------------------------------------------------------------

@Composable
fun MarkX() {
    Canvas(modifier = Modifier.size(44.dp)) {
        val strokeWidth = 8.dp.toPx()
        val padding = 8.dp.toPx()
        val color = Color(0xFF6366F1) // Indigo Primary

        drawLine(
            color = color,
            start = Offset(padding, padding),
            end = Offset(size.width - padding, size.height - padding),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start = Offset(size.width - padding, padding),
            end = Offset(padding, size.height - padding),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun MarkO() {
    Canvas(modifier = Modifier.size(44.dp)) {
        val strokeWidth = 8.dp.toPx()
        val padding = 8.dp.toPx()
        val color = Color(0xFFEC4899) // Coral / Pink Secondary
        val radius = (size.minDimension / 2) - padding

        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

// ---------------------------------------------------------------------------
// Session Stats Modal Dialog (In-Memory Only)
// ---------------------------------------------------------------------------

@Composable
fun SessionStatsDialog(
    statsList: List<MatchStat>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "Stats",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Session Match Stats",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                Text(
                    text = "Stats recorded since opening the app. These are cleared once the app is closed.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (statsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matches played yet in this session.\\nComplete a match to see stats!",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(statsList) { match ->
                            MatchStatItemCard(match)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun MatchStatItemCard(match: MatchStat) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Match #${match.matchNumber} (${if (match.mode == GameMode.ONE_PLAYER) "vs Tony" else "2 Player"})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (match.isDraw) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Draw",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Winner: ${match.winnerName}",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = if (match.isDraw)
                    "Result: Tie match between ${match.player1Name} and ${match.player2Name}"
                else
                    "Winner: ${match.winnerName} | Loser: ${match.loserName}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
            )

            Text(
                text = "Total Steps: ${match.totalSteps} (${match.player1Steps} by ${match.player1Name}, ${match.player2Steps} by ${match.player2Name})",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Android Studio Koala Preview Canvas
// ---------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun TicTacToeScreenPreview() {
    TicTacToeTheme(darkTheme = true) {
        TicTacToeAppRoot()
    }
}
