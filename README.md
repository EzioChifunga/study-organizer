# Study Organizer

Aplicativo desktop em JavaFX para acompanhar sessões de estudo, guardando tudo
como notas Markdown em um **cofre do Obsidian**.

Inicie o cronômetro, estude, escreva um resumo ao terminar — e o app calcula suas
horas, pontos e sequências, e desenha os gráficos.

O projeto foi escrito para ser **lido**: classes pequenas, uma responsabilidade
cada, e comentários que explicam o *porquê* em vez de repetir o código.

---

## Como o armazenamento funciona

Não existe banco de dados. A pasta do cofre **é** os dados:

```
vault/
  Java/                                          <- uma categoria é uma pasta
    2026-08-07 1346 Interfaces e generics.md         <- uma sessão
    2026-08-06 0920 Pratica de collections.md
    Anotacoes sobre generics.md                  <- uma nota sua
    Streams API.md
  Algoritmos/
    2026-08-05 0900 Ordenacao.md
    Referencia de Big O.md
```

**Uma pasta é uma categoria.** Crie uma no app e a pasta aparece; crie uma no
Obsidian e a categoria aparece. Elas não têm como divergir porque existe apenas
uma delas.

**Uma sessão é uma nota na pasta da sua categoria.** Os metadados ficam no
frontmatter YAML, que o Obsidian exibe como propriedades da nota:

```markdown
---
category: Java
started: 2026-08-07T13:46:00
duration_seconds: 3300
points: 0.92
---

## Summary

Estudei interfaces e generics.

## Observations

Revisar classes abstratas.

## References

- [[Anotacoes sobre generics]]
```

**As referências ficam dentro da pasta.** Ao finalizar ou editar uma sessão você
pode buscar as notas daquela pasta por título e marcar as que quer linkar. O
seletor só lista uma pasta, então uma sessão fisicamente não consegue apontar
para outra categoria — a regra vale por causa do que é oferecido, não por causa
de uma checagem que pode ser esquecida. Selecione uma sessão na aba Histórico e
suas referências viram botões clicáveis, com um botão Voltar para refazer o
caminho.

Tudo é arquivo comum. Edite um resumo no Obsidian e o app percebe na próxima
leitura do cofre. Os pontos são sempre recalculados a partir da duração, então se
você corrigir uma duração à mão a pontuação acompanha.

---

## O que o app faz

**Cronômetro** — um mostrador analógico com aro numerado, sessenta marcações e um
ponteiro que varre suavemente. Horas e minutos à esquerda, segundos e milésimos à
direita. Iniciar, pausar e retomar ficam no botão redondo no centro do mostrador,
do jeito que um cronômetro físico funciona.

**Temas claro e escuro** — alternados no canto superior direito e lembrados entre
execuções. Escuro é o padrão.

**Regras da sessão** — iniciar, pausar, retomar, finalizar, cancelar. Só uma
sessão roda por vez. Tempo pausado não conta. Cancelar descarta tudo, e finalizar
exige um resumo escrito antes de salvar qualquer coisa.

**Pontos** — calculados automaticamente a **1 hora = 1 ponto** (30 min → 0,5;
90 min → 1,5). Você nunca digita a pontuação.

**Painel** — tempo e pontos de hoje, pontos da semana e do mês, sequência atual,
última sessão, categoria mais estudada e progresso semanal.

**Histórico** — toda sessão salva numa tabela ordenável, com data, hora de
início, categoria, duração, pontos, resumo e observações. Dá para editar e
excluir sessões, e **criar sessões passadas à mão** — assim o estudo feito longe
do computador também conta nos totais.

**Filtros e ranking** — uma grade de filtros acima das abas: busca por texto,
categoria, intervalo de datas, intervalo de duração e ordenação.

A barra é **uma só, compartilhada** por Histórico, Gráficos e Estatísticas. Filtre
para "Java em agosto" e a tabela, os três gráficos, o mapa de calor e todos os
totais passam a falar exatamente desse recorte — em vez de cada aba responder a
uma pergunta diferente. O Painel fica de fora, porque ele fala de agora (hoje, a
semana, a sequência) e um intervalo de datas não teria o que fazer ali; por isso
a barra some quando você está nele.

