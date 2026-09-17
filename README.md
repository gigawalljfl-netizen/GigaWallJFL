# GeoLock 🌐⏳

Cápsulas do tempo geocriptografadas com Realidade Aumentada — Android nativo (Kotlin + Jetpack Compose).

## Status desta entrega

Esqueleto funcional completo da arquitetura descrita na spec:

- ✅ Room Database (`TimeCapsule`, DAO, versão 2 com altitude de selamento)
- ✅ Motor de criptografia espaço-temporal AES-256-GCM (`SpaceTimeCrypto`) — chave derivada de lat/long/timestamp, nunca persistida; também cifra/decifra bytes (mídia)
- ✅ Repositório de localização com Haversine + `FusedLocationProviderClient`
- ✅ Repositório de sensores (luz, barômetro, bússola/azimute via rotation vector)
- ✅ Motor de sonar tático + haptics proporcional (`ProximityFeedbackEngine`)
- ✅ Sistema de gamificação (patentes, medalhas, XP)
- ✅ Exportação de cápsulas via QR Code (tela `QrExportScreen`, gera bitmap com ZXing)
- ✅ Importação de cápsulas via leitura de QR Code (tela `QrScanScreen`, usa zxing-android-embedded)
- ✅ Anexo real de qualquer arquivo: captura de foto (`TakePicturePreview`) OU seleção de qualquer arquivo do dispositivo (`GetContent`, sem exigir permissão persistente) — cifrado com a mesma chave espaço-temporal e salvo em `filesDir`; ao desbloquear, imagens são exibidas inline e outros tipos ganham botão "Salvar em Downloads" (MediaStore no Android 10+, escrita direta em versões anteriores)
- ✅ Mapa tático real (`MapScreen`, osmdroid/OpenStreetMap — sem API key nem billing do Google Maps): marcadores de cada cápsula, ponto de localização ao vivo do explorador, toque no marcador abre o detalhe
- ✅ Verificação ativa da condição ambiental no desbloqueio: leitura de lux ao vivo para DARKNESS/SUNLIGHT, comparação de altitude (GPS) para ALTITUDE — com status em tempo real na tela
- ✅ 7 telas Compose: Radar tático, Mapa, Selamento de cápsula (foto ou qualquer arquivo), Detalhe/desbloqueio (com sensor ao vivo e anexo), HUD de AR com CameraX + mira por bússola, Exportar QR, Escanear QR
- ✅ Dark Cyber Theme (Neon Cyan / Magenta / Matrix Green)
- ✅ Workflow de CI/CD (`.github/workflows/build.yml`) publicando o APK em GitHub Releases, Gradle fixado em 8.7

## O que falta / próximos passos sugeridos

- Persistência de XP/medalhas (hoje é derivado em runtime a partir das cápsulas — funciona, mas não guarda histórico de conquistas já vistas)
- Barômetro ainda não é usado para o bloqueio de ALTITUDE (hoje usa a altitude do GPS/rede, que a spec já permite — "via barômetro ou altimetria satelital" — mas fundir os dois sinais deixaria mais preciso)
- Ícone final da aplicação (o atual é um placeholder vetorial simples)
- Tela de listagem/gestão de cápsulas importadas separada das criadas (hoje aparecem juntas no radar, diferenciadas só pelo botão de exportar)

## Build local (Termux)

O projeto não inclui o Gradle Wrapper binário (`gradle-wrapper.jar`) porque foi montado num ambiente sem acesso à rede. Duas opções:

1. **Deixar o CI compilar** (recomendado): o workflow já usa `gradle/actions/setup-gradle`, que não depende do wrapper local — basta dar push.
2. **Gerar o wrapper no Termux**, se quiseres compilar localmente:
   ```bash
   pkg install gradle
   cd GeoLock
   gradle wrapper --gradle-version 8.7
   ```

## Publicar no GitHub

```bash
cd GeoLock
git init
git add .
git commit -m "GeoLock: esqueleto inicial da arquitetura"
git branch -M main
git remote add origin https://github.com/gigawalljfl-netizen/geolock.git
git push -u origin main
```

O workflow dispara automaticamente no push para `main` e publica o APK debug em Releases, tag `build-<numero_do_run>`.

