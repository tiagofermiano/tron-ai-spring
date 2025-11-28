@Service
@RequiredArgsConstructor
public class TronAiService {

    private final OpenAiChatModel modelo;

    public String decidirJogada(int[][] grid, int bx, int by, int px, int py) {

        String contexto = """
            Estado do jogo TRON:
            - Sua posição (IA): (%d,%d)
            - Posição do jogador: (%d,%d)
            - Grid 30x30: %s

            Regras:
            - Escolha um movimento: CIMA, BAIXO, ESQUERDA, DIREITA.
            - Não pode colidir com rastros.
            - Retorne APENAS a direção.
            """.formatted(bx, by, px, py, Arrays.deepToString(grid));

        ChatResponse resp = modelo.call(
            new Prompt(contexto)
        );

        return resp.getResult().getOutputText().trim().toUpperCase();
    }
}
