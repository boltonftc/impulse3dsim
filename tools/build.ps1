# Builds the v2 Java modules for the browser runtime.
#   shim.jar = FTC SDK stubs (student compile classpath + runtime API)
#   app.jar  = shim + sim runtime/host ONLY (no student code — that compiles in-browser)
# Student OpModes under java/examples are compiled here only as a desktop sanity check,
# then the source is copied to web/student/ for the browser to compile + run.
# Requires Liberica JDK 17.
$ErrorActionPreference = "Stop"

$jdk  = "C:\Program Files\BellSoft\LibericaJDK-17-Full\bin"
$root = Split-Path -Parent $PSScriptRoot          # impulse_3dsim_v2
$java = Join-Path $root "java"
$build = Join-Path $root "build"
$web  = Join-Path $root "web"

Remove-Item -Recurse -Force $build -ErrorAction SilentlyContinue
$shimOut = Join-Path $build "shim-classes"
$appOut  = Join-Path $build "app-classes"
$exOut   = Join-Path $build "examples-classes"
New-Item -ItemType Directory -Force $shimOut, $appOut, $exOut, (Join-Path $web "student") | Out-Null

function Srcs($dir) { Get-ChildItem -Recurse -Filter *.java $dir | ForEach-Object { $_.FullName } }

# 1) shim (no dependencies)
& "$jdk\javac.exe" --release 17 -d $shimOut (Srcs (Join-Path $java "shim"))
if ($LASTEXITCODE) { throw "shim compile failed" }
& "$jdk\jar.exe" cf (Join-Path $build "shim.jar") -C $shimOut .

# 2) sim runtime/host, compiled against shim + ECJ (host drives the compiler in-process;
#    student class is hot-loaded from /files/ at runtime, not on this classpath)
$simCp = "$(Join-Path $build 'shim.jar');$(Join-Path $web 'ecj.jar')"
& "$jdk\javac.exe" --release 17 -cp $simCp -d $appOut (Srcs (Join-Path $java "sim"))
if ($LASTEXITCODE) { throw "sim compile failed" }

# 3) app.jar = shim classes + sim/host classes (NO student code)
Copy-Item -Recurse -Force "$shimOut\*" $appOut
& "$jdk\jar.exe" cf (Join-Path $web "app.jar") -C $appOut .
Copy-Item -Force (Join-Path $build "shim.jar") (Join-Path $web "shim.jar")

# 4) desktop sanity-compile the example OpModes against the shim + pedro-core (not bundled)
$exCp = "$(Join-Path $build 'shim.jar');$(Join-Path $web 'pedro-core.jar')"
& "$jdk\javac.exe" --release 17 -encoding UTF-8 -cp $exCp -d $exOut (Srcs (Join-Path $java "examples"))
if ($LASTEXITCODE) { throw "examples compile failed" }

# 4b) desktop sanity-compile the STARTER stub package (same classpath as the examples)
$stOut = Join-Path $build "starter-classes"
New-Item -ItemType Directory -Force $stOut | Out-Null
& "$jdk\javac.exe" --release 17 -encoding UTF-8 -cp $exCp -d $stOut (Srcs (Join-Path $root "course\starter"))
if ($LASTEXITCODE) { throw "starter compile failed" }

# 5) publish the student source(s) for the browser to compile + run, plus a package manifest
$teamcode = Join-Path $java "examples\org\firstinspires\ftc\teamcode"
$studentDir = Join-Path $web "student"
Get-ChildItem (Join-Path $studentDir "*.java") -ErrorAction SilentlyContinue | Remove-Item -Force
$names = @()
foreach ($f in Get-ChildItem (Join-Path $teamcode "*.java")) {
    Copy-Item -Force $f.FullName (Join-Path $studentDir $f.Name)
    $names += $f.Name
}
# Float the main TeleOp first so the editor opens on it by default.
$primary = "MecanumDrive.java"
if ($names -contains $primary) { $names = @($primary) + ($names | Where-Object { $_ -ne $primary }) }
$manifest = [ordered]@{ name = "competition_code"; package = "org.firstinspires.ftc.teamcode"; files = $names }
$manifest | ConvertTo-Json -Compress | Set-Content -Encoding UTF8 (Join-Path $studentDir "package.json")

# 6) publish the STARTER stub package (the empty framework the "Create New Package" button clones)
$starterSrc = Join-Path $root "course\starter\org\firstinspires\ftc\teamcode"
$starterDir = Join-Path $web "starter"
New-Item -ItemType Directory -Force $starterDir | Out-Null
Get-ChildItem (Join-Path $starterDir "*.java") -ErrorAction SilentlyContinue | Remove-Item -Force
$sNames = @()
foreach ($f in Get-ChildItem (Join-Path $starterSrc "*.java")) {
    Copy-Item -Force $f.FullName (Join-Path $starterDir $f.Name)
    $sNames += $f.Name
}
if ($sNames -contains $primary) { $sNames = @($primary) + ($sNames | Where-Object { $_ -ne $primary }) }
$sManifest = [ordered]@{ name = "starter"; package = "org.firstinspires.ftc.teamcode"; files = $sNames }
$sManifest | ConvertTo-Json -Compress | Set-Content -Encoding UTF8 (Join-Path $starterDir "package.json")

Write-Host "BUILD_OK"
Get-Item (Join-Path $web "app.jar"), (Join-Path $web "shim.jar") | Select-Object Name, Length
Get-ChildItem (Join-Path $studentDir "*") | Select-Object Name, Length