## Correções críticas (v1.3.0)
- 🐛 **Bug raiz encontrado e corrigido**: o app só começava a rastrear o GPS depois que TODAS as permissões (localização + câmara + armazenamento) fossem concedidas juntas. Se qualquer uma fosse negada, o rastreamento nunca começava — e como selar/desbloquear cápsulas depende do GPS, essas ações falhavam em silêncio, sem nenhum aviso. Agora o rastreamento começa assim que a localização é concedida, independente das demais.
- Adicionado indicador visível de status de GPS ("🛰️ GPS ativo" / "🛰️ Aguardando sinal") no Radar, Selamento, Detalhe e Mapa.
- Selar cápsula e desbloquear agora mostram feedback explícito de sucesso/falha.
- Mapa: corrigido zoom inicial que deixava a tela parecendo vazia antes do primeiro fix de GPS.

## Radar de deteção por câmera (novo, v1.3.0)
No HUD de AR, além do feixe que aponta pro alvo real (a cápsula), a câmera escaneia a cena ao vivo via ML Kit (100% on-device, nunca envia imagem pra rede):
- Rostos detectados → rotulados PESSOA (magenta)
- Outros objetos salientes → rotulados OBJETO genérico (ciano)

Limitação honesta: o ML Kit não vem com classificador embutido que diferencie carro de animal — só "rosto" e "objeto saliente" sem espécie. Rotular CARRO/ANIMAL de verdade exigiria um modelo TensorFlow Lite customizado (ex. SSD MobileNet/COCO), que não deu pra baixar neste ambiente sem acesso à rede. O código já está pronto pra essa extensão futura.

## Precisão (v1.4.0)
- **GPS**: selar e desbloquear agora colhem 5 leituras de GPS em sequência e combinam por média ponderada pela variância inversa (leituras mais precisas pesam mais) — muito mais estável que confiar numa única leitura isolada. Aparece como "🎯 Calibrando GPS…" por alguns segundos antes de gravar/validar.
- **Faixas de qualidade de sinal** (Excelente/Boa/Fraca) exibidas de forma consistente no Radar, Selamento, Detalhe e Mapa.
- **Radar tático**: lista agora ordenada pela distância real até o explorador (mais perto primeiro), não pela data de criação.
- **Mapa**: círculo de precisão translúcido ao redor da tua posição, e círculo do raio de desbloqueio ao redor de cada cápsula (verde = desbloqueada, magenta = travada) — dá pra visualizar exatamente o quão perto é preciso chegar.
- **Radar de câmera**: rastreamento por ID entre quadros com suavização (interpolação) da posição das caixas — reduz o "tremido"/salto que aparecia a cada novo ciclo de deteção do ML Kit.

## Pendências da spec resolvidas (v1.5.0)
- **Ícone final do app**: substituído o placeholder por um logo próprio — hexágono de geofence (ciano) com um cadeado (magenta/verde) dentro, adaptativo (Android 8+).
- **Persistência de medalhas**: medalhas agora são gravadas com data de conquista numa tabela própria (`earned_medals`). Antes eram recalculadas a partir das cápsulas atuais — se apagasses uma cápsula, a medalha que ela rendeu sumia. Agora, uma vez ganha, fica ganha pra sempre. Contador de medalhas (🎖️) visível no Radar.
- **Fusão barômetro + GPS (bloqueio ALTITUDE)**: a pressão atmosférica é capturada no momento do selamento (quando o aparelho tem barômetro) e usada como sinal primário pra medir ganho de altura no desbloqueio — muito mais sensível que GPS em prédios. O GPS continua como alternativa pra aparelhos sem barômetro. A tela de detalhe mostra qual sensor está sendo usado.
- **Diferenciação visual de cápsulas importadas**: cada card do Radar agora mostra "🛡️ SUA" ou "📥 IMPORTADA".

