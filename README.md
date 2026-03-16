# Xiaomi ADB/Fastboot Tools

[![Build and Release](https://github.com/ukaji3/XiaomiADBFastbootTools/actions/workflows/release.yml/badge.svg)](https://github.com/ukaji3/XiaomiADBFastbootTools/actions/workflows/release.yml)
![](https://img.shields.io/github/license/ukaji3/XiaomiADBFastbootTools.svg)

Xiaomi デバイス向けの ADB/Fastboot ツール。システムアプリの管理、ROM フラッシュ、ブートローダー操作などを GUI で実行できます。

> **Fork 元**: [Szaki/XiaomiADBFastbootTools](https://github.com/Szaki/XiaomiADBFastbootTools)（メンテナンス停止）

## ダウンロード

**[最新リリース](https://github.com/ukaji3/XiaomiADBFastbootTools/releases/latest)** から `XiaomiADBFastbootTools.jar` をダウンロードしてください。

## 動作要件

- **Java 21** 以降（OpenJDK 推奨）
- **JavaFX 21**（JAR に同梱）

## モジュール

| モジュール | モード | 説明 |
|---|---|---|
| **App Manager** | ADB | システムアプリのアンインストール/再インストール/無効化/有効化 |
| **Camera2** | Recovery | Camera2 API (HAL3) と EIS の有効化/無効化 |
| **File Explorer** | ADB | デバイスとPC間のファイル転送 |
| **Screen density** | ADB | DPI 値の変更 |
| **Screen resolution** | ADB | 画面解像度の変更 |
| **Device properties** | ADB/Fastboot | デバイス情報の取得・保存 |
| **Flasher** | Fastboot | パーティションへのイメージフラッシュ、ROM フラッシュ |
| **Wiper** | Fastboot | キャッシュ/ユーザーデータの消去 |
| **OEM Unlocker/Locker** | Fastboot | ブートローダーのロック/アンロック |
| **ROM Downloader** | Fastboot | MIUI Fastboot ROM のリンク取得・ダウンロード |

### 全アプリ表示モード

App Manager に「Show all installed apps (risky)」チェックボックスがあり、有効にするとデバイス上の全パッケージを表示できます。既知のブロートウェアリスト外のアプリも操作可能ですが、システムに必要なアプリを削除するリスクがあります。

## 使い方

### ADB モードで接続

1. 開発者オプションを有効化
   - MIUI: 設定 > デバイス情報 > 「MIUI バージョン」を7回タップ
   - Android One: 設定 > システム > デバイス情報 > 「ビルド番号」を7回タップ
2. USB デバッグを有効化
   - MIUI: 設定 > 追加設定 > 開発者オプション > USB デバッグ
   - Android One: 設定 > システム > 開発者オプション > USB デバッグ
3. デバイスを PC に接続し、アプリを起動。デバイス側で USB デバッグを許可
4. デバイスが検出されるまで待機

### Fastboot モードで接続

1. 電源 + 音量下ボタンを長押しして Fastboot モードに入る
2. デバイスを PC に接続し、アプリを起動
3. デバイスが検出されるまで待機

## ビルド

```bash
./gradlew jar
# build/libs/XiaomiADBFastbootTools.jar が生成される
java -jar build/libs/XiaomiADBFastbootTools.jar
```

## 技術スタック

- **Kotlin 1.9** / **Java 21**
- **JavaFX 21** (FXML)
- **kotlinx-coroutines 1.7**
- **Gradle 8.5**

## FAQ

### アンインストールと無効化の違いは？

- **アンインストール**: アプリとデータを現在のユーザーから削除。復元には Reinstaller または工場出荷リセットが必要
- **無効化**: アプリを停止するがデータは保持。Settings や Enabler で即座に復元可能

### アンインストールしたアプリは OTA アップデートに影響する？

いいえ。アップデートは安全に適用できます。

### Xiaomi 以外のデバイスでも使える？

ADB/Fastboot は Android 共通ですが、一部の機能は Xiaomi デバイス固有のため、基本的に Xiaomi デバイス向けです。

## ライセンス

[MIT License](LICENSE)

## 警告

**本ツールの使用は自己責任です。** システムアプリの削除はデバイスの動作に影響を与える可能性があります。
