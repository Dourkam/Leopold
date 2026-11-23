$ErrorActionPreference = 'Stop'

$racine = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $racine
try {
    & mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw 'La construction Maven a échoué.'
    }

    $repertoireCible = Join-Path $racine 'target'
    $jar = Join-Path $repertoireCible 'Leopold-1.0-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jar)) {
        throw "JAR introuvable : $jar"
    }

    $repertoireEntree = Join-Path $repertoireCible 'jpackage-input'
    $repertoireSortie = Join-Path $repertoireCible 'app-image'
    $application = Join-Path $repertoireSortie 'Leopold'
    New-Item -ItemType Directory -Path $repertoireEntree, $repertoireSortie -Force |
            Out-Null
    Copy-Item -LiteralPath $jar `
            -Destination (Join-Path $repertoireEntree 'Leopold.jar') -Force

    if (Test-Path -LiteralPath $application) {
        Remove-Item -LiteralPath $application -Recurse -Force
    }

    & jpackage --type app-image --name Leopold --input $repertoireEntree `
            --main-jar Leopold.jar --main-class org.example.App `
            --icon (Join-Path $racine 'packaging\windows\Leopold.ico') `
            --dest $repertoireSortie
    if ($LASTEXITCODE -ne 0) {
        throw 'La création de l’application Windows a échoué.'
    }

    Write-Output "Application created: $(Join-Path $application 'Leopold.exe')"
} finally {
    Pop-Location
}
