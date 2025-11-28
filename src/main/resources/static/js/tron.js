// tron.js — versão integrada com IA Gemini + histórico de jogadas
// compatível com o novo backend (partidaId + estado completo).

const GRID_SIZE = 30;
const CELL_SIZE = 18;

const EMPTY = 0;
const PLAYER = 1;
const BOT = 2;

const DIRECOES = ["UP", "DOWN", "LEFT", "RIGHT"];

let partidaId = null;

// Keys
const KEYMAP = {
    ArrowUp: "UP",
    ArrowDown: "DOWN",
    ArrowLeft: "LEFT",
    ArrowRight: "RIGHT",
    w: "UP", W: "UP",
    s: "DOWN", S: "DOWN",
    a: "LEFT", A: "LEFT",
    d: "RIGHT", D: "RIGHT"
};

// Canvas
const canvas = document.getElementById("tron-canvas");
const ctx = canvas.getContext("2d");
canvas.width = GRID_SIZE * CELL_SIZE;
canvas.height = GRID_SIZE * CELL_SIZE;

// UI
const statusLabel = document.getElementById("status-label");
const btnNovoJogo = document.getElementById("btn-start");
const overlay = document.getElementById("game-overlay");
const overlayTitle = document.getElementById("overlay-title");
const overlaySubtitle = document.getElementById("overlay-subtitle");
const overlayRestart = document.getElementById("overlay-restart");
const overlayMenu = document.getElementById("overlay-menu");

// Estado do jogo
let grid;
let player;
let bot;

let currentPlayerDirection = null;
let currentBotDirection = "LEFT";

let gameStarted = false;
let gameOver = false;
let winner = null;
let turnCount = 0;

let gameLoopId = null;

// IA async
let iaRequestInFlight = false;
let iaTurnCounter = 0;

const TICK_MS = 70;
const IA_DECISION_INTERVAL_TURNS = 2;

// -----------------------------
// BACKEND: cria nova partida
// -----------------------------
async function criarPartidaNoBackend() {
    const resp = await fetch("/api/partidas/nova", {
        method: "POST"
    });
    partidaId = await resp.json();
}

// -----------------------------
// UTILITÁRIOS DO TABULEIRO
// -----------------------------
function criarGridVazio() {
    const arr = [];
    for (let y = 0; y < GRID_SIZE; y++) {
        const row = [];
        for (let x = 0; x < GRID_SIZE; x++) row.push(EMPTY);
        arr.push(row);
    }
    return arr;
}

function updateStatus(txt) {
    statusLabel.textContent = txt;
}

// Desenho
function drawBoard() {
    ctx.fillStyle = "#050816";
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    ctx.strokeStyle = "#171c3b";
    ctx.lineWidth = 1;

    for (let i = 0; i <= GRID_SIZE; i++) {
        const p = i * CELL_SIZE;

        ctx.beginPath();
        ctx.moveTo(p, 0);
        ctx.lineTo(p, canvas.height);
        ctx.stroke();

        ctx.beginPath();
        ctx.moveTo(0, p);
        ctx.lineTo(canvas.width, p);
        ctx.stroke();
    }

    // trilhas
    for (let y = 0; y < GRID_SIZE; y++) {
        for (let x = 0; x < GRID_SIZE; x++) {
            if (grid[y][x] === PLAYER) {
                ctx.fillStyle = "#26ffe6";
                ctx.fillRect(x * CELL_SIZE + 1, y * CELL_SIZE + 1, CELL_SIZE - 2, CELL_SIZE - 2);
            }
            if (grid[y][x] === BOT) {
                ctx.fillStyle = "#ff2fb2";
                ctx.fillRect(x * CELL_SIZE + 1, y * CELL_SIZE + 1, CELL_SIZE - 2, CELL_SIZE - 2);
            }
        }
    }

    // moto player
    ctx.fillStyle = "#7bfffa";
    ctx.fillRect(player.x * CELL_SIZE + 2, player.y * CELL_SIZE + 2, CELL_SIZE - 4, CELL_SIZE - 4);

    // moto bot
    ctx.fillStyle = "#ff76d6";
    ctx.fillRect(bot.x * CELL_SIZE + 2, bot.y * CELL_SIZE + 2, CELL_SIZE - 4, CELL_SIZE - 4);
}

// movimento
function isOpposite(a, b) {
    if (!a || !b) return false;
    return (
        (a === "UP" && b === "DOWN") ||
        (a === "DOWN" && b === "UP") ||
        (a === "LEFT" && b === "RIGHT") ||
        (a === "RIGHT" && b === "LEFT")
    );
}

function moveBike(bike, dir, type) {
    let dx = 0, dy = 0;
    if (dir === "UP") dy = -1;
    if (dir === "DOWN") dy = 1;
    if (dir === "LEFT") dx = -1;
    if (dir === "RIGHT") dx = 1;

    const nx = bike.x + dx;
    const ny = bike.y + dy;

    // colisão
    if (
        nx < 0 || nx >= GRID_SIZE ||
        ny < 0 || ny >= GRID_SIZE ||
        grid[ny][nx] !== EMPTY
    ) {
        if (type === PLAYER) winner = "BOT";
        else winner = "PLAYER";
        gameOver = true;
        return false;
    }

    bike.x = nx;
    bike.y = ny;
    grid[ny][nx] = type;

    return true;
}

