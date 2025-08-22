# IntelliJ WebView Terminal Sample

VS Code Extension에서 테스트한 Pseudoterminal 기능을 IntelliJ IDEA 플러그인으로 구현한 프로젝트입니다.

## 🎯 프로젝트 목표

VS Code Extension의 `Pseudoterminal` API와 동일한 기능을 IntelliJ IDEA에서 구현하여:
- 실제 쉘 프로세스 제어
- WebView 기반 터미널 UI
- 입출력 완전 제어
- 명령어 상태 유지 (cd, export 등)

## 🏗️ 기술 스택

- **Language**: Kotlin
- **Framework**: IntelliJ Platform Plugin SDK
- **Process Control**: IntelliJ ProcessHandler (vs VS Code Pseudoterminal)
- **WebView**: JCEF (Java Chromium Embedded Framework)
- **Terminal UI**: xterm.js
- **Build Tool**: Gradle

## 📋 주요 기능

### ✅ 구현된 기능
- 🖥️ **실제 쉘 프로세스 실행**: IntelliJ ProcessHandler를 사용한 진짜 쉘 세션
- 🌐 **WebView 터미널 UI**: xterm.js 기반의 브라우저 터미널
- 🔄 **양방향 통신**: JavaScript ↔ Kotlin 메시지 통신
- ⌨️ **키보드 제어**: 방향키 히스토리, Ctrl+C 프로세스 중단
- 🎨 **IntelliJ 테마**: IntelliJ IDEA 다크 테마와 조화로운 UI
- 📦 **상태 관리**: 터미널 생성/종료/상태 확인

### 🔄 VS Code Extension 대비 기능 매핑

| VS Code Extension | IntelliJ Plugin | 비고 |
|---|---|---|
| `vscode.Pseudoterminal` | `ProcessHandler` | 실제 프로세스 제어 |
| `writeEmitter.fire()` | `notifyListeners()` | 출력 전송 |
| `child_process.spawn()` | `ProcessBuilder + OSProcessHandler` | 프로세스 생성 |
| `webview.postMessage()` | `executeJavaScript()` | 메시지 전송 |
| `webview.onDidReceiveMessage()` | `JBCefJSQuery` | 메시지 수신 |

## 🚀 실행 방법

### 1. 필수 요구사항
- **IntelliJ IDEA 2023.2+** (Ultimate 또는 Community)
- **Java 17+**
- **Gradle 8.4+**

### 2. 프로젝트 빌드
```bash
cd /Users/sunungyang/Documents/intellij-webview-sample

# Gradle Wrapper 권한 설정 (macOS/Linux)
chmod +x gradlew

# 의존성 설치 및 빌드
./gradlew build
```

### 3. 플러그인 개발 환경 실행
```bash
# IntelliJ Plugin Development 환경 실행
./gradlew runIde
```

이 명령어를 실행하면:
1. 새로운 IntelliJ IDEA 인스턴스가 열립니다
2. 개발 중인 플러그인이 자동으로 설치됩니다
3. 플러그인을 테스트할 수 있습니다

### 4. 플러그인 사용 방법

#### 방법 1: Tool Window 사용
1. IntelliJ 하단의 **"WebView Terminal"** 탭 클릭
2. WebView Terminal 창이 열립니다

#### 방법 2: 메뉴 사용
1. **Tools** → **Open WebView Terminal** 선택
2. WebView Terminal Tool Window가 활성화됩니다

#### 방법 3: 키보드 단축키
- **Ctrl+Shift+T** (Windows/Linux)
- **Cmd+Shift+T** (macOS)

### 5. 터미널 기능 테스트

#### 기본 명령어 테스트
```bash
# 디렉토리 확인
pwd

# 파일 목록
ls -la

# 환경 변수 테스트
export TEST=hello
echo $TEST

# 디렉토리 이동 (상태 유지 확인)
cd /tmp
pwd
```

#### 고급 기능 테스트
- **명령어 히스토리**: ↑↓ 방향키로 이전 명령어 탐색
- **프로세스 중단**: Ctrl+C로 실행 중인 명령어 중단
- **터미널 제어**: 생성/종료/클리어 버튼 테스트

## 🛠️ 개발 가이드

