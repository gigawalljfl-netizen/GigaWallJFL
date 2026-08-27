# GeoLock 🌐⏳

Cápsulas do tempo geocriptografadas com Realidade Aumentada — Android nativo (Kotlin + Jetpack Compose).

## Status desta entrega

Esqueleto funcional completo da arquitetura descrita na spec:

- ✅ Room Database (`TimeCapsule`, DAO, migrações automáticas via `fallbackToDestructiveMigration`)
- ✅ Motor de criptografia espaço-temporal AES-256-GCM (`SpaceTimeCrypto`) — chave derivada de lat/long/timestamp, nunca persistida
- ✅ Repositório de localização com Haversine + `FusedLocationProviderClient`
- ✅ Repositório de sensores (luz, barômetro, bússola/azimute via rotation vector)
- ✅ Motor de sonar tático + haptics proporcional (`ProximityFeedbackEngine`)
- ✅ Sistema de gamificação (patentes, medalhas, XP)
- ✅ Exportação/importação de cápsulas via JSON para QR Code
- ✅ 4 telas Compose: Radar tático, Selamento de cápsula, Detalhe/desbloqueio, HUD de AR com CameraX + mira por bússola
- ✅ Dark Cyber Theme (Neon Cyan / Magenta / Matrix Green)
- ✅ Workflow de CI/CD (`.github/workflows/build.yml`) publicando o APK em GitHub Releases, seguindo tua convenção

## O que falta / próximos passos sugeridos

- Tela de leitura de QR Code (a lib `zxing-android-embedded` já está nas dependências, falta a Activity/launcher)
- Anexo real de mídia às cápsulas (captura de foto + cifragem do arquivo — `SpaceTimeCrypto.encryptBytes` já existe, falta a UI)
- Verificação ativa da condição ambiental na tela de detalhe (hoje ela assume satisfeita quando não há bloqueio; falta plugar `SensorRepository` + thresholds na hora do desbloqueio)
- Persistência de XP/medalhas (hoje é derivado em runtime a partir das cápsulas — funciona, mas não guarda histórico)
- Ícone final da aplicação (o atual é um placeholder vetorial simples)

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
