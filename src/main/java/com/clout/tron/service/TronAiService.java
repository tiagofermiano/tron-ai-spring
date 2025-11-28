package com.clout.tron.service;

import com.clout.tron.ai.GeminiService;
import com.clout.tron.ai.GptService;
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
    private final GptService gptService;
    private final JogadaRepository jogadaRepository;
    private final ObjectMapper objectMapper;

    private static final List<String> VALID_DIRECTIONS =
            List.of("UP", "DOWN", "LEFT", "RIGHT");

    public String decidirMovimento(EstadoDTO estado) {
        // 1) histórico permanente do banco → RL leve global
        List<Jogada> jogadasRecentes = jogadaRepository.findTop300ByOrderByIdDesc();
        Map<String, Double> scoreAprendizado = calcularScoreAprendizadoPorAcao(jogadasRecentes);

        String estadoJson;
        try {
            estadoJson = objectMapper.writeValueAsString(estado);
        } catch (Exception e) {
            log.error("Erro ao serializar estado. Usando fallback direto.", e);
            return decidirMovimentoFallbackSuperSobrevivencia(estado, scoreAprendizado);
        }

        String resumoAprendizado = montarResumoHistorico(jogadasRecentes, scoreAprendizado);

        // 2) CACHE: tenta reaproveitar decisão em estados idênticos
        String viaCache = decidirPorCache(estado, estadoJson);
        if (viaCache != null) {
            log.debug("Decisão obtida via cache de estado: {}", viaCache);
            return viaCache;
        }

        // 3) Prompt único (serve tanto pro Gemini quanto pro GPT)
        String prompt = """
Você é a IA controlando a moto ROSA no jogo TRON.

OBJETIVO:
- Eliminar o adversário (moto azul) o mais rápido possível.
- Jogar de forma AGRESSIVA, perseguindo e tentando encurralar o inimigo.
- Nunca desistir: mesmo em situações ruins, busque sobreviver o máximo possível, pois o jogador pode errar.

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
""".formatted(resumoAprendizado, estadoJson);

        // 4) motor híbrido SEM cooldown: Gemini → (se falhar) GPT → (se falhar) fallback

        // 4.1 tenta Gemini
        try {
            String respostaGemini = geminiService.gerarMovimento(prompt);
            String dir = normalizarDirecao(respostaGemini);
            if (dir != null && isDirecaoSeguraProfunda(estado, dir, 8)) {
                log.debug("Usando direção do Gemini: {}", dir);
                return dir;
            } else if (dir != null) {
                log.warn("Direção do Gemini inválida ou não segura (mesmo com lookahead): {}. Indo para GPT.", dir);
            }
        } catch (NonTransientAiException e) {
            log.error("Erro de IA (Gemini).", e);
        } catch (Exception e) {
            log.error("Erro genérico chamando Gemini.", e);
        }

        // 4.2 tenta GPT
        try {
            String respostaGpt = gptService.gerarMovimento(prompt);
            String dir = normalizarDirecao(respostaGpt);
            if (dir != null && isDirecaoSeguraProfunda(estado, dir, 8)) {
                log.debug("Usando direção do GPT: {}", dir);
                return dir;
            } else if (dir != null) {
                log.warn("Direção do GPT inválida ou não segura (mesmo com lookahead): {}. Indo para fallback local.", dir);
            }
        } catch (Exception e) {
            log.error("Erro ao chamar GPT. Indo direto para fallback local.", e);
        }

        // 5) se nenhum modelo deu uma jogada realmente boa → fallback local (super sobrevivência)
        return decidirMovimentoFallbackSuperSobrevivencia(estado, scoreAprendizado);
    }

    // ========================= CACHE DE ESTADO =========================

    private String decidirPorCache(EstadoDTO estado, String estadoJson) {
        List<Jogada> jogadasMesmoEstado =
                jogadaRepository.findTop50ByEstadoJsonOrderByIdDesc(estadoJson);

        if (jogadasMesmoEstado == null || jogadasMesmoEstado.isEmpty()) {
            return null;
        }

        Map<String, Long> winsPorAcao = jogadasMesmoEstado.stream()
                .filter(j -> "WIN".equalsIgnoreCase(j.getResultado()))
                .collect(Collectors.groupingBy(Jogada::getAcao, Collectors.counting()));

        Map<String, Long> lossPorAcao = jogadasMesmoEstado.stream()
                .filter(j -> "LOSE".equalsIgnoreCase(j.getResultado()))
                .collect(Collectors.groupingBy(Jogada::getAcao, Collectors.counting()));

        String melhorAcao = null;
        double melhorScore = Double.NEGATIVE_INFINITY;

        for (String dir : VALID_DIRECTIONS) {
            long w = winsPorAcao.getOrDefault(dir, 0L);
            long l = lossPorAcao.getOrDefault(dir, 0L);
            double score = (double) (w - l) / (w + l + 1);

            if (score > melhorScore && isDirecaoSeguraProfunda(estado, dir, 6)) {
                melhorScore = score;
                melhorAcao = dir;
            }
        }

        return melhorAcao;
    }

    private String normalizarDirecao(String raw) {
        if (raw == null) return null;
        String dir = raw.trim().toUpperCase();
        dir = dir.replaceAll("[^A-Z]", ""); // limpa tipo "LEFT." ou "LEFT\n"
        if (!VALID_DIRECTIONS.contains(dir)) return null;
        return dir;
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

    private boolean isDirecaoSeguraImediata(EstadoDTO estado, String dir) {
        int n = estado.getBoardSize();
        boolean[][] ocupado = montarMatrizOcupacao(estado);

        int bx = estado.getBotX();
        int by = estado.getBotY();
        String currentDir = estado.getBotDirection();

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

    /**
     * Segurança "profunda": simula alguns passos à frente.
     * Se morre muito rápido, consideramos essa direção suicida.
     */
    private boolean isDirecaoSeguraProfunda(EstadoDTO estado, String dir, int depth) {
        if (!isDirecaoSeguraImediata(estado, dir)) return false;

        int n = estado.getBoardSize();
        boolean[][] ocupado = montarMatrizOcupacao(estado);

        int x = estado.getBotX();
        int y = estado.getBotY();
        String atualDir = dir;

        // aplica o primeiro passo na direção sugerida
        switch (atualDir) {
            case "UP" -> y--;
            case "DOWN" -> y++;
            case "LEFT" -> x--;
            case "RIGHT" -> x++;
        }

        if (x < 0 || x >= n || y < 0 || y >= n) return false;
        if (ocupado[y][x]) return false;
        ocupado[y][x] = true;

        int survived = 0;

        for (int step = 1; step < depth; step++) {
            // tenta continuar ou virar sem ré
            List<String> tentativas = new ArrayList<>();
            tentativas.add(atualDir);
            if (atualDir.equals("UP") || atualDir.equals("DOWN")) {
                tentativas.add("LEFT");
                tentativas.add("RIGHT");
            } else {
                tentativas.add("UP");
                tentativas.add("DOWN");
            }

            boolean moveFeito = false;
            for (String cand : tentativas) {
                if (isOpposite(cand, atualDir)) continue;

                int nx = x;
                int ny = y;
                switch (cand) {
                    case "UP" -> ny--;
                    case "DOWN" -> ny++;
                    case "LEFT" -> nx--;
                    case "RIGHT" -> nx++;
                }

                if (nx < 0 || nx >= n || ny < 0 || ny >= n) continue;
                if (ocupado[ny][nx]) continue;

                x = nx;
                y = ny;
                atualDir = cand;
                ocupado[ny][nx] = true;
                survived++;
                moveFeito = true;
                break;
            }

            if (!moveFeito) break;
        }

        // se não aguenta nem metade dos passos de lookahead, consideramos ruim
        return survived >= Math.max(1, depth / 2);
    }

    // ========================= APRENDIZADO (RL LEVE GLOBAL) =========================

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

    // ========================= FALLBACK LOCAL SUPER SOBREVIVÊNCIA =========================

    /**
     * Fallback ultra conservador:
     * - procura a direção que mais tempo mantém o bot vivo (simula até 20 passos);
     * - usa área livre e aprendizado como desempate;
     * - só se estiver TUDO muito ruim ele pega uma direção "legal" qualquer.
     */
    private String decidirMovimentoFallbackSuperSobrevivencia(EstadoDTO estado, Map<String, Double> scoreAprendizado) {
        int n = estado.getBoardSize();
        boolean[][] ocupado = montarMatrizOcupacao(estado);

        int bx = estado.getBotX();
        int by = estado.getBotY();
        String currentDir = estado.getBotDirection();

        Map<String, Double> scorePorDirecao = new HashMap<>();

        for (String dir : VALID_DIRECTIONS) {
            if (isOpposite(dir, currentDir)) continue;
            if (!isDirecaoSeguraImediata(estado, dir)) continue;

            int nx = bx;
            int ny = by;
            switch (dir) {
                case "UP" -> ny--;
                case "DOWN" -> ny++;
                case "LEFT" -> nx--;
                case "RIGHT" -> nx++;
            }

            // area livre a partir do próximo passo
            int area = floodFillArea(nx, ny, ocupado);
            double learned = scoreAprendizado.getOrDefault(dir, 0.0);

            // quantos passos ele consegue sobreviver seguindo essa direção e variações
            int survivalSteps = simularPassosAteMorrer(nx, ny, dir, ocupado, 20);

            // score focado em sobreviver MUITO
            double score = survivalSteps * 1000.0    // prioridade máxima: não morrer rápido
                         + area * 5.0               // espaço conta bastante
                         + learned * 10.0;          // histórico ajuda, mas é terceiro critério

            scorePorDirecao.put(dir, score);
        }

        if (scorePorDirecao.isEmpty()) {
            // nenhuma direção 100% segura → ainda tentamos alguma direção "legal" (sem sair da grade)
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
                log.warn("Fallback: sem movimentos totalmente seguros. Escolhendo direção legal aleatória: {}", escolhido);
                return escolhido;
            }

            log.warn("Fallback: sem qualquer direção válida. Retornando UP como último recurso.");
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
        log.debug("Fallback super sobrevivência escolheu {} (score = {})", escolhido, maxScore);
        return escolhido;
    }

    /**
     * Simula até "depth" passos à frente e retorna quantos passos conseguiu
     * dar antes de morrer (bateu na parede ou rastro).
     */
    private int simularPassosAteMorrer(int startX, int startY, String dirInicial, boolean[][] ocupadoOriginal, int depth) {
        int n = ocupadoOriginal.length;

        boolean[][] ocupado = new boolean[n][n];
        for (int y = 0; y < n; y++) {
            System.arraycopy(ocupadoOriginal[y], 0, ocupado[y], 0, n);
        }

        int x = startX;
        int y = startY;
        String dir = dirInicial;

        if (x < 0 || x >= n || y < 0 || y >= n) return 0;
        if (ocupado[y][x]) return 0;
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
                if (isOpposite(cand, dir)) continue;

                int nx = x;
                int ny = y;

                switch (cand) {
                    case "UP" -> ny--;
                    case "DOWN" -> ny++;
                    case "LEFT" -> nx--;
                    case "RIGHT" -> nx++;
                }

                if (nx < 0 || nx >= n || ny < 0 || ny >= n) continue;
                if (ocupado[ny][nx]) continue;

                x = nx;
                y = ny;
                dir = cand;
                ocupado[ny][nx] = true;
                survived++;
                moveFeito = true;
                break;
            }

            if (!moveFeito) break;
        }

        return survived;
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