## UX e segurança (v1.6.0)
- **Apagar/editar cápsula**: botões na tela de detalhe. Apagar remove também o anexo cifrado do disco. Editar é limitado a título/pista — local, hora e mensagem estão fundidos na chave de criptografia e nunca podem mudar.
- **Onboarding**: tela de boas-vindas explicando geofence + tempo + ambiente, mostrada só na primeira abertura (flag em SharedPreferences).
- **Tela de medalhas**: lista todas as medalhas possíveis, com emoji, descrição, e data de conquista pra quem já ganhou. Acesse tocando no rank/XP no Radar.
- **Backup cifrado por senha**: exporta a coleção inteira de cápsulas (mensagens; anexos ficam de fora) cifrada com AES-256-GCM + PBKDF2 (120 mil iterações) numa tela de Configurações nova. Restaurar pede a mesma senha.
- **Bloqueio do app por PIN/biometria**: opcional, via BiometricPrompt do Android — usa a biometria/PIN já configurado no aparelho, o GeoLock não guarda senha própria nenhuma. Ativa/desativa em Configurações.
- MainActivity agora estende FragmentActivity (exigência do BiometricPrompt).

## Anexo restrito a imagens comprimidas (v1.7.0)
- Removida a opção de anexar qualquer arquivo — agora só imagens, com duas opções: 📷 tirar foto agora ou 🖼️ escolher da galeria (via Photo Picker do Android, que não pede nenhuma permissão de armazenamento).
- Toda imagem passa por `ImageCompressor`: remove todo metadado EXIF (inclusive GPS embutido — bom pra privacidade), reduz resolução pro máximo necessário pra uma foto de referência (1600px no lado maior), e ajusta a qualidade JPEG iterativamente até caber em ~200KB, com piso de qualidade pra nunca ficar visivelmente "blocada". Se mesmo no piso ainda estiver grande, reduz mais a resolução em vez de forçar mais compressão.
- Caso de uso: uma foto do local (bar, banco, loja) junto da mensagem — cápsulas de só texto continuam ocupando espaço mínimo.
- Compatibilidade: cápsulas antigas com anexo não-imagem (de antes dessa mudança) continuam abrindo normalmente — a tela de detalhe ainda sabe mostrar "salvar em Downloads" pra qualquer mimetype que não seja imagem.

## Firebase Auth — login opcional (v1.8.0)

⚠️ **ESTE BUILD SÓ COMPILA DEPOIS DE ADICIONAR `google-services.json` EM `app/`** — ver instruções abaixo. Sem esse arquivo, o Gradle falha de propósito (mensagem clara: "File google-services.json is missing").

- Nova seção "CONTA" em Configurações: criar conta ou entrar com e-mail/senha, ver e-mail conectado, sair da conta.
- `AuthRepository` — só envolve o Firebase Auth (signUp/signIn/signOut, estado do usuário como Flow, obtenção do ID token).
- Totalmente opcional — o app continua funcionando 100% sem login, exatamente como sempre. Login só existirá pra habilitar sincronização (próxima etapa: Supabase Storage).

### Passo a passo pra desbloquear o build

**1. Firebase Console** (console.firebase.google.com):
1. "Adicionar projeto" → nomeia (ex.: GeoLock) → pode desativar o Google Analytics → Criar.
2. Dentro do projeto, ícone Android → package name **exatamente** `com.gigawalljfl.geolock` → Registrar app.
3. Baixa o `google-services.json` → coloca em `GeoLock/app/google-services.json` (mesma pasta do `build.gradle.kts` do módulo app).
4. Pula os passos de "adicionar SDK" no console (o Gradle já está configurado) → Continuar pro console.
5. Menu lateral → Build → Authentication → Get Started → aba "Sign-in method" → ativa "E-mail/senha" → Salvar.

**2. No Termux**, depois de baixar o `google-services.json` pro telemóvel:
```bash
mv ~/storage/downloads/google-services.json ~/GeoLock/app/google-services.json
```

## Supabase Storage — sincronização de imagens e backup (v1.9.0)

⚠️ **PRECISA DE MAIS CONFIGURAÇÃO ANTES DE COMPILAR** — ver passo a passo abaixo. Diferente do google-services.json, aqui o Gradle NÃO vai acusar erro — ele compila normal, só que as chamadas à nuvem vão falhar em runtime até os valores certos entrarem em `SupabaseConfig.kt`.

- Chamadas REST diretas à API do Supabase Storage (`CloudStorageRepository`) — sem o SDK Kotlin Multiplatform oficial, que puxaria uma versão de Kotlin incompatível com o projeto.
- Toda chamada é autorizada com o TOKEN DE IDENTIDADE DO FIREBASE — é a integração "Third-Party Auth" do Supabase que valida isso do lado do servidor, sem precisar de uma segunda conta.
- Depois de selar uma cápsula com imagem, se estiver logado, a imagem JÁ CIFRADA (nunca a original) sobe pra nuvem em segundo plano — falha aqui nunca afeta o selamento local.
- Nova seção "BACKUP NA NUVEM" em Configurações: mesma senha do backup local, mas essa versão leva as imagens junto (o backup local sozinho não leva).

