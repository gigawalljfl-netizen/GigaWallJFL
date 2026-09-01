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
