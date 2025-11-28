@Entity
@Data
public class Partida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String vencedor;

    @Lob
    private String mapaFinalJson;

    private LocalDateTime data = LocalDateTime.now();
}
