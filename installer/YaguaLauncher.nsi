Unicode true
SetCompressor /SOLID lzma

!include "MUI2.nsh"
!include "FileFunc.nsh"
!include "LogicLib.nsh"
!include "nsDialogs.nsh"

; ========= Metadatos =========
!define APP_NAME        "YaguaLauncher"
!define APP_VERSION     "1.0.6"
!define COMPANY_NAME    "Tu Estudio"
!define APP_PUBLISHER   "${COMPANY_NAME}"
!define APP_EXE_NAME    "YaguaLauncher.exe"
!define INSTALL_DIRNAME "YaguaLauncher"

; Instalar per-user (sin admin) para facilitar auto-update
!define INSTALL_ROOT "$LOCALAPPDATA\Programs"

; ========= Recursos locales (en la misma carpeta que este .nsi) =========
!define APPICON     "${__FILEDIR}\icon.ico"     ; 256px recomendado
!define HEADERBMP   "${__FILEDIR}\header.bmp"   ; 150x57 BMP
!define WELCOMEBMP  "${__FILEDIR}\welcome.bmp"  ; 164x314 BMP 24-bit

!define MUI_ICON    "${APPICON}"
!define MUI_UNICON  "${APPICON}"
Icon "${APPICON}"

; Header (si existe header.bmp)
!ifmacrondef MUI_HEADERIMAGE
!endif
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_RIGHT
!define MUI_HEADERIMAGE_BITMAP "${HEADERBMP}"

; Welcome/Finish sidebar (si existe welcome.bmp)
!define MUI_WELCOMEFINISHPAGE_BITMAP "${WELCOMEBMP}"

BrandingText "Instalador de ${APP_NAME}"

; ========= Salida =========
!define OUTDIR  "${__FILEDIR}\out"
!define OUTNAME "YaguaLauncher-Setup-${APP_VERSION}.exe"
OutFile "${OUTDIR}\${OUTNAME}"

; ========= Directorio destino =========
InstallDir "${INSTALL_ROOT}\${INSTALL_DIRNAME}"
InstallDirRegKey HKCU "Software\${COMPANY_NAME}\${APP_NAME}" "Install_Dir"

; ========= Niveles / Idioma =========
RequestExecutionLevel user
!insertmacro MUI_LANGUAGE "Spanish"

; ========= Start Menu =========
Var StartMenuFolder
!define MUI_STARTMENUPAGE_DEFAULTFOLDER "${APP_NAME}"
!define MUI_STARTMENUPAGE_REGISTRY_ROOT "HKCU"
!define MUI_STARTMENUPAGE_REGISTRY_KEY  "Software\${COMPANY_NAME}\${APP_NAME}"
!define MUI_STARTMENUPAGE_REGISTRY_VALUENAME "StartMenuFolder"

; ========= Textos de páginas =========
!define MUI_WELCOMEPAGE_TITLE "Instalación de ${APP_NAME}"
!define MUI_WELCOMEPAGE_TEXT  "Se instalará ${APP_NAME} ${APP_VERSION}.$\r$\nHacé clic en Siguiente para continuar."
!define MUI_FINISHPAGE_TITLE  "${APP_NAME} instalado"
!define MUI_FINISHPAGE_TEXT   "¡${APP_NAME} se instaló correctamente!"

; ========= Páginas =========
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_STARTMENU Application $StartMenuFolder
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_FUNCTION LaunchApp
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

!define MUI_ABORTWARNING

; ========= Secciones =========
Section "Aplicación (requerido)" SEC_APP
  SectionIn RO

  ; Ruta por defecto de la app-image generada por jpackage, relativa a este .nsi
  !define APPIMAGE_DIR "${__FILEDIR}\..\build\jpackage\YaguaLauncher"

  ; Verifica que exista el EXE en la app-image
  IfFileExists "${APPIMAGE_DIR}\${APP_EXE_NAME}" +3 0
    MessageBox MB_ICONSTOP "No se encontró la app-image en:${NL}${APPIMAGE_DIR}${NL}${NL}Ejecutá jpackage antes de compilar el instalador."
    Abort

  SetOutPath "$INSTDIR"

  ; Copia todo el contenido de la app-image
  File /r "${APPIMAGE_DIR}\*.*"

  ; Registrar desinstalación (Apps & Features) — usa el EXE como DisplayIcon
  WriteRegStr HKCU "Software\${COMPANY_NAME}\${APP_NAME}" "Install_Dir" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "DisplayName"    "${APP_NAME}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "Publisher"      "${APP_PUBLISHER}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "DisplayVersion" "${APP_VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "UninstallString" "$INSTDIR\Uninstall.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "DisplayIcon"     "$INSTDIR\${APP_EXE_NAME}"
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" "NoRepair" 1

  ; Uninstaller
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  ; Accesos del Menú Inicio (carpeta elegida por el usuario)
  !insertmacro MUI_STARTMENU_WRITE_BEGIN Application
    CreateDirectory "$SMPROGRAMS\$StartMenuFolder"
    CreateShortCut "$SMPROGRAMS\$StartMenuFolder\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE_NAME}" "" "$INSTDIR\${APP_EXE_NAME}" 0
    CreateShortCut "$SMPROGRAMS\$StartMenuFolder\Desinstalar ${APP_NAME}.lnk" "$INSTDIR\Uninstall.exe"
  !insertmacro MUI_STARTMENU_WRITE_END
SectionEnd

Section "Acceso directo en el escritorio" SEC_DESKTOP
  CreateShortCut "$DESKTOP\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE_NAME}" "" "$INSTDIR\${APP_EXE_NAME}" 0
SectionEnd

; ========= Desinstalación =========
Section "Uninstall"
  ; Intentar cerrar si está abierto
  nsExec::ExecToStack 'taskkill /IM "${APP_EXE_NAME}" /T /F'

  ; Borrar accesos del Menú Inicio
  !insertmacro MUI_STARTMENU_GETFOLDER Application $StartMenuFolder
  Delete "$SMPROGRAMS\$StartMenuFolder\${APP_NAME}.lnk"
  Delete "$SMPROGRAMS\$StartMenuFolder\Desinstalar ${APP_NAME}.lnk"
  RMDir  "$SMPROGRAMS\$StartMenuFolder"

  ; Borrar acceso del escritorio
  Delete "$DESKTOP\${APP_NAME}.lnk"

  ; Borrar app
  RMDir /r "$INSTDIR"

  ; Limpiar registro
  DeleteRegKey HKCU "Software\${COMPANY_NAME}\${APP_NAME}"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}"
SectionEnd

Function LaunchApp
  Exec '"$INSTDIR\${APP_EXE_NAME}"'
FunctionEnd
