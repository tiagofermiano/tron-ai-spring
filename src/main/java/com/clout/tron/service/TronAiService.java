package com.clout.tron.service;

import com.clout.tron.ai.GeminiService;
import com.clout.tron.dto.EstadoDTO;
import com.clout.tron.entity.Jogada;
import com.clout.tron.repository.JogadaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TronAiService {

    private final GeminiService geminiService;
    private final JogadaRepository jogadaRepository;
    private final ObjectMapper objectMapper;

    private static final List<String> VALID_DIRECTIONS =
            List.of("UP", "DOWN", "LEFT", "RIGHT");

    // cooldown pra não ficar levando 429 o tempo todo
    private volatile long geminiCooldownUntil = 0L;

    public String decidirMovimento(EstadoDTO estado) {

        // 1) Histórico “permanente” do banco → aprendizado leve
        List<Jogada> jogadas = jogadaRepository.findTop300ByOrderByIdDesc();
        Map<String, Double> scoreAprendizado = calcularScoreAprendizadoPorAcao(jogadas);
        String historicoResumo = montarResumoHistorico(jogadas, scoreAprendizado);

        long now = System.currentTimeMillis();

        // 2) Se Gemini está em cooldown → usa só cérebro local
        if (now < geminiCooldownUntil) {
            log.debug("Gemini em cooldown até {}. Usando fallback agressivo/aprendido.", geminiCooldownUntil);
            return decidirMovimentoFallbackAgressivo(estado, scoreAprendizado);
        }

        try {
            String estadoJson = objectMapper.writeValueAsString(estado);

            String prompt = """
Você é a IA controlando a moto ROSA no jogo TRON.

OBJETIVO:
- Eliminar o adversário (moto azul) o mais rápido possível.
- Jogar de forma AGRESSIVA, perseguindo e tentando encurralar o inimigo.
- Nunca "desistir": mesmo em situações ruins, busque sobreviver o máximo possível, pois o jogador pode errar.

REGRAS DE MOVIMENTO:
- A moto NÃO PODE inverter a direção imediatamente (sem andar de ré).
  Exemplos:
  - Se estiver indo para CIMA (UP), não pode ir para BAIXO (DOWN) no próximo movimento.
  - Se estiver indo para ESQUERDA (LEFT), não pode ir para DIREITA (RIGHT) no próximo movimento.
- Só é permitido:
  - continuar na mesma direção,
  - virar à esquerda,
  - virar à direita.
- NÃO escolha movimentos que causem colisão imediata com parede ou rastro.

ESTRATÉGIA:
- Prefira movimentos que:
  - mantenham você vivo por mais tempo,
  - aproveitem regiões com muito espaço livre,
  - aproximem você do jogador sem se jogar em becos suicidas,
  - respeitem as direções que historicamente trazem MAIS vitórias.

APRENDIZADO (calculado no backend a partir do banco de dados):
%s

ESTADO ATUAL DO JOGO (JSON):
%s

IMPORTANTE:
- Responda APENAS com uma palavra: UP, DOWN, LEFT ou RIGHT.
- NÃO escreva comentários, explicações ou frases extras.

Qual é o movimento mais agressivo e inteligente agora (UP, DOWN, LEFT ou RIGHT)?
""".formatted(historicoResumo, estadoJson);

            log.debug("Prompt Gemini:\n{}", prompt);

            String resposta = geminiService.gerarMovimento(prompt);
            if (resposta == null) {
                throw new IllegalStateException("Resposta nula do Gemini");
            }

            resposta = resposta.trim().toUpperCase();
            log.debug("Resposta Gemini (raw): {}", resposta);

            if (!VALID_DIRECTIONS.contains(resposta)) {
                log.warn("Direção inválida do Gemini: {}. Usando fallback agressivo/aprendido.", resposta);
                return decidirMovimentoFallbackAgressivo(estado, scoreAprendizado);
            }

            if (!isDirecaoSegura(estado, resposta)) {
                log.warn("Direção do Gemini não segura (colisão/imediata ou ré): {}. Usando fallback agressivo/aprendido.", resposta);
                return decidirMovimentoFallbackAgressivo(estado, scoreAprendizado);
            }

            return resposta;

        } catch (NonTransientAiException e) {
            log.error("Erro de IA (Spring AI). Usando fallback agressivo/aprendido.", e);
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                long cooldown = 30_000L;
                geminiCooldownUntil = System.currentTimeMillis() + cooldown;
                log.warn("Recebido 429 do Gemini. Ativando cooldown por {} ms.", cooldown);
            }
            return decidirMovimentoFallbackAgressivo(estado, scoreAprendizado);

        } catch (Exception e) {
            log.error("Erro ao decidir movimento com Gemini. Usando fallback agressivo/aprendido.", e);
            return decidirMovimentoFallbackAgressivo(estado, scoreAprendizado);
        }
    }

    // ========================= REGRAS DE MOVIMENTO =========================

    private boolean isOpposite(String a, String b) {
        if (a == null || b == null) return false;
        return (a.equals("UP") && b.equals("DOWN")) ||
               (a.equals("DOWN") && b.equals("UP")) ||
               (a.equals("LEFT") && b.equals("RIGHT")) ||
               (a.equals("RIGHT") && b.equals("LEFT"));
    }

    private boolean[][] montarMatrizOcupacao(EstadoDTO estado) {
        int n = estado.getBoardSize();
        boolean[][] ocupado = new boolean[n][n];

        if (estado.getOccupied() != null) {
            for (EstadoDTO.Posicao p : estado.getOccupied()) {
                if (p.getX() >= 0 && p.getX() < n && p.getY() >= 0 && p.getY() < n) {
                    ocupado[p.getY()][p.getX()] = true;
                }
            }
        }
        return ocupado;
    }

    private boolean isDirecaoSegura(EstadoDTO estado, String dir) {
        int n = estado.getBoardSize();
        boolean[][] ocupado = montarMatrizOcupacao(estado);

        int bx = estado.getBotX();
        int by = estado.getBotY();
        String currentDir = estado.getBotDirection();

        // não pode andar de ré
        if (isOpposite(dir, currentDir)) {
            return false;
        }

        int nx = bx;
        int ny = by;

        switch (dir) {
            case "UP" -> ny--;
            case "DOWN" -> ny++;
            case "LEFT" -> nx--;
            case "RIGHT" -> nx++;
        }

        if (nx < 0 || nx >= n || ny < 0 || ny >= n) {
            return false;
        }

        return !ocupado[ny][nx];
    }

    // ========================= APRENDIZADO (RL LEVE) =========================

    private Map<String, Double> calcularScoreAprendizadoPorAcao(List<Jogada> jogadas) {
        Map<String, Long> winsPorAcao = jogadas.stream()
                .filter(j -> "WIN".equalsIgnoreCase(j.getResultado()))
                .collect(Collectors.groupingBy(Jogada::getAcao, Collectors.counting()));

        Map<String, Long> lossPorAcao = jogadas.stream()
                .filter(j -> "LOSE".equalsIgnoreCase(j.getResultado()))
                .collect(Collectors.groupingBy(Jogada::getAcao, Collectors.counting()));

        Map<String, Double> score = new HashMap<>();
        for (String dir : VALID_DIRECTIONS) {
            long w = winsPorAcao.getOrDefault(dir, 0L);
            long l = lossPorAcao.getOrDefault(dir, 0L);
            double s = (double) (w - l) / (w + l + 1);
            score.put(dir, s);
        }
        return score;
    }

    // ========================= FALLBACK AGRESSIVO + “VER O FUTURO” =========================

    private String decidirMovimentoFallbackAgressivo(EstadoDTO estado, Map<String, Double> scoreAprendizado) {
        int n = estado.getBoardSize();
        boolean[][] ocupado = montarMatrizOcupacao(estado);

        int bx = estado.getBotX();
        int by = estado.getBotY();
        int px = estado.getPlayerX();
        int py = estado.getPlayerY();
        String currentDir = estado.getBotDirection();

        Map<String, Double> scorePorDirecao = new HashMap<>();

        for (String dir : VALID_DIRECTIONS) {
            // regra: não andar de ré
            if (isOpposite(dir, currentDir)) continue;

            int nx = bx;
            int ny = by;

            switch (dir) {
                case "UP" -> ny--;
                case "DOWN" -> ny++;
                case "LEFT" -> nx--;
                case "RIGHT" -> nx++;
            }

            // se sair da grade, descarta
            if (nx < 0 || nx >= n || ny < 0 || ny >= n) continue;

            // se já está ocupado, ainda consideramos como “não seguro”,
            // mas vamos deixar para o caso extremo (ver mais abaixo)
            if (ocupado[ny][nx]) continue;

            int area = floodFillArea(nx, ny, ocupado);
            int distPlayer = Math.abs(nx - px) + Math.abs(ny - py);
            double learned = scoreAprendizado.getOrDefault(dir, 0.0);

            // lookahead: quanto essa direção permite sobreviver vários passos
            double survival = simularSobrevivencia(nx, ny, dir, ocupado, 7);

            // rebalanceado: mais peso em sobreviver e ter espaço,
            // um pouco menos em encurtar distancia pra não se matar à toa
            double score = area * 2.0     // espaço importa bastante
                         + survival * 10.0 // sobreviver vários passos importa MUITO
                         - distPlayer * 1.8 // agressivo, mas menos kamikaze
                         + learned * 8.0;   // aprendizado histórico

            scorePorDirecao.put(dir, score);
        }

        if (scorePorDirecao.isEmpty()) {
            // aqui significa: nenhuma direção é 100% segura (todas batem em algo logo cedo)
            // em vez de simplesmente "desistir" e retornar UP,
            // escolhemos uma direção qualquer que ainda seja "legal" (sem ré, dentro da grade),
            // mesmo que vá morrer depois. Isso dá a sensação de que ele luta até o fim.
            List<String> candidatos = new ArrayList<>();
            for (String dir : VALID_DIRECTIONS) {
                if (isOpposite(dir, currentDir)) continue;

                int nx = bx;
                int ny = by;
                switch (dir) {
                    case "UP" -> ny--;
                    case "DOWN" -> ny++;
                    case "LEFT" -> nx--;
                    case "RIGHT" -> nx++;
                }
                if (nx < 0 || nx >= n || ny < 0 || ny >= n) continue;
                candidatos.add(dir);
            }

            if (!candidatos.isEmpty()) {
                String escolhido = candidatos.get(new Random().nextInt(candidatos.size()));
                log.warn("Sem movimentos totalmente seguros. Escolhendo direção legal aleatória: {}", escolhido);
                return escolhido;
            }

            log.warn("Sem qualquer direção válida. Retornando UP como último recurso.");
            return "UP";
        }

        double maxScore = scorePorDirecao.values().stream()
                .max(Double::compareTo)
                .orElse(Double.NEGATIVE_INFINITY);

        List<String> melhores = scorePorDirecao.entrySet().stream()
                .filter(e -> Objects.equals(e.getValue(), maxScore))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        String escolhido = melhores.get(new Random().nextInt(melhores.size()));
        log.debug("Fallback agressivo/aprendido escolheu {} (score = {})", escolhido, maxScore);
        return escolhido;
    }

    /**
     * Simula alguns passos à frente para medir a "sobrevivência".
     * Quanto mais passos conseguir sem bater, maior o valor (0 a 1).
     */
    private double simularSobrevivencia(int startX, int startY, String dirInicial, boolean[][] ocupadoOriginal, int depth) {
        int n = ocupadoOriginal.length;

        boolean[][] ocupado = new boolean[n][n];
        for (int y = 0; y < n; y++) {
            System.arraycopy(ocupadoOriginal[y], 0, ocupado[y], 0, n);
        }

        int x = startX;
        int y = startY;
        String dir = dirInicial;

        ocupado[y][x] = true;

        int survived = 0;

        for (int step = 0; step < depth; step++) {
            List<String> tentativas = new ArrayList<>();
            tentativas.add(dir);
            if (dir.equals("UP") || dir.equals("DOWN")) {
                tentativas.add("LEFT");
                tentativas.add("RIGHT");
            } else {
                tentativas.add("UP");
                tentativas.add("DOWN");
            }

            boolean moveFeito = false;
            for (String cand : tentativas) {
                int nx = x;
                int ny = y;

                switch (cand) {
                    case "UP" -> ny--;
                    case "DOWN" -> ny++;
                    case "LEFT" -> nx--;
                    case "RIGHT" -> nx++;
                }

                if (nx < 0 || nx >= n || ny < 0 || ny >= n) {
                    continue;
                }
                if (ocupado[ny][nx]) {
                    continue;
                }

                x = nx;
                y = ny;
                dir = cand;
                ocupado[ny][nx] = true;
                survived++;
                moveFeito = true;
                break;
            }

            if (!moveFeito) {
                break;
            }
        }

        return (double) survived / depth;
    }

    private int floodFillArea(int startX, int startY, boolean[][] ocupado) {
        int n = ocupado.length;
        boolean[][] visitado = new boolean[n][n];
        Deque<int[]> fila = new ArrayDeque<>();
        fila.add(new int[]{startX, startY});
        visitado[startY][startX] = true;
        int cont = 0;

        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

        while (!fila.isEmpty()) {
            int[] atual = fila.poll();
            int x = atual[0];
            int y = atual[1];
            cont++;

            for (int[] d : dirs) {
                int nx = x + d[0];
                int ny = y + d[1];
                if (nx < 0 || nx >= n || ny < 0 || ny >= n) continue;
                if (visitado[ny][nx]) continue;
                if (ocupado[ny][nx]) continue;

                visitado[ny][nx] = true;
                fila.add(new int[]{nx, ny});
            }
        }

        return cont;
    }

    private String montarResumoHistorico(List<Jogada> jogadas, Map<String, Double> scoreAprendizado) {
        long total = jogadas.size();
        long wins = jogadas.stream().filter(j -> "WIN".equalsIgnoreCase(j.getResultado())).count();
        long losses = jogadas.stream().filter(j -> "LOSE".equalsIgnoreCase(j.getResultado())).count();

        Map<String, Long> porAcaoWin = jogadas.stream()
                .filter(j -> "WIN".equalsIgnoreCase(j.getResultado()))
                .collect(Collectors.groupingBy(Jogada::getAcao, Collectors.counting()));

        Map<String, Long> porAcaoLoss = jogadas.stream()
                .filter(j -> "LOSE".equalsIgnoreCase(j.getResultado()))
                .collect(Collectors.groupingBy(Jogada::getAcao, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("Total de jogadas consideradas: ").append(total).append("\n");
        sb.append("Vitórias do BOT: ").append(wins).append("\n");
        sb.append("Derrotas do BOT: ").append(losses).append("\n\n");

        for (String dir : VALID_DIRECTIONS) {
            long w = porAcaoWin.getOrDefault(dir, 0L);
            long l = porAcaoLoss.getOrDefault(dir, 0L);
            double s = scoreAprendizado.getOrDefault(dir, 0.0);
            sb.append("Direção ").append(dir)
              .append(" -> vitórias: ").append(w)
              .append(", derrotas: ").append(l)
              .append(", score: ").append(String.format("%.2f", s))
              .append("\n");
        }

        return sb.toString();
    }
}