**Gráficos** — horas por dia da semana, horas por dia do mês, fatia do tempo por
categoria, e um mapa de calor de doze semanas de frequência de estudo.

**Estatísticas** — totais, sessões mais longa e mais curta, médias por sessão /
dia / semana / mês, e seus dias da semana mais e menos produtivos. Como o resto,
descrevem o recorte filtrado; limpe os filtros para ver todos os tempos.

---

## Experimente agora (modo demo)

Não envolve conta, chave nem conexão com a internet:

```bash
./mvnw javafx:run -Pdemo
```

Isso abre a mesma janela contra um **cofre temporário** cheio de categorias, notas
de apoio e cerca de dois meses e meio de sessões inventadas — então todo gráfico,
bloco, quadradinho do mapa de calor e link de referência tem conteúdo real por
trás. Uma faixa amarela no topo deixa claro que os dados não são seus.

Tudo funciona de verdade aqui, porque o demo escreve arquivos Markdown reais
usando exatamente o mesmo código do app. Inicie sessões, edite, siga referências,
abra uma pasta no Obsidian. A pasta temporária é apagada ao fechar a janela, e os
dados de exemplo usam uma semente fixa — então o demo começa idêntico toda vez, e
qualquer diferença que você notar veio da sua edição, não de números novos.

Seu cofre nunca é tocado no modo demo.

---

## Configuração para uso real

### 1. Pré-requisitos

Só é preciso um **JDK 17 ou mais novo**. Maven não precisa estar instalado — o
projeto inclui o Maven Wrapper (`mvnw`), que baixa a versão certa no primeiro uso.

Confira sua versão do Java:

```bash
java -version
```

### 2. Escolha um cofre (opcional)

Não há mais nada a configurar. Na primeira execução o app cria uma pasta `vault`
ao lado do projeto e a utiliza.

Para usar um cofre do Obsidian que você já tem, clique no botão **Vault:** no
canto superior direito e escolha *Choose a different vault…*. A escolha é
lembrada e passa a valer na próxima vez que você iniciar o app.

> Se uma pasta de cofre salva for movida ou apagada depois, inicie o app uma vez
> com `-Dvault.reset=true` para esquecer a configuração e voltar ao padrão.

### 3. Abra o cofre no Obsidian

Aponte o Obsidian para a mesma pasta ("Abrir pasta como cofre") e suas sessões
viram notas comuns, que você pode ler, buscar, linkar e editar por lá. O botão
**Open folder** na aba Histórico, e o botão de cofre no canto superior direito,
entregam a pasta direto ao Obsidian.

---

## Executando

```bash
# Modo demo - cofre temporário com notas de exemplo, sem configuração
./mvnw javafx:run -Pdemo

# Modo real - seu próprio cofre
./mvnw javafx:run

# Rodar os testes
./mvnw test
```

No `cmd.exe` ou PowerShell do Windows, use `mvnw.cmd` no lugar de `./mvnw`.

---

## Gerando o .exe

Para usar o app sem depender do Maven, e para levá-lo a outros PCs:

```
package-windows.cmd
```

