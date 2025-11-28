// tron.js – movimento contínuo, IA assíncrona (sem travar) + overlay + histórico

const GRID_SIZE = 30;
const CELL_SIZE = 18;

const EMPTY = 0;
const PLAYER = 1; // azul
const BOT = 2;    // rosa

const DIRECOES = ["UP", "DOWN", "LEFT", "RIGHT"];

const KEYMAP = {
    ArrowUp: "UP",
    ArrowDown: "DOWN",
    ArrowLeft: "LEFT",
    ArrowRight: "RIGHT",
    w: "UP",
    s: "DOWN",
    a: "LEFT",
    d: "RIGHT",
    W: "UP",
    S: "DOWN",
    A: "LEFT",
    D: "RIGHT"
};

const canvas = document.getElementById("tron-canvas");
const ctx = canvas.getContext("2d");
canvas.width = GRID_SIZE * CELL_SIZE;
canvas.height = GRID_SIZE * CELL_SIZE;

const statusLabel   = document.getElementById("status-label");
const btnNovoJogo   = document.getElementById("btn-start");

// elementos do overlay
const overlay         = document.getElementById("game-overlay");
const overlayTitle    = document.getElementById("overlay-title");
const overlaySubtitle = document.getElementById("overlay-subtitle");
const overlayRestart  = document.getElementById("overlay-restart");
const overlayMenu     = document.getElementById("overlay-menu");

// ---- ESTADO DO JOGO ----
let grid;
let player;
let bot;

let currentPlayerDirection = null;
let currentBotDirection    = "LEFT"; // direção inicial do bot

let gameStarted = false;
let gameOver = false;
let winner = null;
let turnCount = 0;

let gameLoopId = null;

// IA assíncrona (para não travar o loop)
let iaRequestInFlight = false;
let iaTurnCounter = 0;

// velocidade das motos em ms (quanto menor, mais rápido/suave)
const TICK_MS = 70;

// IA decide nova direção a cada N turnos
const IA_DECISION_INTERVAL_TURNS = 3;

// ---- FUNÇÕES BÁSICAS ----
function criarGridVazio() {
    const g = [];
    for (let y = 0; y < GRID_SIZE; y++) {
        const row = [];
        for (let x = 0; x < GRID_SIZE; x++) {
            row.push(EMPTY);
        }
        g.push(row);
    }
    return g;
}

function updateStatus(text) {
    if (statusLabel) {
        statusLabel.textContent = text;
    }
}

function drawBoard() {
    // fundo
    ctx.fillStyle = "#050816";
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // grid
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
                ctx.fillRect(
                    x * CELL_SIZE + 1,
                    y * CELL_SIZE + 1,
                    CELL_SIZE - 2,
                    CELL_SIZE - 2
                );
            } else if (grid[y][x] === BOT) {
                ctx.fillStyle = "#ff2fb2";
                ctx.fillRect(
                    x * CELL_SIZE + 1,
                    y * CELL_SIZE + 1,
                    CELL_SIZE - 2,
                    CELL_SIZE - 2
                );
            }
        }
    }

    // motos destacadas
    if (player) {
        ctx.fillStyle = "#7bfffa";
        ctx.fillRect(
            player.x * CELL_SIZE + 2,
            player.y * CELL_SIZE + 2,
            CELL_SIZE - 4,
            CELL_SIZE - 4
        );
    }
    if (bot) {
        ctx.fillStyle = "#ff76d6";
        ctx.fillRect(
            bot.x * CELL_SIZE + 2,
            bot.y * CELL_SIZE + 2,
            CELL_SIZE - 4,
            CELL_SIZE - 4
        );
    }
}

// impede virar 180° direto
function isOpposite(a, b) {
    if (!a || !b) return false;
    return (
        (a === "UP"    && b === "DOWN") ||
        (a === "DOWN"  && b === "UP")   ||
        (a === "LEFT"  && b === "RIGHT")||
        (a === "RIGHT" && b === "LEFT")
    );
}

function moveBike(bike, dir, type) {
    let dx = 0, dy = 0;
    if (dir === "UP")    dy = -1;
    if (dir === "DOWN")  dy = 1;
    if (dir === "LEFT")  dx = -1;
    if (dir === "RIGHT") dx = 1;

    const nx = bike.x + dx;
    const ny = bike.y + dy;

    // colisão com borda ou trilha
    if (
        nx < 0 || nx >= GRID_SIZE ||
        ny < 0 || ny >= GRID_SIZE ||
        grid[ny][nx] !== EMPTY
    ) {
        console.log(`Colisão de ${type === PLAYER ? "PLAYER" : "BOT"} em (${nx}, ${ny})`);
        if (type === PLAYER && winner == null) {
            winner = "BOT";
        } else if (type === BOT && winner == null) {
            winner = "PLAYER";
        }
        gameOver = true;
        return false;
    }

    bike.x = nx;
    bike.y = ny;
    grid[ny][nx] = type;
    return true;
}