// snapshot para backend
function snapshotEstado() {
    const occ = [];
    for (let y = 0; y < GRID_SIZE; y++) {
        for (let x = 0; x < GRID_SIZE; x++) {
            if (grid[y][x] !== EMPTY) occ.push({ x, y });
        }
    }
    return {
        boardSize: GRID_SIZE,
        playerX: player.x,
        playerY: player.y,
        botX: bot.x,
        botY: bot.y,
        turno: turnCount,
        botDirection: currentBotDirection ? currentBotDirection : null,
        occupied: occ
    };
}


// -----------------------------
// IA (NEW BACKEND FORMAT)
// -----------------------------
async function pedirMovimentoIAAsync() {
    if (iaRequestInFlight) return;
    iaRequestInFlight = true;

    const estado = snapshotEstado();

    try {
        const resp = await fetch("/api/ia/movimento", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                partidaId: partidaId,
                estado: estado
            })
        });

        if (!resp.ok) {
            console.error("Erro IA:", resp.status);
            return;
        }

        const texto = await resp.text();
        const dir = texto.trim().toUpperCase();

        // impede o bot de virar "de costas"
        if (DIRECOES.includes(dir) && !isOpposite(dir, currentBotDirection)) {
            currentBotDirection = dir;
        }
    } catch (e) {
        console.error("Erro IA:", e);
    } finally {
        iaRequestInFlight = false;
    }
}

// -----------------------------
// REGISTRAR FIM DE PARTIDA
// -----------------------------
async function registrarResultado() {
    if (!winner || !partidaId) return;

    try {
        await fetch("/api/partidas/fim", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                partidaId: partidaId,
                vencedor: winner,
                turnos: turnCount
            })
        });
    } catch (e) {
        console.error("Erro registrar fim:", e);
    }
}

// -----------------------------
// OVERLAY
// -----------------------------
function showOverlay(t, subt) {
    overlayTitle.textContent = t;
    overlaySubtitle.textContent = subt;
    overlay.classList.remove("hidden");
}

function hideOverlay() {
    overlay.classList.add("hidden");
}

// -----------------------------
// GAME LOOP
// -----------------------------
function gameTick() {
    if (gameOver || !gameStarted) return;
    if (!currentPlayerDirection) return;

    turnCount++;

    // player
    const okPlayer = moveBike(player, currentPlayerDirection, PLAYER);
    if (!okPlayer) {
        showOverlay("FIM DE JOGO", `Bot venceu em ${turnCount} turnos.`);
        registrarResultado();
        stopGameLoop();
        drawBoard();
        return;
    }

    // IA
    iaTurnCounter++;
    if (iaTurnCounter >= IA_DECISION_INTERVAL_TURNS) {
        iaTurnCounter = 0;
        pedirMovimentoIAAsync();
    }

    // bot
    const okBot = moveBike(bot, currentBotDirection, BOT);
    if (!okBot) {
        showOverlay("FIM DE JOGO", `Você venceu! em ${turnCount} turnos.`);
        registrarResultado();
        stopGameLoop();
        drawBoard();
        return;
    }

    drawBoard();
    updateStatus(`Em jogo • Turno #${turnCount}`);
}

function startGameLoop() {
    if (gameLoopId == null) gameLoopId = setInterval(gameTick, TICK_MS);
}

function stopGameLoop() {
    if (gameLoopId != null) {
        clearInterval(gameLoopId);
        gameLoopId = null;
    }
}

// -----------------------------
// RESET GAME (AGORA CRIA PARTIDA NO BACKEND)
// -----------------------------
async function resetGame() {
    stopGameLoop();
    hideOverlay();

    await criarPartidaNoBackend(); // <-- agora cria partida no banco!

    grid = criarGridVazio();

    player = { x: 8, y: Math.floor(GRID_SIZE / 2) };
    bot = { x: GRID_SIZE - 9, y: Math.floor(GRID_SIZE / 2) };

    grid[player.y][player.x] = PLAYER;
    grid[bot.y][bot.x] = BOT;

    currentPlayerDirection = null;
    currentBotDirection = "LEFT";

    gameStarted = false;
    gameOver = false;
    winner = null;
    turnCount = 0;

    iaTurnCounter = 0;
    iaRequestInFlight = false;

    updateStatus("Pressione uma direção para começar.");
    drawBoard();
}

// -----------------------------
// EVENTOS
// -----------------------------
document.addEventListener("keydown", (e) => {
    if (gameOver) return;

    const map = KEYMAP[e.key];
    if (!map) return;

    if (!gameStarted) {
        currentPlayerDirection = map;
        gameStarted = true;
        startGameLoop();
        return;
    }

    if (isOpposite(map, currentPlayerDirection)) return;

    currentPlayerDirection = map;
});

btnNovoJogo?.addEventListener("click", resetGame);
overlayRestart?.addEventListener("click", resetGame);
overlayMenu?.addEventListener("click", () => window.location.href = "/");

// inicializa
resetGame();
