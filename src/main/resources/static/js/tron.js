const canvas = document.getElementById('tronCanvas');
const ctx = canvas.getContext('2d');
const CELL = 20;

function desenhar(grid) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    for (let y = 0; y < 30; y++) {
        for (let x = 0; x < 30; x++) {

            if (grid[y][x] === 1) ctx.fillStyle = '#00bfff';      // player
            else if (grid[y][x] === 2) ctx.fillStyle = '#ff0066'; // IA
            else ctx.fillStyle = '#000000';

            ctx.fillRect(x * CELL, y * CELL, CELL, CELL);
        }
    }
}