### Passo a passo Supabase

**1. Console do Supabase** (supabase.com/dashboard, sem cartão):
1. New Project → nomeia, define senha do banco (guarda em lugar seguro) → Create (leva ~2min).
2. Menu lateral → Storage → New bucket → nome **exatamente** `geolock-vault` → **Private** → Save.
3. Menu lateral → SQL Editor → New query → cola e roda:
```sql
create policy "cada usuario so acessa a propria pasta"
on storage.objects for all
using ( (storage.foldername(name))[1] = auth.uid()::text )
with check ( (storage.foldername(name))[1] = auth.uid()::text );
```
4. Menu lateral → Authentication → Sign In / Providers → Third-Party Auth → Add integration → Firebase → cola o **Project ID** do Firebase (Firebase Console → engrenagem → Project Settings → aba General → campo "Project ID").
5. Menu lateral → Project Settings → API → copia **Project URL** e a chave **anon / public**.

**2. No código**, edita `app/src/main/java/com/gigawalljfl/geolock/cloud/CloudStorageRepository.kt` e substitui:
```kotlin
const val SUPABASE_URL = "https://SUBSTITUA-AQUI.supabase.co"       // → tua Project URL
const val SUPABASE_ANON_KEY = "SUBSTITUA_PELA_ANON_KEY_AQUI"        // → tua anon/public key
```

## Sincronização automática do backup (v2.0.0)

- Novo toggle em Configurações → Backup na Nuvem: "Sincronizar sozinho após cada cápsula". Ligado, o app sobe o backup completo pra nuvem automaticamente depois de cada cápsula selada — sem precisar lembrar de tocar em "Enviar backup".
- `SecureBackupPassphraseStore` — guarda a senha de backup cifrada via Android Keystore (chave que nunca sai do hardware), NÃO via `EncryptedSharedPreferences` (a própria Google marcou essa classe como obsoleta em 2025). Zero dependências novas.
- Troca de segurança sendo dita com honestidade: antes, a senha de backup só existia na cabeça de quem a escolheu. Agora fica cifrada no aparelho — continua protegida contra quem rouba só o arquivo/bucket, mas não contra acesso root a um aparelho já desbloqueado.

## Correção crítica: crash ao selar (v2.0.1)

🐛 **Causa raiz**: o código de sincronização automática (v2.0.0) chamava o Android Keystore de forma síncrona e bloqueante DIRETO NA THREAD PRINCIPAL — tanto `autoSyncBackupIfEnabled()` (rodava dentro do `viewModelScope.launch` padrão de `sealCapsule`, que usa Main por default) quanto `enableAutoSync()` (chamado direto do `onCheckedChange` do Switch, sem nenhuma coroutine). Operações de Keystore, principalmente geração de chave, podem ser lentas o bastante pra travar a interface — o Android mata o app por ANR (App Not Responding), que aparece como "trava e sai sozinho".

- Toda leitura/escrita no `SecureBackupPassphraseStore` agora roda dentro de `Dispatchers.IO`, nunca na thread principal.
- `save()` agora blindado com try/catch e retorna `Boolean` de sucesso — antes podia lançar exceção sem rede de proteção nenhuma.

## Radar sonar imersivo (v2.1.0)

- `SonarGridCanvas` novo (`ui/components/`) — anéis concêntricos, retículo, raios em 8 direções e feixe de varredura animado com rastro esmaecendo. Reutilizado nas duas telas de radar.
- **Radar tático**: o topo da lista virou um radar circular de verdade — cada cápsula é um blip posicionado por rumo (relativo à bússola do aparelho — "pra cima" é sempre pra onde estás apontando agora) e distância (mais perto do centro = mais perto na vida real). Cor do blip: verde = desbloqueada, magenta = tua e travada, ciano = importada. Tocar num blip abre a cápsula direto. A lista completa continua embaixo, sem perder nada.
- **HUD de AR**: os mesmos anéis sonar, semitransparentes, por cima do feed da câmera — mesma linguagem visual das duas telas, sem atrapalhar a leitura da cena real nem as caixas de deteção.
- `LocationRepository.bearingDegrees()` novo — rumo inicial entre dois pontos, usado pra posicionar os blips no radar.