// snapshot enviado pra IA
function snapshotEstado() {
    const occupied = [];
    for (let y = 0; y < GRID_SIZE; y++) {
        for (let x = 0; x < GRID_SIZE; x++) {
            if (grid[y][x] !== EMPTY) {
                occupied.push({ x, y });
            }
        }
    }

    return {
        boardSize: GRID_SIZE,
        playerX: player.x,
        playerY: player.y,
        botX: bot.x,
        botY: bot.y,
        occupied: occupied
    };
}

function randomDirection() {
    return DIRECOES[Math.floor(Math.random() * DIRECOES.length)];
}

// ---- CHAMADAS BACKEND ----
function pedirMovimentoIAAsync() {
    if (iaRequestInFlight) return;
    iaRequestInFlight = true;

    const payload = snapshotEstado();

    fetch("/api/ia/movimento", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    })
        .then(resp => {
            if (!resp.ok) {
                console.error("Erro ao chamar IA:", resp.status);
                return null;
            }
            return resp.text();
        })
        .then(texto => {
            if (!texto) return;
            const upper = texto.trim().toUpperCase();
            for (const d of DIRECOES) {
                if (upper.startsWith(d)) {
                    currentBotDirection = d;
                    break;
                }
            }
        })
        .catch(err => {
            console.error("Erro fetch IA:", err);
        })
        .finally(() => {
            iaRequestInFlight = false;
        });
}

function registrarResultado() {
    if (!winner) return;

    // fire-and-forget, não bloqueia o jogo
    fetch("/api/partidas/fim", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            vencedor: winner,
            turnos: turnCount
        })
    }).catch(e => console.error("Erro ao registrar partida:", e));
}

// ---- OVERLAY ----
function showOverlay(titulo, detalhe) {
    if (!overlay) return;
    overlayTitle.textContent = titulo;
    overlaySubtitle.textContent = detalhe || "";
    overlay.classList.remove("hidden");
}

function hideOverlay() {
    if (!overlay) return;
    overlay.classList.add("hidden");
}

// ---- LOOP DO JOGO ----
function gameTick() {
    if (gameOver || !gameStarted) return;
    if (!currentPlayerDirection) return;

    turnCount++;

    // 1) move jogador
    const okPlayer = moveBike(player, currentPlayerDirection, PLAYER);
    if (!okPlayer || gameOver) {
        gameOver = true;
        const msg = `Bot venceu em ${turnCount} turnos.`;
        showOverlay("FIM DE JOGO", msg);
        registrarResultado();
        drawBoard();
        stopGameLoop();
        return;
    }

    // 2) IA: decide nova direção de tempo em tempo sem travar o tick
    iaTurnCounter++;
    if (iaTurnCounter >= IA_DECISION_INTERVAL_TURNS) {
        iaTurnCounter = 0;
        pedirMovimentoIAAsync();
    }

    const okBot = moveBike(bot, currentBotDirection, BOT);
    if (!okBot || gameOver) {
        gameOver = true;
        const msg = `Você venceu! em ${turnCount} turnos.`;
        showOverlay("FIM DE JOGO", msg);
        registrarResultado();
        drawBoard();
        stopGameLoop();
        return;
    }

    // 3) seguir o baile
    drawBoard();
    updateStatus(`Em jogo • Turno #${turnCount}`);
}

function startGameLoop() {
    if (gameLoopId !== null) return; // já rodando
    gameLoopId = setInterval(gameTick, TICK_MS);
}

function stopGameLoop() {
    if (gameLoopId !== null) {
        clearInterval(gameLoopId);
        gameLoopId = null;
    }
}

// ---- RESET / NOVO JOGO ----
function resetGame() {
    stopGameLoop();
    hideOverlay();

    grid = criarGridVazio();

    // posições iniciais
    player = { x: 8, y: Math.floor(GRID_SIZE / 2) };
    bot    = { x: GRID_SIZE - 9, y: Math.floor(GRID_SIZE / 2) };

    grid[player.y][player.x] = PLAYER;
    grid[bot.y][bot.x] = BOT;

    currentPlayerDirection = null;
    currentBotDirection    = "LEFT";

    gameStarted = false;
    gameOver = false;
    winner = null;
    turnCount = 0;
    iaRequestInFlight = false;
    iaTurnCounter = 0;

    updateStatus("Pressione uma direção (← ↑ → ↓ ou WASD) para começar.");
    drawBoard();
}

// ---- EVENTOS ----
document.addEventListener("keydown", (e) => {
    if (gameOver) return;

    const key = e.key;
    const mapped = KEYMAP[key] || KEYMAP[key.toLowerCase()];
    if (!mapped) return;

    // primeiro comando: seta direção e começa o loop
    if (!gameStarted) {
        currentPlayerDirection = mapped;
        gameStarted = true;
        startGameLoop();
        return;
    }

    // depois de começar: só muda direção
    if (isOpposite(mapped, currentPlayerDirection)) {
        return; // ignora giro de 180°
    }

    currentPlayerDirection = mapped;
});

if (btnNovoJogo) {
    btnNovoJogo.addEventListener("click", () => {
        resetGame();
    });
}

if (overlayRestart) {
    overlayRestart.addEventListener("click", () => {
        resetGame();
    });
}

if (overlayMenu) {
    overlayMenu.addEventListener("click", () => {
        window.location.href = "/";
    });
}

// inicia com o tabuleiro desenhado aguardando primeira tecla
resetGame();