### 프로젝트 구조
```
intellij-webview-sample/
├── build.gradle.kts              # Gradle 빌드 설정
├── gradle.properties             # Gradle 속성
├── src/main/
│   ├── kotlin/com/example/intellijwebviewsample/
│   │   ├── TerminalService.kt             # 터미널 백엔드 서비스
│   │   ├── WebViewTerminalPanel.kt       # WebView UI 컴포넌트
│   │   ├── WebViewTerminalToolWindowFactory.kt  # Tool Window Factory
│   │   └── OpenWebViewTerminalAction.kt  # 메뉴 액션
│   └── resources/META-INF/
│       └── plugin.xml            # 플러그인 메타데이터
└── README.md                     # 이 파일
```

### 주요 컴포넌트 설명

#### 1. TerminalService.kt
- **역할**: VS Code의 Pseudoterminal 역할
- **기능**: 실제 쉘 프로세스 생성 및 제어
- **API**: `executeCommand()`, `killCurrentProcess()`, `terminateTerminal()`

#### 2. WebViewTerminalPanel.kt
- **역할**: VS Code의 WebView Panel 역할
- **기능**: JCEF 브라우저 + xterm.js 터미널 UI
- **통신**: JavaScript ↔ Kotlin 양방향 메시지

#### 3. WebViewTerminalToolWindowFactory.kt
- **역할**: IntelliJ Tool Window 생성
- **위치**: IDE 하단 탭으로 표시

### 디버깅 방법

#### 1. 로그 확인
IntelliJ 개발 환경에서:
```
Help → Show Log in Finder/Explorer
```
또는 IDE 내부 로그:
```
Help → Diagnostic Tools → Debug Log Settings
```

#### 2. JavaScript 디버깅
WebView에서 F12 개발자 도구 사용 가능:
```kotlin
// WebViewTerminalPanel.kt에서 디버깅 활성화
browser.jbCefClient.addContextMenuHandler(...)
```

### 빌드 및 배포

#### 플러그인 JAR 생성
```bash
./gradlew buildPlugin
```
생성된 JAR: `build/distributions/intellij-webview-sample-1.0-SNAPSHOT.zip`

#### 로컬 설치 테스트
```bash
./gradlew publishPlugin
```

## 🔍 VS Code와 IntelliJ 비교

### 유사점
- ✅ 실제 쉘 프로세스 제어
- ✅ WebView 기반 터미널 UI
- ✅ xterm.js 사용
- ✅ 양방향 메시지 통신
- ✅ 키보드 제어 및 히스토리

### 차이점
| 항목 | VS Code Extension | IntelliJ Plugin |
|---|---|---|
| **프로세스 제어** | Pseudoterminal API | ProcessHandler |
| **WebView** | VS Code WebView API | JCEF |
| **언어** | TypeScript | Kotlin |
| **빌드** | npm + tsc | Gradle |
| **배포** | VSIX | JAR |

## 🐛 문제 해결

### 일반적인 문제

#### 1. JCEF 브라우저가 로드되지 않음
```kotlin
// 해결: JBCefApp 초기화 확인
if (!JBCefApp.isSupported()) {
    logger.error("JCEF is not supported")
    return
}
```

#### 2. JavaScript 브리지가 작동하지 않음
- LoadHandler에서 올바른 타이밍에 스크립트 주입 확인
- `frame?.isMain == true` 조건 확인

#### 3. 프로세스가 종료되지 않음
```kotlin
// 해결: 강제 종료 로직 추가
process.destroyForcibly()
```

### 로그 레벨 설정
```xml
<!-- plugin.xml에 추가 -->
<extensions defaultExtensionNs="com.intellij">
    <errorHandler implementation="com.intellij.diagnostic.DefaultIdeaErrorReporter"/>
</extensions>
```

## 📚 참고 자료

### IntelliJ Platform 문서
- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [JCEF (Java CEF) 가이드](https://plugins.jetbrains.com/docs/intellij/jcef.html)
- [Tool Windows](https://plugins.jetbrains.com/docs/intellij/tool-windows.html)

### 외부 라이브러리
- [xterm.js](https://xtermjs.org/) - 웹 터미널 UI
- [Jackson](https://github.com/FasterXML/jackson) - JSON 파싱

## 🤝 기여

이 프로젝트는 VS Code Extension의 Pseudoterminal 기능을 IntelliJ에서 검증하기 위한 테스트 프로젝트입니다.

### 개발 우선순위
1. ✅ 기본 터미널 기능 구현
2. 🔄 성능 최적화
3. 📈 에러 처리 강화
4. 🎨 UI/UX 개선

---

**🎯 목표**: VS Code Extension과 100% 동일한 터미널 기능을 IntelliJ에서 구현하여 크로스 플랫폼 호환성 검증
