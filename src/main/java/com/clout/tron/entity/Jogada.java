@Entity
@Data
public class Jogada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long partidaId;

    private int turno;

    private String jogador; // PLAYER ou IA

    private String movimento;

    @Lob
    private String contextoJson;

    @Lob
    private String respostaIa;
}
