# Royal Pontos - instalador (baixa a versao publicada no GitHub)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$base = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $base
Start-Transcript -Path (Join-Path $base 'instalacao.log') -Force | Out-Null
try {
    $repo = 'royalparafusos/royal'
    $tools = Join-Path $base 'ferramentas'
    New-Item -ItemType Directory -Force -Path $tools | Out-Null

    Write-Host "`n=== [1/4] Java 21 ===" -ForegroundColor Cyan
    $jdk = Get-ChildItem $tools -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jdk) {
        Write-Host "Baixando o Java 21 (~190 MB)..."
        $zip = Join-Path $tools 'jdk.zip'
        Invoke-WebRequest -UseBasicParsing -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $zip
        Expand-Archive -Path $zip -DestinationPath $tools -Force
        Remove-Item $zip
        $jdk = Get-ChildItem $tools -Directory -Filter 'jdk-21*' | Select-Object -First 1
    }
    Write-Host "OK: $($jdk.Name)"

    Write-Host "`n=== [2/4] Baixando a versao mais recente do Royal Pontos ===" -ForegroundColor Cyan
    $rel = Invoke-RestMethod -UseBasicParsing -Uri "https://api.github.com/repos/$repo/releases/latest" -Headers @{ 'User-Agent' = 'RoyalPontos' }
    $asset = $rel.assets | Where-Object { $_.name -like '*.jar' } | Select-Object -First 1
    $app = Join-Path $base 'app'
    if (Test-Path $app) { Remove-Item $app -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $app | Out-Null
    Invoke-WebRequest -UseBasicParsing -Uri $asset.browser_download_url -OutFile (Join-Path $app 'royal-pontos.jar')
    $ico = Join-Path $base 'icone.ico'
    Invoke-WebRequest -UseBasicParsing -Uri "https://raw.githubusercontent.com/$repo/main/icone.ico" -OutFile $ico
    Write-Host "OK: versao $($rel.tag_name)"

    Write-Host "`n=== [3/4] Criando o Royal Pontos.exe ===" -ForegroundColor Cyan
    Get-Process -Name 'Royal Pontos' -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 2
    $dist = Join-Path $base 'programa'
    if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
    & (Join-Path $jdk.FullName 'bin\jpackage.exe') --type app-image --input $app --main-jar royal-pontos.jar `
        --main-class br.com.royal.pontos.Inicio --name 'Royal Pontos' --icon $ico --dest $dist --java-options '-Dfile.encoding=UTF-8' `
        --jlink-options '--strip-debug --no-man-pages --no-header-files'
    if ($LASTEXITCODE -ne 0) { throw "Falha no jpackage (codigo $LASTEXITCODE)" }
    $exe = Join-Path $dist 'Royal Pontos\Royal Pontos.exe'

    Write-Host "`n=== [4/4] Atalho na Area de Trabalho ===" -ForegroundColor Cyan
    $desktop = [Environment]::GetFolderPath('Desktop')
    $lnkPath = Join-Path $desktop 'Royal Pontos.lnk'
    if (Test-Path $lnkPath) { Remove-Item $lnkPath -Force }
    $ws = New-Object -ComObject WScript.Shell
    $lnk = $ws.CreateShortcut($lnkPath)
    $lnk.TargetPath = $exe
    $lnk.WorkingDirectory = Split-Path $exe
    $lnk.IconLocation = "$exe,0"
    $lnk.Description = 'Royal Pontos - Programa de Fidelidade'
    $lnk.Save()
    # remove sobras da instalacao anterior
    foreach ($velho in 'royal-pontos', 'RoyalPontos.zip', 'ferramentas\apache-maven-3.9.11') {
        $p = Join-Path $base $velho; if (Test-Path $p) { Remove-Item $p -Recurse -Force -ErrorAction SilentlyContinue }
    }
    ie4uinit.exe -show 2>$null

    Write-Host "`n*** INSTALACAO CONCLUIDA ***" -ForegroundColor Green
    Write-Host "A partir de agora o Royal Pontos se atualiza sozinho."
    Write-Host "RESULTADO=SUCESSO"
    Start-Process $exe
}
catch {
    Write-Host "`nERRO: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "RESULTADO=ERRO"
}
finally {
    Stop-Transcript | Out-Null
}
