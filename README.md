# Analisador de Corridas (Android Nativo)

Aplicativo Android nativo em **Kotlin**, **Jetpack Compose** e **Material 3**, desenvolvido para motoristas da **Uber** e **99** analisarem automaticamente o retorno por quilômetro (`R$/km`), por hora (`R$/hora`), custo estimado de combustível e resultado **Após combustível**, considerando obrigatoriamente o deslocamento até o passageiro.

## Veículo padrão e regras financeiras

- **Veículo de referência**: Hyundai HB20 1.6 automático 2017 (editável)
- **Combustível inicial**: Etanol a `R$ 3,99/litro` com consumo urbano inicial de `7,1 km/l` (apenas referência editável)
- **Classificação por R$/km (KM total)**: Ruim `≤ R$ 1,40` · Média `> R$ 1,40 e ≤ R$ 1,80` · Boa `> R$ 1,80`
- **Classificação pela distância até o passageiro**: Boa `≤ 2,00 km` · Média `> 2,00 e ≤ 3,00 km` · Média com alerta `> 3,00 e ≤ 4,00 km` · Ruim `> 4,00 km`
- **Classificação final combinada**: `BOA` (verde), `MÉDIA` (amarelo), `MÉDIA COM ALERTA` (laranja com ícone de atenção) e `RUIM` (vermelho), acompanhada de justificativa breve.

## Como compilar no Android Studio

1. Abra a pasta `android/` no **Android Studio Ladybug (ou superior)**.
2. Aguarde a sincronização do Gradle (JDK 17, Android SDK 35).
3. Execute os testes unitários locais (`RideCalculatorTest` e `RideOfferParserTest`):
   ```bash
   ./gradlew testDebugUnitTest
   ```
4. Instale em um celular Android físico (Android 8.0 / API 26 ou superior) via USB/ADB:
   ```bash
   ./gradlew installDebug
   ```

## Recursos que dependem de teste em aparelho Android real

1. **Captura de tela via `MediaProjection`**: exige o diálogo nativo do Android de compartilhamento de tela e `VirtualDisplay` ativo.
2. **Janela flutuante sobre outros apps (`SYSTEM_ALERT_WINDOW`)**: exige concessão manual nas configurações do Android e sobreposição sobre os apps Uber Motorista e 99 Motorista.
3. **Google ML Kit OCR em tempo real**: depende de quadros reais capturados da tela do dispositivo.
4. **Formatos reais das ofertas da Uber e da 99**: variações regionais ou atualizações visuais dos aplicativos de corrida não puderam ser validadas sem um aparelho real e podem exigir ajustes nos padrões do `RideOfferParser`.