O resultado sai em `target\dist\Study Organizer\`, com o `Study Organizer.exe`
dentro. **Essa pasta é autossuficiente**: leva um Java embutido, então roda em
qualquer Windows 64 bits mesmo em máquina sem Java instalado. Não há instalação —
copie a pasta inteira e pronto. São cerca de 138 MB, quase tudo o Java embutido.

Só este PC precisa de um JDK 17+ para *gerar* (o `jpackage` vem junto com ele). Os
PCs que apenas vão *rodar* não precisam de nada.

### Onde ficam as notas no app empacotado

O app procura, nesta ordem:

1. O cofre que você escolheu pelo botão **Vault:** (fica salvo entre execuções e
   vale tanto para o `.exe` quanto para o `./mvnw javafx:run`).
2. Uma pasta `vault` ao lado do `.exe`, se der para escrever ali — assim a pasta
   toda cabe num pen drive e viaja com você.
3. `%USERPROFILE%\StudyOrganizer\vault`, se o local do `.exe` for somente
   leitura (é o caso se você copiar para `Program Files`).

### Um aviso esperado no console

Ao iniciar, o app empacotado registra:

```
Unsupported JavaFX configuration: classes were loaded from 'unnamed module'
```

Isso é esperado e não quebra nada. Vem de o JavaFX estar no *classpath* em vez do
*module path* — o preço de não transformar o projeto num módulo Java. Sumiria com
`module-info.java` + `jlink`, que é a rota mais moderna mas bem mais burocrática
para uma aplicação de uma janela só. A alternativa está explicada no Javadoc de
`Launcher`.

### Instalador em vez de pasta

Se preferir um instalador `.msi` de verdade, instale o
[WiX Toolset 3](https://wixtoolset.org/) e troque no script:

```
--type app-image     ->     --type msi
```

---

## Como o código está organizado

```
src/main/java/com/study/organizer/
  MainApp.java              Entrada do uso real. Abre o seu cofre.
  DemoApp.java              Entrada do modo demo. Abre um cofre temporário.

  demo/
    SampleData.java         Inventa um histórico plausível, de semente fixa.
    DemoVault.java          Escreve esse histórico num cofre descartável.

  vault/
    ObsidianVault.java      A árvore de pastas: categorias, notas, links, movimentações.
    SessionNote.java        Sessão <-> Markdown, incluindo o frontmatter YAML.
    VaultLocation.java      Onde fica o cofre, lembrado entre execuções.
    ObsidianLauncher.java   Entrega pasta ou nota ao Obsidian via obsidian://
    MalformedNoteException.java  Marca um arquivo que não é nota de sessão.

  model/
    SessionState.java       A máquina de estados IDLE / RUNNING / PAUSED / FINISHED.
    StudySession.java       Uma sessão concluída. Imutável.

  service/
    TimerService.java       O cronômetro e as regras do ciclo de vida. Sem código de UI.
    PointsCalculator.java   A regra 1 hora = 1 ponto. Fonte única da verdade.
    StatisticsService.java  Todo número do painel, gráficos e estatísticas.
    FilteredSessionView.java O recorte em vista: a lista filtrada e seus números.
    StudyDataService.java   Roda o trabalho de arquivo fora da thread da UI.

  repository/
    SessionRepository.java        Interface: save, update, delete, findAll.
    CategoryRepository.java       Interface: o que o armazenamento precisa oferecer.
    VaultSessionRepository.java   Sessões como notas nas pastas das categorias.
    VaultCategoryRepository.java  Categorias como pastas.
    RepositoryException.java      Um tipo de exceção para toda falha de armazenamento.

  ui/
    MainWindow.java         Monta a janela. Compartilhada pelas duas entradas.
    Theme.java              As duas paletas, incluindo as cores pintadas em código.
    ThemeManager.java       Aplica um tema e lembra a escolha.
    DashboardView.java      Tela inicial: cronômetro e os números do dia.
    TimerPane.java          O mostrador, as leituras e os controles da sessão.
    StopwatchDial.java      O mostrador redondo, pintado num Canvas.
    FinishSessionDialog.java O resumo obrigatório, mais o seletor de notas.
    HistoryPane.java        A tabela de sessões, com adicionar / editar / excluir.
    SessionFilterPane.java  A grade de filtros compartilhada e as ordenações prontas.
    SessionEditorDialog.java O formulário de entrada manual ou correção.
    NotePickerPane.java     Busca as notas de uma pasta por título e marca.
    NoteViewerPane.java     Lê uma nota; seus links viram botões clicáveis.
    ChartsPane.java         Os três gráficos.
    HeatMapPane.java        A grade de frequência, construída à mão.
    StatisticsPane.java     Estatísticas de todos os tempos.
    StatTile.java           Um bloco reutilizável do painel.
    Dialogs.java            Pop-ups compartilhados de aviso / erro / confirmação.

  util/
    DurationFormatter.java  Segundos para "02:34:18" e "2h 34m".
