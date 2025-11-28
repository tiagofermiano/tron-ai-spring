@Service
@RequiredArgsConstructor
public class GameService {

    private final TronAiService ia;
    private final PartidaRepository partidaRepo;
    private final JogadaRepository jogadaRepo;

    private int[][] grid = new int[30][30];

    private int px = 5, py = 15;  // posição do player
    private int bx = 25, by = 15; // posição da IA
    private int turno = 0;

    public EstadoDTO estadoInicial() {
        grid = new int[30][30];
        px = 5; py = 15;
        bx = 25; by = 15;
        turno = 0;

        return new EstadoDTO(grid, px, py, bx, by, false, null);
    }

    public EstadoDTO turnoJogador(String direcao) {
        moverPlayer(direcao);

        if (colidiu(px, py)) {
            return new EstadoDTO(grid, px, py, bx, by, true, "IA");
        }

        String movimentoIa = ia.decidirJogada(grid, bx, by, px, py);
        moverIA(movimentoIa);

        if (colidiu(bx, by)) {
            return new EstadoDTO(grid, px, py, bx, by, true, "PLAYER");
        }

        return new EstadoDTO(grid, px, py, bx, by, false, null);
    }

    private void moverPlayer(String dir) { /* implementaremos */ }
    private void moverIA(String dir) { /* implementaremos */ }
    private boolean colidiu(int x, int y) { return grid[y][x] != 0; }
}