## Modelo de deteção mais robusto (referência pra depois)
Quando formos integrar: **EfficientDet-Lite0** (recomendação oficial do Google, Apache 2.0, treinado no COCO — cobre pessoa/carro/animal). Baixar a variante "metadata" em kaggle.com/models/tensorflow/efficientdet/tfLite/lite0-detection-metadata/1 (ou espelho em huggingface.co/litert-community/efficientdet), colocar em `app/src/main/assets/efficientdet-lite0.tflite`.

## Correção crítica: crash ao selar em aparelhos sem barômetro (v2.1.2)

🐛 **Causa raiz, confirmada por log de crash real** (obrigado ao log do Termux/logcat — foi isso que tornou possível achar isso com certeza em vez de adivinhar): `readPressureOnce()` usava `observePressure().first()`. Em aparelhos SEM sensor de barômetro (comum em modelos de entrada, ex.: Itel L5006S), `observePressure()` fecha o fluxo de propósito sem emitir nada — e `first()` num fluxo que termina vazio lança `NoSuchElementException`, uma exceção que `withTimeoutOrNull` NÃO captura (ele só entende "estourou o tempo", não "terminou sem valor nenhum"). Resultado: crash garantido ao selar QUALQUER cápsula, em QUALQUER aparelho sem barômetro — a mesma leva que trouxe a fusão barômetro+GPS quebrou o selamento pra quem não tem o sensor.

- Trocado `.first()` por `.firstOrNull()` — agora retorna null graciosamente nesse caso, e o app segue usando só GPS pro bloqueio ALTITUDE, como sempre foi o fallback pretendido.

## Independência do Google Play Services (v2.2.0)

- `LocationRepository` reescrito para usar `android.location.LocationManager` (API nativa do Android) em vez de `FusedLocationProviderClient` (Google Play Services). Dependência `com.google.android.gms:play-services-location` removida do projeto.
- Cada provedor disponível no aparelho (GPS, rede, etc.) é registrado separadamente — sem a "fusão" automática que o Google oferecia pronta, mas a média ponderada por precisão em `getAveragedFix()` já naturalmente prioriza a leitura mais confiável entre eles, então a qualidade final do fix não muda na prática.
- Resultado: a função central do app (selar/desbloquear/mapa/radar, tudo que depende de localização) agora funciona em QUALQUER aparelho Android — inclusive Huawei pós-2019 e ROMs sem Google Play Services — não só nos que têm Play Services ativo.
- Osmdroid (o mapa) já não dependia de Google Maps; ML Kit (radar de câmera) já vinha embutido no APK; Firebase Auth por e-mail/senha não usa Google Sign-In. Nenhum desses precisou de mudança.

## Suporte a telas de todos os tamanhos (v2.3.0)

- **Correção de densidade**: vários elementos desenhados em `Canvas` (radar sonar, blips, HUD de AR) usavam pixel bruto fixo em vez de `dp`/`sp` — isso os fazia parecer gigantes em tela de baixa densidade e minúsculos em tela de alta densidade, o oposto de "adaptável". Todos convertidos pra escalar com a densidade real do aparelho: `SonarGridCanvas`, os blips do Radar tático, as caixas de deteção e o feixe do HUD de AR, e os círculos do Mapa.
- **Alvo de toque**: o toque nos blips do radar estava em pixel fixo (48px reais, minúsculo em tela de alta densidade); agora é 48dp — o mínimo recomendado pela própria Android pra toque confiável, especialmente importante em tela pequena.
- **Telas grandes (tablet)**: o radar circular e o QR Code de exportação agora têm largura máxima (420dp e 320dp) e ficam centralizados — sem isso, num tablet eles cresceriam sem limite até ficar absurdamente grandes e desproporcionais ao resto da tela.
- **Telas pequenas**: `CapsuleDetailScreen` e `QrExportScreen` ganharam rolagem — antes, em tela pequena, conteúdo (principalmente a imagem desbloqueada) podia ficar cortado sem jeito de ver o resto.
- Título da cápsula no Radar agora corta com reticências em vez de quebrar o layout numa tela estreita.