```

### Seis ideias que valem notar

**Um filtro, uma fonte de "o que está em vista".** O `FilteredSessionView` guarda
a lista filtrada e os números calculados sobre exatamente ela. As três telas leem
dele, então não têm como discordar entre si — narrar o mesmo recorte é
consequência da estrutura, não de três atualizações que precisam ser lembradas.
Os números são recalculados do zero a cada mudança de filtro; é uma passada por
algumas centenas de objetos, rápida demais para se ver, e elimina a chance de um
total ficar pela metade.

**O cofre é a única fonte da verdade.** Sem arquivo de índice, sem cache, sem
banco guardando uma segunda cópia. Ler tudo significa abrir cada nota — para um
registro pessoal de estudo são algumas centenas de arquivos pequenos e leva
milissegundos, um bom preço por nunca ter duas versões da verdade que podem
discordar.

**A pasta é quem impõe a regra.** Uma sessão só pode linkar dentro da própria
categoria. Isso não é verificado depois do fato; o seletor simplesmente nunca
recebe outra coisa, porque `listNoteTitles` lista exatamente uma pasta. O único
ponto onde a regra precisa ser *aplicada* em vez de emergir é quando você troca a
categoria de uma sessão — e lá o app avisa o que será descartado antes de fazer.

**O cronômetro nunca conta ticks.** O tempo decorrido é sempre derivado do
relógio do sistema (`accumulatedMillis + tempo desde o último resume`). Um
`AnimationTimer` dispara a cada atualização de tela apenas para *reler* esse
valor, de modo que o ponteiro varre suavemente e os milésimos acompanham. Um
frame perdido ou atrasado faz a exibição atualizar um pouco tarde, mas nunca
torna o número errado — que é exatamente o que aconteceria com `elapsed++`.

**O mostrador é um Canvas, não setenta nós.** Sessenta marcações e dois ponteiros
como formas JavaFX individuais significariam o grafo de cena recalculando layout
de setenta e tantos objetos a cada frame. Um `Canvas` é um nó só, pintado
diretamente, e para algo tão geométrico quanto um relógio o código de desenho lê
melhor do que a pilha de nós equivalente. Os ângulos são resolvidos rotacionando
o canvas em vez de calcular a ponta de cada linha com trigonometria.

**Trabalho de arquivo nunca roda na thread da UI.** O JavaFX repinta e trata
cliques numa única thread; varrer uma pasta ali congela a janela — e um cofre em
drive sincronizado pode ser lento. O `StudyDataService` move toda leitura e
escrita para uma thread de fundo e devolve só o resultado com `Platform.runLater`.
Essa fronteira de threads é cruzada em exatamente um método.

### Onde ficam as cores

A maior parte da janela é estilizada por `src/main/resources/styles.css`, que
declara a paleta inteira uma vez por tema. O `ThemeManager` troca uma única
classe na raiz da cena e tudo se re-estiliza junto.

Duas partes o CSS não alcança, porque são pintadas em código e não por folha de
estilo: o **mostrador do cronômetro** e o **mapa de calor**. As cores delas ficam
como constantes `Color` em `Theme.java`. Se mexer numa paleta, mexa na outra.

---

## Testes

```bash
./mvnw test
```

- `PointsCalculatorTest` — a fórmula de pontos, contra os exemplos trabalhados.
- `DurationFormatterTest` — formatação do relógio, incluindo casos extremos e a
  divisão em horas / segundos / milésimos que o mostrador usa.
- `StatisticsServiceTest` — totais, limites de semana e mês, e as regras de
  sequência. A data e o fuso são fixos, então os resultados nunca dependem de
  quando os testes rodam.
- `SessionNoteTest` — o formato do arquivo: uma sessão sobrevive à ida e volta,
  os pontos são recalculados a partir da duração em vez de confiados, uma nota
  comum é recusada em vez de lida como sessão, e uma nota editada à mão no
  Obsidian com seções extras ainda é interpretada.
- `VaultSessionRepositoryTest` — arquivos reais numa pasta temporária: criar
  categoria cria pasta, pasta criada à mão vira categoria, editar sobrescreve no
  lugar, trocar de categoria move a nota, referências resolvem dentro da pasta e
  em nenhum outro lugar, e um caminho tentando escapar do cofre é recusado.
- `SessionFilterPaneTest` — as ordenações prontas, incluindo o desempate por data
  dentro de cada categoria.
- `UserInterfaceSmokeTest` — monta a janela e a folha de estilo de verdade contra
  um cofre real, pegando bindings quebrados e dados ruins de gráfico que a
  compilação não vê. Também verifica que o seletor descarta uma referência de
  outra pasta, que o mapa de calor tem escalas distintas nos dois temas, que um
  filtro move a tabela e os números **juntos** (inclusive ao excluir uma sessão
  com filtro ativo), e que aplicar a folha de estilo não produz **nenhum aviso de
  CSS** — uma cor não
  resolvida não é uma exceção, é um aviso no log e uma aparência quebrada em
  silêncio, então o teste escuta o logger de CSS do JavaFX e falha em qualquer
  coisa que ele diga. Pulado automaticamente se não houver display.

---

## Definições

A especificação deixou alguns termos em aberto. As escolhas feitas aqui:

| Termo | Significado |
| --- | --- |
| **Semana** | Segunda a domingo |
| **Mês** | Mês do calendário |
| **Sequência** | Dias consecutivos com ao menos uma sessão. Contada a partir de hoje se você já estudou hoje, senão a partir de ontem — para não marcar zero toda manhã antes de você começar. |
| **O dia de uma sessão** | O dia em que ela **começou**. Uma sessão das 23:30 às 00:15 conta inteira para o dia em que começou. |
| **Média por dia** | Tempo total dividido por todos os dias de calendário desde a primeira sessão, incluindo os dias sem estudo. |
| **Meta semanal** | 10 horas, usada pela barra de progresso do painel. Mude `WEEKLY_GOAL_HOURS` em `DashboardView`. |
| **Ponteiros do mostrador** | O ponteiro laranja dá uma volta por minuto; o mais escuro leva uma hora. |
| **Precisão gravada** | O mostrador exibe milésimos, mas uma sessão salva registra segundos inteiros — a precisão de milissegundos importa para a exibição fluida, não para um registro de estudo. |
| **Entradas manuais** | Idênticas às cronometradas depois de salvas. Os pontos são recalculados a partir da duração de qualquer forma, então um registro feito à mão não vale mais que o tempo que representa. |
| **Datas futuras** | Não podem ser escolhidas no editor — quebrariam todo cálculo de "dias desde" e não descrevem estudo já feito. |
| **Referências** | Só para notas da própria pasta da categoria. Troque a categoria de uma sessão e o app avisa que as referências serão descartadas. |
| **Nomes dos arquivos** | `yyyy-MM-dd HHmm` e depois um trecho do resumo, para que uma listagem simples da pasta já seja cronológica. Em caso de colisão, ganha ` (2)` em vez de sobrescrever. |
| **Notas que não são sessão** | Qualquer `.md` sem frontmatter de sessão e sem `## Summary` é ignorado, não reportado como quebrado. Um cofre é cheio de notas comuns. |
| **Máximo de minutos no filtro** | O topo do spinner (600) significa "sem limite superior", para uma sessão longa não sumir só porque o controle não vai mais alto. |
| **Alcance dos filtros** | Histórico, Gráficos e Estatísticas. O Painel não, porque fala de agora — a barra some quando você está nele. |
| **Ordenar por coluna** | Clicar num cabeçalho tem precedência sobre o "Sort by": um clique é uma instrução direta e momentânea. Ao limpar a ordenação da coluna, o "Sort by" reassume. |

---

## Ainda não incluído

Markdown renderizado no visualizador de notas (o texto cru é exibido), backlinks
("quais sessões referenciam esta nota?"), e observar o cofre em busca de
alterações feitas no Obsidian com o app aberto — por ora, reinicie ou reabra uma
aba para captá-las. Cada um é um próximo passo limpo quando você estiver
confortável com o núcleo.
