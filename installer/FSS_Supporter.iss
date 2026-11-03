; FSS_Supporter 설치 파일 (Inno Setup 6)
; 관리자 권한 없이 사용자 폴더(%LOCALAPPDATA%\Programs)에 설치한다. 내부망 PC에서도 설치 가능.
; ISCC /DAppVersion=0.1.0 /DArch=x64 /DSourceDir=...\FSS_Supporter /DOutputDir=... FSS_Supporter.iss

#ifndef AppVersion
  #define AppVersion "0.1.0"
#endif
#ifndef Arch
  #define Arch "x64"
#endif
#ifndef SourceDir
  #define SourceDir "..\app\build\compose\binaries\main\app\FSS_Supporter"
#endif
#ifndef OutputDir
  #define OutputDir "..\dist"
#endif

[Setup]
AppId={{4F1D7C3A-2B8E-4E51-9A6D-53C2E7A0B911}
AppName=FSS_Supporter
AppVersion={#AppVersion}
AppVerName=FSS_Supporter {#AppVersion}
AppPublisher=이현덕 with Claude
AppPublisherURL=https://hdlee73.github.io/Home/
AppSupportURL=https://github.com/hdlee73/FSS_supporter
DefaultDirName={localappdata}\Programs\FSS_Supporter
DefaultGroupName=FSS_Supporter
DisableProgramGroupPage=yes
DisableDirPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir={#OutputDir}
OutputBaseFilename=FSS_Supporter-{#AppVersion}-{#Arch}-Setup
SetupIconFile=..\app\src\main\resources\icons\app.ico
UninstallDisplayIcon={app}\FSS_Supporter.exe
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
CloseApplications=force
RestartApplications=no
#if Arch == "arm64"
ArchitecturesAllowed=arm64
ArchitecturesInstallIn64BitMode=arm64
#else
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
#endif

[Languages]
Name: "korean"; MessagesFile: "compiler:Languages\Korean.isl"

[Tasks]
Name: "desktopicon"; Description: "바탕 화면에 바로 가기 만들기"; GroupDescription: "추가 작업:"

[InstallDelete]
; 이전 버전의 런타임·라이브러리를 지워 버전이 섞이지 않게 한다(사용자 자료는 %APPDATA%에 있어 지워지지 않음).
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\FSS_Supporter"; Filename: "{app}\FSS_Supporter.exe"
Name: "{userdesktop}\FSS_Supporter"; Filename: "{app}\FSS_Supporter.exe"; Tasks: desktopicon

[Run]
; 업데이트(조용한 설치) 뒤에도 앱을 다시 켠다.
Filename: "{app}\FSS_Supporter.exe"; Description: "FSS_Supporter 실행"; Flags: nowait postinstall