Escopo consciente: orientação continua travada em retrato (portrait) — mudar isso exigiria testar cada tela em paisagem também, um projeto à parte. O manifesto não tem nenhuma restrição de `<supports-screens>`, então o Android já assume suporte a todos os tamanhos por padrão.

## Confiabilidade do rumo — correção séria (v2.4.0)

Problema relatado: o rumo até a cápsula no radar/HUD não era confiável o bastante pra guiar alguém de volta a um local à distância, sem abrir o mapa. Causa raiz identificada: a bússola do telemóvel aponta pro **norte magnético**, mas o rumo calculado até a cápsula é em relação ao **norte verdadeiro** — sem corrigir essa diferença (declinação magnética), o erro pode passar de 15-20° dependendo da região, o que a 300m de distância é quase 90m de erro lateral.

Solução — usa TUDO que o Android oferece, na ordem de confiabilidade:
1. **Rumo de deslocamento do GPS** (`Location.bearing`) — quando o explorador está caminhando de verdade (velocidade ≥0.8 m/s), usa o rumo que o próprio GPS calcula a partir de posições reais. Zero interferência magnética possível.
2. **Bússola corrigida por declinação magnética** (`GeomagneticField`, com cache por posição pra não pesar no processador) — usada só quando parado ou sem rumo de GPS disponível.
3. **Suavização circular** da leitura da bússola — reduz a "tremida" natural do magnetômetro, tratando corretamente a virada 359°→1°.
4. **Aviso de baixa precisão do sensor** — quando a bússola está descalibrada (perto de metal, eletrônicos), avisa e sugere o gesto de "mexer em forma de 8" pra recalibrar, em vez de mostrar silenciosamente um rumo em que não se pode confiar.
5. **Transparência total**: Radar e HUD de AR agora mostram qual fonte de rumo está ativa agora (🛰️ GPS ou 🧭 bússola) — o mesmo princípio de nunca esconder incerteza que já usamos pro status de GPS.

`GeoFix` ganhou o campo `bearingDegrees` (do próprio GPS). Novo `HeadingEstimator` centraliza toda essa lógica, usado tanto no Radar tático quanto no HUD de AR.

## Aparelhos sem sensor de bússola (v2.4.1)

Pergunta que expôs um buraco real: em aparelhos sem `TYPE_ROTATION_VECTOR` (sem magnetômetro), o azimute ficava travado no valor inicial (0°) pra sempre, e o app mostraria uma direção FIXA e ERRADA sem avisar — exatamente o tipo de coisa que este app nunca deveria fazer.

Corrigido: `HeadingSource` ganhou um terceiro estado, `UNAVAILABLE`, usado quando não há bússola E o explorador não está caminhando rápido o bastante pro GPS calcular rumo. Nesse estado:
- Radar tático: nenhum blip é desenhado em ângulo nenhum — mostra "⚠️ SEM RUMO — Anda alguns passos" no centro do círculo, em vez de posições inventadas.
- HUD de AR: a mira direcional não é desenhada (só o círculo central neutro), e o retorno háptico de "travado no alvo" nunca dispara por engano.
- Ambas as telas mostram a mensagem de status "⚠️ Sem bússola neste aparelho — anda alguns passos pra ativar o rumo por GPS".

Resultado: aparelhos sem bússola funcionam perfeitamente enquanto o explorador estiver caminhando (rumo por GPS, que nunca precisou de bússola pra começo de conversa) — só fica sem informação de direção quando parado, e nesse caso o app admite isso claramente em vez de mentir.

## Precisão de selamento + diagnóstico do backup na nuvem (v2.5.0)

### Precisão de selamento
Relato: cápsulas seladas a 3-10m do lugar real, ao ponto de cair em propriedade vizinha. Causa: a calibração antiga parava sempre em 5 amostras num teto de 6s, aceitando o que vier — perto de prédios, multipath (sinal ricocheteando antes de chegar no aparelho) causa erro SISTEMÁTICO que não desaparece só com mais amostras coletadas rapidamente.

