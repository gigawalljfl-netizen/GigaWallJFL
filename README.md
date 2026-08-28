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