Mudanças em `LocationRepository.getAveragedFix()`:
- Coleta agora até 20 amostras num teto de 12s (dobro do anterior), mas **para assim que atingir uma precisão-alvo de ±8m** — não espera à toa se o sinal já está bom.
- **Rejeição de outliers**: descarta leituras isoladas a mais de 25m do grupo antes de calcular a média — um salto espúrio não pesa mais no resultado.
- **Progresso ao vivo**: Selamento e Desbloqueio agora mostram quantas leituras já foram colhidas e a melhor precisão até agora, em vez de uma espera às cegas.
- **Precisão gravada por cápsula**: `sealAccuracyMeters` novo em `TimeCapsule` — se o raio escolhido for menor que a precisão realmente alcançada, avisa por Toast no momento e mostra um aviso permanente na tela da cápsula depois.

Limite honesto, documentado no código: isto reduz o ruído aleatório do GPS, boa parte do erro típico — mas erro sistemático por multipath perto de estruturas não desaparece por software sozinho, precisaria de GPS diferencial (hardware que nenhum telemóvel comum tem). O que dá pra fazer é tirar o máximo proveito do sinal disponível, e isso o código agora faz.

### Backup na nuvem — diagnóstico
Erros do Supabase estavam sendo engolidos em silêncio (`catch { false }`) — impossível saber se era rede, bucket errado, política de RLS rejeitando, ou token expirado. `CloudStorageRepository` agora retorna `CloudResult` com o motivo real (código HTTP + corpo da resposta do Supabase + uma dica específica pra cada código comum: 401/403/404). Upload/restauração manuais e automáticos mostram esse motivo exato em vez de "não está funcionando".

Teste de novo e me manda a mensagem exata que aparecer — agora ela vai dizer o que está errado de verdade.

## Mapa satélite, área offline, barra fixa, limite de largura (v2.6.0)

### Mapa: dois modos + área offline real
- **Modo satélite** novo: imagens aéreas via ESRI World Imagery (gratuito, sem precisar do Google Maps/Play Services/chave de API) — toggle 🗺️ Mapa / 🛰️ Satélite no canto superior direito do mapa.
- **"Baixar área (2km) pra offline"**: usa o `CacheManager` nativo do osmdroid pra baixar os blocos de uma área delimitada (2km de raio, zoom 13-18) ao redor da posição atual, com progresso ao vivo. Isso é a versão REAL e sustentável de "mapa offline" — baixar o mundo inteiro no primeiro uso, como foi pedido originalmente, não é possível: são vários terabytes de dados, e o OpenStreetMap proíbe e bane download em massa. Isso cumpre o mesmo objetivo (usar sem internet depois) sem quebrar o serviço.
- O cache de navegação normal (tudo que já foi visto no mapa) continua funcionando automaticamente como sempre, por cima disso.

### Barra de navegação fixa
Configurações, Mapa, Escanear QR e Selar cápsula saíram do topo do Radar e foram pra uma barra fixa na parte de baixo da tela (`Scaffold` + `bottomBar`) — posição que não é afetada por diferença de tamanho de tela.

### Limite de largura de 500dp
Todo o conteúdo do app agora tem um teto de 500dp de largura, centralizado — em qualquer telemóvel comum (bem mais estreito que isso) não muda nada visualmente; em tablet, evita que tudo estique de forma esquisita. Mapa e HUD de AR ficam de fora de propósito (são experiências de tela cheia — câmera e mapa devem aproveitar o espaço todo). O radar circular também ficou um pouco menor (360dp em vez de 420dp) — isso deixa mais espaço vertical de sobra em telas pequenas/baixas, que era provavelmente a causa real de "radar grande demais pra ver por completo" (a tela não tinha nenhuma rolagem antes).

Texto continua seguindo o tamanho de fonte do sistema Android (nunca um valor fixo do app) — como já era antes, isso não mudou.

## Atribuição de mapa (v2.6.1)
Adicionada atribuição visível no canto do mapa — "© OpenStreetMap contributors" no modo Mapa, créditos do ESRI no modo Satélite. Não é opcional: é condição de uso gratuito de ambos os serviços.

## Correção de build: nome de classe errado (v2.6.2)
🐛 `XYZTileSource` não existe no osmdroid — o nome certo é `XYTileSource` (sem "Z"). Corrigido em `MapScreen.kt` (import + fonte de satélite do ESRI). Causava falha de compilação total.
